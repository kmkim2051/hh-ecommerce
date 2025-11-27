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

}
