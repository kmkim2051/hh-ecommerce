package com.hh.ecom.point.application;

import com.hh.ecom.common.lock.util.LockKeyGenerator;
import com.hh.ecom.common.lock.util.RedisLockExecutor;
import com.hh.ecom.common.transaction.OptimisticLockRetryExecutor;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.domain.TransactionType;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointService 단위 테스트")
class PointServiceTest {

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointTransactionRepository transactionRepository;

    @Mock
    private OptimisticLockRetryExecutor retryExecutor;

    @Mock
    private RedisLockExecutor redisLockExecutor;

    @Mock
    private LockKeyGenerator lockKeyGenerator;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PointService pointService;

    private Point testPoint;
    private Long userId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        testPoint = Point.builder()
                .id(1L)
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        // retryExecutor.execute()가 호출되면 실제로 operation을 실행
        lenient().when(retryExecutor.execute(any(), anyInt())).thenAnswer(invocation -> {
            var operation = invocation.getArgument(0, java.util.function.Supplier.class);
            return operation.get();
        });

        lenient().when(retryExecutor.execute(any())).thenAnswer(invocation -> {
            var operation = invocation.getArgument(0, java.util.function.Supplier.class);
            return operation.get();
        });

        // redisLockExecutor.executeWithLock()이 호출되면 실제로 action을 실행
        lenient().when(redisLockExecutor.executeWithLock(any(), any())).thenAnswer(invocation -> {
            var action = invocation.getArgument(1, java.util.function.Supplier.class);
            return action.get();
        });

