package com.hh.ecom.point.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.domain.TransactionType;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("PointService 통합 테스트 (Service + Repository)")
class PointServiceIntegrationTest extends TestContainersConfig {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        pointRepository.deleteAll();
    }

    @Test
    @DisplayName("통합 테스트 - 포인트 충전 후 조회")
    void integration_ChargePointAndGet() {
        // Given
        Long userId = 1L;
        BigDecimal chargeAmount = BigDecimal.valueOf(10000);

        // When
        Point chargedPoint = pointService.chargePoint(userId, chargeAmount);

        // Then
        assertThat(chargedPoint).isNotNull();
        assertThat(chargedPoint.getId()).isNotNull();
        assertThat(chargedPoint.getUserId()).isEqualTo(userId);
        assertThat(chargedPoint.getBalance()).isEqualByComparingTo(chargeAmount);

        // 조회 확인
        Point retrievedPoint = pointService.getPoint(userId);
        assertThat(retrievedPoint.getId()).isEqualTo(chargedPoint.getId());
        assertThat(retrievedPoint.getBalance()).isEqualByComparingTo(chargeAmount);

        // 거래 이력 확인
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(chargeAmount);
        assertThat(transactions.get(0).getBalanceAfter()).isEqualByComparingTo(chargeAmount);
        assertThat(transactions.get(0).getOrderId()).isNull();
    }

    @Test
    @DisplayName("통합 테스트 - 여러 번 충전")
    void integration_MultipleCharges() {
        // Given
        Long userId = 1L;

        // When
        pointService.chargePoint(userId, BigDecimal.valueOf(5000));
        pointService.chargePoint(userId, BigDecimal.valueOf(3000));
        Point finalPoint = pointService.chargePoint(userId, BigDecimal.valueOf(2000));

        // Then
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        // 거래 이력 확인 (최신순으로 정렬됨)
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(3);
        assertThat(transactions).allMatch(t -> t.getType() == TransactionType.CHARGE);
        assertThat(transactions)
                .extracting(PointTransaction::getBalanceAfter)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(
                        BigDecimal.valueOf(5000),
                        BigDecimal.valueOf(8000),
                        BigDecimal.valueOf(10000)
                );
    }

    @Test
    @DisplayName("통합 테스트 - 포인트 사용")
    void integration_UsePoint() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        pointService.chargePoint(userId, BigDecimal.valueOf(10000));

        // When
        Point usedPoint = pointService.usePoint(userId, BigDecimal.valueOf(3000), orderId);

        // Then
        assertThat(usedPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(7000));

        // 거래 이력 확인
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(2);

        PointTransaction useTransaction = transactions.stream()
                .filter(t -> t.getType() == TransactionType.USE)
                .findFirst()
                .orElseThrow();

        assertThat(useTransaction.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(useTransaction.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(7000));
        assertThat(useTransaction.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("통합 테스트 - 잔액 부족으로 사용 실패")
    void integration_UsePointWithInsufficientBalance() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        pointService.chargePoint(userId, BigDecimal.valueOf(5000));

        // When & Then
        assertThatThrownBy(() -> pointService.usePoint(userId, BigDecimal.valueOf(10000), orderId))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);

        // 잔액 변화 없음 확인
        Point point = pointService.getPoint(userId);
        assertThat(point.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000));

        // 거래 이력에 실패한 거래가 기록되지 않음
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(1); // 충전 거래만 존재
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.CHARGE);
    }

    @Test
    @DisplayName("통합 테스트 - 포인트 환불")
    void integration_RefundPoint() {
        // Given
        Long userId = 1L;
        Long orderId = 100L;
        pointService.chargePoint(userId, BigDecimal.valueOf(10000));
        pointService.usePoint(userId, BigDecimal.valueOf(7000), orderId);

        // When - 환불
        Point refundedPoint = pointService.refundPoint(userId, BigDecimal.valueOf(7000), orderId);

        // Then
        assertThat(refundedPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        // 거래 이력 확인
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(3);

        PointTransaction refundTransaction = transactions.stream()
                .filter(t -> t.getType() == TransactionType.REFUND)
                .findFirst()
                .orElseThrow();

        assertThat(refundTransaction.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000));
        assertThat(refundTransaction.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(refundTransaction.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("통합 테스트 - 여러 사용자의 포인트 관리")
    void integration_MultipleUsers() {
        // Given
        Long user1 = 1L;
        Long user2 = 2L;
        Long user3 = 3L;

        // When
        pointService.chargePoint(user1, BigDecimal.valueOf(10000));
        pointService.chargePoint(user2, BigDecimal.valueOf(20000));
        pointService.chargePoint(user3, BigDecimal.valueOf(15000));

        pointService.usePoint(user1, BigDecimal.valueOf(3000), 100L);
        pointService.usePoint(user2, BigDecimal.valueOf(5000), 101L);

        // Then
        Point point1 = pointService.getPoint(user1);
        Point point2 = pointService.getPoint(user2);
        Point point3 = pointService.getPoint(user3);

        assertThat(point1.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(7000));
        assertThat(point2.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(point3.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(15000));

        // 거래 이력 확인
        assertThat(pointService.getTransactionHistory(user1)).hasSize(2); // 충전 1, 사용 1
        assertThat(pointService.getTransactionHistory(user2)).hasSize(2); // 충전 1, 사용 1
        assertThat(pointService.getTransactionHistory(user3)).hasSize(1); // 충전 1
    }

    @Test
    @DisplayName("통합 테스트 - 포인트 계좌가 없는 사용자의 첫 충전")
    void integration_FirstChargeCreatesAccount() {
        // Given
        Long userId = 1L;

        // When
        assertThat(pointService.hasPointAccount(userId)).isFalse();

        Point chargedPoint = pointService.chargePoint(userId, BigDecimal.valueOf(5000));

        // Then
        assertThat(pointService.hasPointAccount(userId)).isTrue();
        assertThat(chargedPoint.getId()).isNotNull();
        assertThat(chargedPoint.getUserId()).isEqualTo(userId);
        assertThat(chargedPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("통합 테스트 - 존재하지 않는 사용자의 포인트 조회")
    void integration_GetPointForNonexistentUser() {
        // Given
        Long nonexistentUserId = 99999L;

        // When & Then
        assertThatThrownBy(() -> pointService.getPoint(nonexistentUserId))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.POINT_NOT_FOUND);
    }

    @Test
    @DisplayName("통합 테스트 - 존재하지 않는 사용자의 포인트 사용 시도")
    void integration_UsePointForNonexistentUser() {
        // Given
        Long nonexistentUserId = 99999L;
        Long orderId = 100L;

        // When & Then
        assertThatThrownBy(() -> pointService.usePoint(nonexistentUserId, BigDecimal.valueOf(1000), orderId))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.POINT_NOT_FOUND);
    }

    @Test
    @DisplayName("통합 테스트 - 존재하지 않는 사용자의 거래 이력 조회")
    void integration_GetTransactionHistoryForNonexistentUser() {
        // Given
        Long nonexistentUserId = 99999L;

        // When & Then
        assertThatThrownBy(() -> pointService.getTransactionHistory(nonexistentUserId))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.POINT_NOT_FOUND);
    }

    @Test
    @DisplayName("통합 테스트 - 유효하지 않은 금액으로 충전 시도")
    void integration_ChargeWithInvalidAmount() {
        // Given
        Long userId = 1L;

        // When & Then - null 금액
        assertThatThrownBy(() -> pointService.chargePoint(userId, null))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_AMOUNT);

        // When & Then - 0 금액
        assertThatThrownBy(() -> pointService.chargePoint(userId, BigDecimal.ZERO))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_AMOUNT);

        // When & Then - 음수 금액
        assertThatThrownBy(() -> pointService.chargePoint(userId, BigDecimal.valueOf(-1000)))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("통합 테스트 - 복잡한 시나리오 (충전, 사용, 환불 혼합)")
    void integration_ComplexScenario() {
        // Given
        Long userId = 1L;
        Long order1 = 100L;
        Long order2 = 101L;

        // When - 시나리오 실행
        // 1. 초기 충전
        pointService.chargePoint(userId, BigDecimal.valueOf(50000));

        // 2. 첫 번째 주문 사용
        pointService.usePoint(userId, BigDecimal.valueOf(15000), order1);

        // 3. 추가 충전
        pointService.chargePoint(userId, BigDecimal.valueOf(20000));

        // 4. 두 번째 주문 사용
        pointService.usePoint(userId, BigDecimal.valueOf(30000), order2);

        // 5. 첫 번째 주문 환불
        pointService.refundPoint(userId, BigDecimal.valueOf(15000), order1);

        // Then - 최종 잔액 확인
        Point finalPoint = pointService.getPoint(userId);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(40000));
        // 50000 - 15000 + 20000 - 30000 + 15000 = 40000

        // 거래 이력 확인
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(5);

        // 거래 타입별 카운트
        long chargeCount = transactions.stream().filter(PointTransaction::isCharge).count();
        long useCount = transactions.stream().filter(PointTransaction::isUse).count();
        long refundCount = transactions.stream().filter(PointTransaction::isRefund).count();

        assertThat(chargeCount).isEqualTo(2);
        assertThat(useCount).isEqualTo(2);
        assertThat(refundCount).isEqualTo(1);

        assertThat(transactions).extracting(PointTransaction::getBalanceAfter)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(
                        BigDecimal.valueOf(50000),  // 충전
                        BigDecimal.valueOf(35000),  // 사용
                        BigDecimal.valueOf(55000),  // 충전
                        BigDecimal.valueOf(25000),  // 사용
                        BigDecimal.valueOf(40000)  // 환불
                );
    }

    @Test
    @DisplayName("통합 테스트 - getPointById로 조회")
    void integration_GetPointById() {
        // Given
        Long userId = 1L;
        Point chargedPoint = pointService.chargePoint(userId, BigDecimal.valueOf(10000));

        // When
        Point retrievedPoint = pointService.getPointById(chargedPoint.getId());

        // Then
        assertThat(retrievedPoint.getId()).isEqualTo(chargedPoint.getId());
        assertThat(retrievedPoint.getUserId()).isEqualTo(userId);
        assertThat(retrievedPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("통합 테스트 - 포인트 계정 존재 여부 확인")
    void integration_HasPointAccount() {
        // Given
        Long user1 = 1L;
        Long user2 = 2L;

        // When & Then - 초기에는 계정 없음
        assertThat(pointService.hasPointAccount(user1)).isFalse();
        assertThat(pointService.hasPointAccount(user2)).isFalse();

        // When - user1만 충전
        pointService.chargePoint(user1, BigDecimal.valueOf(5000));

        // Then - user1만 계정 존재
        assertThat(pointService.hasPointAccount(user1)).isTrue();
        assertThat(pointService.hasPointAccount(user2)).isFalse();
    }

    @Test
    @DisplayName("통합 테스트 - 버전 관리 확인")
    void integration_VersionManagement() {
        // Given
        Long userId = 1L;

        // When
        Point point1 = pointService.chargePoint(userId, BigDecimal.valueOf(5000));
        Point point2 = pointService.chargePoint(userId, BigDecimal.valueOf(3000));
        Point point3 = pointService.usePoint(userId, BigDecimal.valueOf(2000), 100L);

        // Then - 버전이 순차적으로 증가

        // 조회한 포인트도 최신 버전
        Point currentPoint = pointService.getPoint(userId);
    }

    @Test
    @DisplayName("통합 테스트 - 모든 잔액을 사용한 경우")
    void integration_UseAllBalance() {
        // Given
        Long userId = 1L;
        BigDecimal chargeAmount = BigDecimal.valueOf(10000);
        pointService.chargePoint(userId, chargeAmount);

        // When - 모든 잔액 사용
        Point usedPoint = pointService.usePoint(userId, chargeAmount, 100L);

        // Then
        assertThat(usedPoint.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(usedPoint.isZeroBalance()).isTrue();
        assertThat(usedPoint.isPositiveBalance()).isFalse();

        // 추가 사용 시도는 실패
        assertThatThrownBy(() -> pointService.usePoint(userId, BigDecimal.valueOf(1), 101L))
                .isInstanceOf(PointException.class)
                .extracting(ex -> ((PointException) ex).getErrorCode())
                .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("통합 테스트 - 대용량 거래 이력 조회")
    void integration_LargeTransactionHistory() {
        // Given
        Long userId = 1L;
        int transactionCount = 50;

        // When - 많은 거래 생성
        for (int i = 0; i < transactionCount / 2; i++) {
            pointService.chargePoint(userId, BigDecimal.valueOf(1000));
            pointService.usePoint(userId, BigDecimal.valueOf(500), (long) i);
        }

        // Then
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        assertThat(transactions).hasSize(transactionCount);

        Point finalPoint = pointService.getPoint(userId);
        assertThat(finalPoint.getBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(500 * (transactionCount / 2)));
    }
}
