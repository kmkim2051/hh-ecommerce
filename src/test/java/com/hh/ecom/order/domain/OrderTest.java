package com.hh.ecom.order.domain;

import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 - 성공")
    void createOrder_Success() {
        Long userId = 1L;
        String orderNumber = "ORDER-123";
        BigDecimal totalAmount = BigDecimal.valueOf(10000);
        BigDecimal discountAmount = BigDecimal.valueOf(1000);
        Long couponUserId = 1L;

        Order order = Order.create(userId, orderNumber, totalAmount, discountAmount, couponUserId);

        assertThat(order).isNotNull();
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(order.getTotalAmount()).isEqualTo(totalAmount);
        assertThat(order.getDiscountAmount()).isEqualTo(discountAmount);
        assertThat(order.getFinalAmount()).isEqualTo(BigDecimal.valueOf(9000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCouponUserId()).isEqualTo(couponUserId);
    }

    @Test
    @DisplayName("주문 생성 - 쿠폰 없이 성공")
    void createOrder_Success_WithoutCoupon() {
        Long userId = 1L;
        String orderNumber = "ORDER-123";
        BigDecimal totalAmount = BigDecimal.valueOf(10000);
        BigDecimal discountAmount = BigDecimal.ZERO;

        Order order = Order.create(userId, orderNumber, totalAmount, discountAmount, null);

        assertThat(order).isNotNull();
        assertThat(order.getFinalAmount()).isEqualTo(totalAmount);
        assertThat(order.getCouponUserId()).isNull();
        assertThat(order.hasCoupon()).isFalse();
    }

    @Test
    @DisplayName("주문 생성 실패 - 사용자 ID 없음")
    void createOrder_Fail_NoUserId() {
        assertThatThrownBy(() ->
                Order.create(null, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("사용자 ID는 필수입니다");
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 번호 없음")
    void createOrder_Fail_NoOrderNumber() {
        assertThatThrownBy(() ->
                Order.create(1L, null, BigDecimal.valueOf(10000), BigDecimal.ZERO, null))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("주문 번호는 필수입니다");
    }

    @Test
    @DisplayName("주문 생성 실패 - 할인 금액이 주문 금액보다 큼")
    void createOrder_Fail_DiscountGreaterThanTotal() {
        assertThatThrownBy(() ->
                Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.valueOf(15000), null))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_DISCOUNT_AMOUNT);
    }

    @Test
    @DisplayName("주문 생성 실패 - 음수 주문 금액")
    void createOrder_Fail_NegativeTotalAmount() {
        assertThatThrownBy(() ->
                Order.create(1L, "ORDER-123", BigDecimal.valueOf(-1000), BigDecimal.ZERO, null))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("주문 금액은 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("주문 상태 변경 - 성공")
    void updateOrderStatus_Success() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);

        Order updatedOrder = order.updateStatus(OrderStatus.PAID);

        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("주문 취소 가능 여부 검증 - PAID 상태")
    void validateCancelable_Paid() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);
        Order paidOrder = order.updateStatus(OrderStatus.PAID);

        assertThatNoException().isThrownBy(paidOrder::validateCancelable);
    }

    @Test
    @DisplayName("주문 취소 가능 여부 검증 - COMPLETED 상태")
    void validateCancelable_Completed() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);
        Order completedOrder = order.updateStatus(OrderStatus.COMPLETED);

        assertThatNoException().isThrownBy(completedOrder::validateCancelable);
    }

    @Test
    @DisplayName("주문 취소 가능 여부 검증 실패 - PENDING 상태")
    void validateCancelable_Fail_Pending() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);

        assertThatThrownBy(order::validateCancelable)
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELABLE);
    }

    @Test
    @DisplayName("주문 취소 가능 여부 검증 실패 - CANCELED 상태")
    void validateCancelable_Fail_Canceled() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);
        Order canceledOrder = order.updateStatus(OrderStatus.CANCELED);

        assertThatThrownBy(canceledOrder::validateCancelable)
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELABLE);
    }

    @Test
    @DisplayName("주문 소유자 검증 - 성공")
    void validateOwner_Success() {
        Long userId = 1L;
        Order order = Order.create(userId, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);

        assertThatNoException().isThrownBy(() -> order.validateOwner(userId));
    }

    @Test
    @DisplayName("주문 소유자 검증 실패 - 다른 사용자")
    void validateOwner_Fail_DifferentUser() {
        Long userId = 1L;
        Long otherUserId = 2L;
        Order order = Order.create(userId, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);

        assertThatThrownBy(() -> order.validateOwner(otherUserId))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS);
    }

    @Test
    @DisplayName("withId로 ID 설정")
    void withId() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);

        Order orderWithId = order.withId(999L);

        assertThat(orderWithId.getId()).isEqualTo(999L);
        assertThat(orderWithId.getOrderNumber()).isEqualTo(order.getOrderNumber());
    }

    @Test
    @DisplayName("주문 상태 확인 메서드")
    void statusCheckMethods() {
        Order order = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);

        Order paidOrder = order.updateStatus(OrderStatus.PAID);
        assertThat(paidOrder.isPaid()).isTrue();
        assertThat(paidOrder.isCanceled()).isFalse();

        Order canceledOrder = order.updateStatus(OrderStatus.CANCELED);
        assertThat(canceledOrder.isCanceled()).isTrue();
        assertThat(canceledOrder.isPaid()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 보유 여부 확인")
    void hasCoupon() {
        Order orderWithCoupon = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.valueOf(1000), 1L);
        assertThat(orderWithCoupon.hasCoupon()).isTrue();

        Order orderWithoutCoupon = Order.create(1L, "ORDER-123", BigDecimal.valueOf(10000), BigDecimal.ZERO, null);
        assertThat(orderWithoutCoupon.hasCoupon()).isFalse();
    }
}
