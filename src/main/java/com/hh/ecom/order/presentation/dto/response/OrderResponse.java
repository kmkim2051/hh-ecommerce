package com.hh.ecom.order.presentation.dto.response;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "주문 응답")
public record OrderResponse(
        @Schema(description = "주문 ID", example = "1")
        Long id,

        @Schema(description = "주문 번호", example = "ORDER-1704614400000")
        String orderNumber,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "총 주문 금액 (할인 전)", example = "1570000")
        BigDecimal totalAmount,

        @Schema(description = "할인 금액", example = "10000")
        BigDecimal discountAmount,

        @Schema(description = "최종 결제 금액", example = "1560000")
        BigDecimal finalAmount,

        @Schema(description = "주문 상태", example = "PAID")
        OrderStatus status,

        @Schema(description = "쿠폰 사용자 ID", example = "1")
        Long couponUserId,

        @Schema(description = "주문일시", example = "2025-01-07T10:30:00")
        LocalDateTime createdAt,

        @Schema(description = "주문 아이템 목록")
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getCouponUserId(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    public static OrderResponse fromWithoutItems(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getCouponUserId(),
                order.getCreatedAt(),
                null
        );
    }
}
