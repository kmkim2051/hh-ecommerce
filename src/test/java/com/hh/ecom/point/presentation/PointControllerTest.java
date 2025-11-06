package com.hh.ecom.point.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.TransactionType;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import com.hh.ecom.product.presentation.dto.request.ChargePointRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
@DisplayName("PointController 단위 테스트")
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PointService pointService;

    @Test
    @DisplayName("GET /points/balance - 포인트 잔액 조회 성공")
    void getPointBalance_Success() throws Exception {
        // Given
        Long userId = 1L;
        Point point = createPoint(1L, userId, BigDecimal.valueOf(50000), 3L);

        given(pointService.getPoint(userId)).willReturn(point);

        // When & Then
        mockMvc.perform(get("/points/balance")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.balance").value(50000))
                .andExpect(jsonPath("$.version").value(3));

        verify(pointService, times(1)).getPoint(userId);
    }

    @Test
    @DisplayName("GET /points/balance - 존재하지 않는 사용자")
    void getPointBalance_UserNotFound() throws Exception {
        // Given
        Long userId = 99999L;
        given(pointService.getPoint(userId))
                .willThrow(new PointException(PointErrorCode.POINT_NOT_FOUND));

        // When & Then
        mockMvc.perform(get("/points/balance")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(pointService, times(1)).getPoint(userId);
    }

    @Test
    @DisplayName("POST /points/charge - 포인트 충전 성공")
    void chargePoint_Success() throws Exception {
        // Given
        Long userId = 1L;
        ChargePointRequest request = new ChargePointRequest(10000);
        Point chargedPoint = createPoint(1L, userId, BigDecimal.valueOf(60000), 4L);

        given(pointService.chargePoint(userId, BigDecimal.valueOf(10000))).willReturn(chargedPoint);

        // When & Then
        mockMvc.perform(post("/points/charge")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.balance").value(60000))
                .andExpect(jsonPath("$.version").value(4));

        verify(pointService, times(1)).chargePoint(userId, BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("POST /points/charge - 유효하지 않은 금액 (0)")
    void chargePoint_InvalidAmount_Zero() throws Exception {
        // Given
        Long userId = 1L;
        ChargePointRequest request = new ChargePointRequest(0);

        given(pointService.chargePoint(userId, BigDecimal.valueOf(0)))
                .willThrow(new PointException(PointErrorCode.INVALID_AMOUNT));

        // When & Then
        mockMvc.perform(post("/points/charge")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(pointService, times(1)).chargePoint(userId, BigDecimal.valueOf(0));
    }

    @Test
    @DisplayName("POST /points/charge - 유효하지 않은 금액 (음수)")
    void chargePoint_InvalidAmount_Negative() throws Exception {
        // Given
        Long userId = 1L;
        ChargePointRequest request = new ChargePointRequest(-1000);

        given(pointService.chargePoint(userId, BigDecimal.valueOf(-1000)))
                .willThrow(new PointException(PointErrorCode.INVALID_AMOUNT));

        // When & Then
        mockMvc.perform(post("/points/charge")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(pointService, times(1)).chargePoint(userId, BigDecimal.valueOf(-1000));
    }

    @Test
    @DisplayName("GET /points/transactions - 거래 이력 조회 성공")
    void getPointTransactions_Success() throws Exception {
        // Given
        Long userId = 1L;
        Long pointId = 100L;

        List<PointTransaction> transactions = List.of(
                createTransaction(1L, pointId, BigDecimal.valueOf(50000), TransactionType.CHARGE, null, BigDecimal.valueOf(50000)),
                createTransaction(2L, pointId, BigDecimal.valueOf(10000), TransactionType.USE, 1000L, BigDecimal.valueOf(40000)),
                createTransaction(3L, pointId, BigDecimal.valueOf(20000), TransactionType.CHARGE, null, BigDecimal.valueOf(60000))
        );

        given(pointService.getTransactionHistory(userId)).willReturn(transactions);

        // When & Then
        mockMvc.perform(get("/points/transactions")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].pointId").value(pointId))
                .andExpect(jsonPath("$[0].amount").value(50000))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[0].balanceAfter").value(50000))
                .andExpect(jsonPath("$[0].orderId").isEmpty())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].type").value("USE"))
                .andExpect(jsonPath("$[1].orderId").value(1000))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].type").value("CHARGE"));

        verify(pointService, times(1)).getTransactionHistory(userId);
    }

    @Test
    @DisplayName("GET /points/transactions - 거래 이력이 없는 경우")
    void getPointTransactions_Empty() throws Exception {
        // Given
        Long userId = 1L;
        given(pointService.getTransactionHistory(userId)).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/points/transactions")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(pointService, times(1)).getTransactionHistory(userId);
    }

    @Test
    @DisplayName("GET /points/transactions - 존재하지 않는 사용자")
    void getPointTransactions_UserNotFound() throws Exception {
        // Given
        Long userId = 99999L;
        given(pointService.getTransactionHistory(userId))
                .willThrow(new PointException(PointErrorCode.POINT_NOT_FOUND));

        // When & Then
        mockMvc.perform(get("/points/transactions")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(pointService, times(1)).getTransactionHistory(userId);
    }

    // Helper methods
    private Point createPoint(Long id, Long userId, BigDecimal balance, Long version) {
        return Point.builder()
                .id(id)
                .userId(userId)
                .balance(balance)
                .version(version)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PointTransaction createTransaction(Long id, Long pointId, BigDecimal amount,
                                               TransactionType type, Long orderId, BigDecimal balanceAfter) {
        return PointTransaction.builder()
                .id(id)
                .pointId(pointId)
                .amount(amount)
                .type(type)
                .orderId(orderId)
                .balanceAfter(balanceAfter)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
