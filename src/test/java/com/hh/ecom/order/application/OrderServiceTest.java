package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.domain.*;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.product.application.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartService cartService;

    @Mock
    private ProductService productService;

    @Mock
    private CouponService couponService;

    @Mock
    private PointService pointService;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private OrderItem testOrderItem;

    @BeforeEach
    void setUp() {
        testOrder = Order.create(
                1L,
                "ORDER-123",
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(1000),
                1L
        ).withId(1L);

        testOrderItem = OrderItem.create(
                1L,
                100L,
                "노트북",
                BigDecimal.valueOf(10000),
                1
        ).withId(1L);
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 아이템 비어있음")
    void createOrder_Fail_EmptyOrderItems() {
        Long userId = 1L;
        List<Long> emptyCartItemIds = List.of();
        CreateOrderCommand command = new CreateOrderCommand(emptyCartItemIds, null);

        assertThatThrownBy(() ->
                orderService.createOrder(userId, command))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.EMPTY_ORDER_CART_ITEM);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 상태 업데이트 - 성공")
    void updateOrderStatus_Success() {
        Long orderId = 1L;
        OrderStatus newStatus = OrderStatus.PAID;

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.updateOrderStatus(orderId, newStatus);

        assertThat(result.getStatus()).isEqualTo(newStatus);
        verify(orderRepository).findById(orderId);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 상태 업데이트 실패 - 주문 없음")
    void updateOrderStatus_Fail_OrderNotFound() {
        Long orderId = 999L;
        OrderStatus newStatus = OrderStatus.PAID;

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.updateOrderStatus(orderId, newStatus))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);

        verify(orderRepository).findById(orderId);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("사용자 주문 목록 조회 - 성공")
    void getOrders_Success() {
        Long userId = 1L;
        List<Order> orders = List.of(testOrder);

        when(orderRepository.findByUserId(userId)).thenReturn(orders);

        List<Order> result = orderService.getOrders(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(testOrder.getId());
        verify(orderRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("주문 상세 조회 - 성공")
    void getOrder_Success() {
        Long orderId = 1L;
        Long userId = 1L;
        List<OrderItem> orderItems = List.of(testOrderItem);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        Order result = orderService.getOrder(orderId, userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getOrderItems()).hasSize(1);
        verify(orderRepository).findById(orderId);
        verify(orderItemRepository).findByOrderId(orderId);
    }

    @Test
    @DisplayName("주문 상세 조회 실패 - 주문 없음")
    void getOrder_Fail_OrderNotFound() {
        Long orderId = 999L;
        Long userId = 1L;

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.getOrder(orderId, userId))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);

        verify(orderRepository).findById(orderId);
        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("주문 상세 조회 실패 - 권한 없음")
    void getOrder_Fail_Unauthorized() {
        Long orderId = 1L;
        Long userId = 1L;
        Long otherUserId = 2L;

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() ->
                orderService.getOrder(orderId, otherUserId))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS);

        verify(orderRepository).findById(orderId);
        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("주문 ID로 조회 - 성공")
    void getOrderById_Success() {
        Long orderId = 1L;
        List<OrderItem> orderItems = List.of(testOrderItem);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        Order result = orderService.getOrderById(orderId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getOrderItems()).hasSize(1);
        verify(orderRepository).findById(orderId);
        verify(orderItemRepository).findByOrderId(orderId);
    }
}
