package com.hh.ecom.order.domain;

import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order {
    private final Long id;
    private final String orderNumber;
    private final Long userId;
    private final BigDecimal totalAmount;      // 총 주문 금액 (할인 전)
    private final BigDecimal discountAmount;   // 할인 금액
    private final BigDecimal finalAmount;      // 최종 결제 금액 (totalAmount - discountAmount)
    private final OrderStatus status;
    private final Long couponUserId;           // 사용된 쿠폰 ID (nullable)
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Builder.Default
    private final List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(
            Long userId,
            String orderNumber,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            Long couponUserId
    ) {
        validateCreateParams(userId, orderNumber, totalAmount, discountAmount);

        BigDecimal finalAmount = totalAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_DISCOUNT_AMOUNT);
        }

        LocalDateTime now = LocalDateTime.now();
        return Order.builder()
                .userId(userId)
                .orderNumber(orderNumber)
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .status(OrderStatus.PENDING)
                .couponUserId(couponUserId)
                .createdAt(now)
                .updatedAt(now)
                .orderItems(new ArrayList<>())
                .build();
    }

    private static void validateCreateParams(
            Long userId,
            String orderNumber,
            BigDecimal totalAmount,
            BigDecimal discountAmount
    ) {
        if (userId == null) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "사용자 ID는 필수입니다.");
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "주문 번호는 필수입니다.");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_AMOUNT, "주문 금액은 0 이상이어야 합니다.");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_DISCOUNT_AMOUNT, "할인 금액은 0 이상이어야 합니다.");
        }
        if (discountAmount.compareTo(totalAmount) > 0) {
            throw new OrderException(OrderErrorCode.INVALID_DISCOUNT_AMOUNT);
        }
    }

    public Order withId(Long id) {
        return this.toBuilder()
                .id(id)
                .build();
    }

    public Order updateStatus(OrderStatus newStatus) {
        return this.toBuilder()
                .status(newStatus)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public Order addOrderItem(OrderItem orderItem) {
        List<OrderItem> newItems = new ArrayList<>(this.orderItems);
        newItems.add(orderItem);
        return this.toBuilder()
                .orderItems(newItems)
                .build();
    }

    public Order setOrderItems(List<OrderItem> orderItems) {
        return this.toBuilder()
                .orderItems(new ArrayList<>(orderItems))
                .build();
    }

    public void validateCancelable() {
        if (status != OrderStatus.PAID && status != OrderStatus.COMPLETED) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_CANCELABLE,
                    "PAID 또는 COMPLETED 상태의 주문만 취소 가능합니다. 현재 상태: " + status);
        }
    }

    public void validateOwner(Long requestUserId) {
        if (!this.userId.equals(requestUserId)) {
            throw new OrderException(OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS);
        }
    }

    public boolean isPaid() {
        return status == OrderStatus.PAID;
    }

    public boolean isCanceled() {
        return status == OrderStatus.CANCELED;
    }

    public boolean hasCoupon() {
        return couponUserId != null;
    }
}