        // transactionTemplate.execute()가 호출되면 실제로 callback을 실행
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });

        // lockKeyGenerator mock 설정
        lenient().when(lockKeyGenerator.generatePointLockKey(anyLong()))
                .thenAnswer(invocation -> "lock:point:user:" + invocation.getArgument(0));
    }

    @Nested
    @DisplayName("포인트 충전 테스트")
    class ChargePointTest {

        @Test
        @DisplayName("포인트 계좌가 없으면 새로 생성하고 충전한다")
        void chargePoint_createNewAccount() {
            // given
            BigDecimal amount = BigDecimal.valueOf(10000);
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.empty());

            given(pointRepository.save(any(Point.class))).willAnswer(invocation -> {
                Point point = invocation.getArgument(0);
                if (point.getId() == null) {
                    // 새로 생성되는 경우 ID 부여
                    return point.toBuilder().id(1L).build();
                }
                return point;
            });

            given(transactionRepository.save(any(PointTransaction.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            Point result = pointService.chargePoint(userId, amount);

            // then
            assertThat(result).isNotNull();
            verify(pointRepository, times(2)).save(any(Point.class)); // 생성 + 충전
            verify(transactionRepository).save(any(PointTransaction.class));
        }

        @Test
        @DisplayName("기존 포인트 계좌에 충전한다")
        void chargePoint_existingAccount() {
            // given
            BigDecimal amount = BigDecimal.valueOf(5000);
            Point existingPoint = testPoint.toBuilder().balance(BigDecimal.valueOf(10000)).build();

            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(existingPoint));

            Point chargedPoint = existingPoint.charge(amount);
            given(pointRepository.save(any(Point.class))).willReturn(chargedPoint);
            given(transactionRepository.save(any(PointTransaction.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            pointService.chargePoint(userId, amount);

            // then
            verify(pointRepository).save(any(Point.class));

            ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
            verify(transactionRepository).save(captor.capture());

            PointTransaction savedTransaction = captor.getValue();
            assertThat(savedTransaction.getType()).isEqualTo(TransactionType.CHARGE);
            assertThat(savedTransaction.getAmount()).isEqualTo(amount);
        }

        @Test
        @DisplayName("0 이하의 금액으로 충전 시 예외가 발생한다")
        void chargePoint_invalidAmount() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(testPoint));

            // when & then
            assertThatThrownBy(() -> pointService.chargePoint(userId, BigDecimal.ZERO))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
        }
    }

    @Nested
    @DisplayName("포인트 조회 테스트")
    class GetPointTest {

        @Test
        @DisplayName("사용자 ID로 포인트 계좌를 조회한다")
        void getPoint_success() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(testPoint));

            // when
            Point result = pointService.getPoint(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            verify(pointRepository).findByUserId(userId);
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 포인트 조회 시 예외가 발생한다")
        void getPoint_notFound() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.getPoint(userId))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.POINT_NOT_FOUND);
        }

        @Test
        @DisplayName("포인트 ID로 조회한다")
        void getPointById_success() {
            // given
            Long pointId = 1L;
            given(pointRepository.findById(anyLong())).willReturn(Optional.of(testPoint));

            // when
            Point result = pointService.getPointById(pointId);

            // then
            assertThat(result).isNotNull();
            verify(pointRepository).findById(pointId);
        }
    }

    @Nested
    @DisplayName("포인트 거래 이력 조회 테스트")
    class GetTransactionHistoryTest {

        @Test
        @DisplayName("사용자의 거래 이력을 조회한다")
        void getTransactionHistory_success() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(testPoint));

            List<PointTransaction> transactions = List.of(
                    PointTransaction.create(testPoint.getId(), BigDecimal.valueOf(10000),
                            TransactionType.CHARGE, null, BigDecimal.valueOf(10000)),
                    PointTransaction.create(testPoint.getId(), BigDecimal.valueOf(3000),
                            TransactionType.USE, 1L, BigDecimal.valueOf(7000))
            );
            given(transactionRepository.findByPointId(anyLong())).willReturn(transactions);

            // when
            List<PointTransaction> result = pointService.getTransactionHistory(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getType()).isEqualTo(TransactionType.CHARGE);
            assertThat(result.get(1).getType()).isEqualTo(TransactionType.USE);
            verify(transactionRepository).findByPointId(testPoint.getId());
        }

        @Test
        @DisplayName("포인트 계좌가 없으면 예외가 발생한다")
        void getTransactionHistory_pointNotFound() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.getTransactionHistory(userId))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.POINT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("포인트 환불 테스트")
    class RefundPointTest {

        @Test
        @DisplayName("포인트를 환불한다")
        void refundPoint_success() {
            // given
            BigDecimal amount = BigDecimal.valueOf(3000);
            Long orderId = 1L;
            Point point = testPoint.toBuilder().balance(BigDecimal.valueOf(7000)).build();

            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(point));

            Point refundedPoint = point.refund(amount);
            given(pointRepository.save(any(Point.class))).willReturn(refundedPoint);
            given(transactionRepository.save(any(PointTransaction.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            pointService.refundPoint(userId, amount, orderId);

            // then
            verify(pointRepository).save(any(Point.class));

            ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
            verify(transactionRepository).save(captor.capture());

            PointTransaction savedTransaction = captor.getValue();
            assertThat(savedTransaction.getType()).isEqualTo(TransactionType.REFUND);
            assertThat(savedTransaction.getOrderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("잔액이 0인 상태에서도 환불할 수 있다")
        void refundPoint_zeroBalance() {
            // given
            BigDecimal amount = BigDecimal.valueOf(5000);
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(testPoint));

            Point refundedPoint = testPoint.refund(amount);
            given(pointRepository.save(any(Point.class))).willReturn(refundedPoint);
            given(transactionRepository.save(any(PointTransaction.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            Point result = pointService.refundPoint(userId, amount, 1L);

            // then
            verify(pointRepository).save(any(Point.class));
            verify(transactionRepository).save(any(PointTransaction.class));
        }
    }

    @Nested
    @DisplayName("포인트 계좌 존재 여부 확인 테스트")
    class HasPointAccountTest {

        @Test
        @DisplayName("포인트 계좌가 존재하면 true를 반환한다")
        void hasPointAccount_exists() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.of(testPoint));

            // when
            boolean result = pointService.hasPointAccount(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("포인트 계좌가 없으면 false를 반환한다")
        void hasPointAccount_notExists() {
            // given
            given(pointRepository.findByUserId(anyLong())).willReturn(Optional.empty());

            // when
            boolean result = pointService.hasPointAccount(userId);

            // then
            assertThat(result).isFalse();
        }
    }
}
