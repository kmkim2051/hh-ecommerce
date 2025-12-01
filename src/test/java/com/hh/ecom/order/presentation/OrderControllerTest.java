package com.hh.ecom.order.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hh.ecom.order.application.OrderCommandService;
import com.hh.ecom.order.application.OrderQueryService;
import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderStatus;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.product.presentation.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController 단위 테스트")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("POST /orders - 주문 생성 성공")
    void createOrder_Success() throws Exception {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(List.of(100L, 101L), null);

        Order order = createOrder(1L, userId, "ORDER-123456",
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000),
                OrderStatus.PAID, null);

        given(orderCommandService.createOrder(eq(userId), any())).willReturn(order);

        // When & Then
        mockMvc.perform(post("/orders")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderNumber").value("ORDER-123456"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.totalAmount").value(100000))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.finalAmount").value(100000))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.couponUserId").isEmpty())
                .andExpect(jsonPath("$.items", hasSize(2)));

        verify(orderCommandService, times(1)).createOrder(eq(userId), any());
    }

    @Test
    @DisplayName("POST /orders - 쿠폰을 사용한 주문 생성")
    void createOrder_WithCoupon() throws Exception {
        // Given
        Long userId = 1L;
        Long couponId = 10L;
        Long couponUserId = 50L;
        CreateOrderRequest request = new CreateOrderRequest(List.of(100L), couponId);

        Order order = createOrder(1L, userId, "ORDER-123456",
                BigDecimal.valueOf(100000), BigDecimal.valueOf(10000), BigDecimal.valueOf(90000),
                OrderStatus.PAID, couponUserId);

        given(orderCommandService.createOrder(eq(userId), any())).willReturn(order);

        // When & Then
        mockMvc.perform(post("/orders")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(100000))
                .andExpect(jsonPath("$.discountAmount").value(10000))
                .andExpect(jsonPath("$.finalAmount").value(90000))
                .andExpect(jsonPath("$.couponUserId").value(couponUserId));

        verify(orderCommandService, times(1)).createOrder(eq(userId), any());
    }
    @Test
    @DisplayName("POST /orders - 빈 장바구니로 주문 생성 시도")
    void createOrder_EmptyCart() throws Exception {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(List.of(), null);

        given(orderCommandService.createOrder(eq(userId), any()))
                .willThrow(new OrderException(OrderErrorCode.EMPTY_ORDER_ITEMS));

        // When & Then
        mockMvc.perform(post("/orders")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(orderCommandService, times(1)).createOrder(eq(userId), any());
    }

    @Test
    @DisplayName("POST /orders - 유효하지 않은 주문 금액으로 생성 실패")
    void createOrder_InvalidAmount() throws Exception {
        // Given
        Long userId = 1L;
        CreateOrderRequest request = new CreateOrderRequest(List.of(100L), null);

        given(orderCommandService.createOrder(eq(userId), any()))
                .willThrow(new OrderException(OrderErrorCode.INVALID_ORDER_AMOUNT));

        // When & Then
        mockMvc.perform(post("/orders")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(orderCommandService, times(1)).createOrder(eq(userId), any());
    }

    @Test
    @DisplayName("GET /orders - 사용자의 주문 목록 조회 성공")
    void getOrders_Success() throws Exception {
        // Given
        Long userId = 1L;

        List<Order> orders = List.of(
                createOrder(1L, userId, "ORDER-123456",
                        BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000),
                        OrderStatus.PAID, null),
                createOrder(2L, userId, "ORDER-123457",
                        BigDecimal.valueOf(50000), BigDecimal.valueOf(5000), BigDecimal.valueOf(45000),
                        OrderStatus.COMPLETED, 10L)
        );

        given(orderQueryService.getOrders(userId)).willReturn(orders);

        // When & Then
        mockMvc.perform(get("/orders")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(2)))
                .andExpect(jsonPath("$.orders[0].id").value(1))
                .andExpect(jsonPath("$.orders[0].orderNumber").value("ORDER-123456"))
                .andExpect(jsonPath("$.orders[0].status").value("PAID"))
                .andExpect(jsonPath("$.orders[1].id").value(2))
                .andExpect(jsonPath("$.orders[1].orderNumber").value("ORDER-123457"))
                .andExpect(jsonPath("$.orders[1].status").value("COMPLETED"));

        verify(orderQueryService, times(1)).getOrders(userId);
    }

    @Test
    @DisplayName("GET /orders - 주문 내역이 없는 경우")
    void getOrders_Empty() throws Exception {
        // Given
        Long userId = 1L;
        given(orderQueryService.getOrders(userId)).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/orders")
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(0)));

        verify(orderQueryService, times(1)).getOrders(userId);
    }

    @Test
    @DisplayName("GET /orders/{id} - 특정 주문 조회 성공")
    void getOrder_Success() throws Exception {
        // Given
        Long userId = 1L;
        Long orderId = 100L;

        Order order = createOrder(orderId, userId, "ORDER-123456",
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(100000),
                OrderStatus.PAID, null);

        given(orderQueryService.getOrder(orderId, userId)).willReturn(order);

        // When & Then
        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.orderNumber").value("ORDER-123456"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items", hasSize(2)));

        verify(orderQueryService, times(1)).getOrder(orderId, userId);
    }

    @Test
    @DisplayName("GET /orders/{id} - 존재하지 않는 주문 조회")
    void getOrder_NotFound() throws Exception {
        // Given
        Long userId = 1L;
        Long orderId = 99999L;

        given(orderQueryService.getOrder(orderId, userId))
                .willThrow(new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // When & Then
        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("userId", userId))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(orderQueryService, times(1)).getOrder(orderId, userId);
    }

    @Test
    @DisplayName("GET /orders/{id} - 다른 사용자의 주문 조회 시도")
    void getOrder_UnauthorizedAccess() throws Exception {
        // Given
        Long userId = 1L;
        Long otherUserId = 2L;
        Long orderId = 100L;

        given(orderQueryService.getOrder(orderId, otherUserId))
                .willThrow(new OrderException(OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS));

        // When & Then
        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("userId", otherUserId))
                .andDo(print())
                .andExpect(status().isForbidden());

        verify(orderQueryService, times(1)).getOrder(orderId, otherUserId);
    }

    // Helper methods
    private Order createOrder(Long id, Long userId, String orderNumber,
                             BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal finalAmount,
                             OrderStatus status, Long couponUserId) {
        List<OrderItem> orderItems = List.of(
                createOrderItem(1L, id, 1000L, "상품1", BigDecimal.valueOf(50000), 1),
                createOrderItem(2L, id, 1001L, "상품2", BigDecimal.valueOf(50000), 1)
        );

        return Order.builder()
                .id(id)
                .orderNumber(orderNumber)
                .userId(userId)
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .status(status)
                .couponUserId(couponUserId)
                .orderItems(orderItems)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private OrderItem createOrderItem(Long id, Long orderId, Long productId, String productName,
                                     BigDecimal price, Integer quantity) {
        return OrderItem.builder()
                .id(id)
                .orderId(orderId)
                .productId(productId)
                .productName(productName)
                .price(price)
                .quantity(quantity)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
