package com.hh.ecom.order.infrastructure.persistence.entity;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {
    private Long id;
    private String orderNumber;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private OrderStatus status;
    private Long couponUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order toDomain() {
        return Order.builder()
                .id(this.id)
                .orderNumber(this.orderNumber)
                .userId(this.userId)
                .totalAmount(this.totalAmount)
                .discountAmount(this.discountAmount)
                .finalAmount(this.finalAmount)
                .status(this.status)
                .couponUserId(this.couponUserId)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static OrderEntity from(Order order) {
        return OrderEntity.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .couponUserId(order.getCouponUserId())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
