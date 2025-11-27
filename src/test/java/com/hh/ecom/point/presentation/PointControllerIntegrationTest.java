package com.hh.ecom.point.presentation;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.domain.exception.PointException;
import com.hh.ecom.product.presentation.dto.request.ChargePointRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("PointController 통합 테스트 (Controller + Service + Repository)")
class PointControllerIntegrationTest extends TestContainersConfig {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    private PointController pointController;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        pointController = new PointController(pointService);

        pointTransactionRepository.deleteAll();
        pointRepository.deleteAll();

        testUserId = 1L;
    }

    @Test
    @DisplayName("포인트를 충전하면 잔액이 증가한다")
    void chargePoint_Success() {
        // given
        ChargePointRequest request = new ChargePointRequest(10000);

        // when
        ResponseEntity<Point> response = pointController.chargePoint(testUserId, request);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("포인트를 여러 번 충전하면 누적된다")
    void chargePoint_Multiple() {
        // given - 첫 번째 충전
        ChargePointRequest firstRequest = new ChargePointRequest(10000);
        pointController.chargePoint(testUserId, firstRequest);

        // when - 두 번째 충전
        ChargePointRequest secondRequest = new ChargePointRequest(5000);
        ResponseEntity<Point> response = pointController.chargePoint(testUserId, secondRequest);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBalance()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    @DisplayName("포인트 잔액을 조회한다")
    void getPointBalance() {
        // given - 포인트 충전
        pointService.chargePoint(testUserId, BigDecimal.valueOf(20000));

        // when
        ResponseEntity<Point> response = pointController.getPointBalance(testUserId);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBalance()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    @DisplayName("포인트 계정이 없는 사용자의 잔액 조회 시 실패한다")
    void getPointBalance_NotFound() {
        // when & then
        assertThatThrownBy(() -> pointController.getPointBalance(999999L))
                .isInstanceOf(PointException.class);
    }

    @Test
    @DisplayName("포인트 거래 내역을 조회한다")
    void getPointTransactions() {
        // given - 포인트 충전
        pointService.chargePoint(testUserId, BigDecimal.valueOf(10000));
        pointService.chargePoint(testUserId, BigDecimal.valueOf(5000));

        // when
        ResponseEntity<List<PointTransaction>> response = pointController.getPointTransactions(testUserId);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allMatch(tx -> tx.getType().name().equals("CHARGE"));
    }

    @Test
    @DisplayName("포인트가 없는 사용자의 거래 내역 조회 시 실패한다")
    void getPointTransactions_NotFound() {
        // when & then
        assertThatThrownBy(() -> pointController.getPointTransactions(999999L))
                .isInstanceOf(PointException.class);
    }
}
