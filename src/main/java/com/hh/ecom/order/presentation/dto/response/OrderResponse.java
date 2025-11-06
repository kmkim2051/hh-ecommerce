package com.hh.ecom.order.presentation.dto.response;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "주문 응답")
public class OrderResponse {

    @Schema(description = "주문 ID", example = "1")
    private Long id;

    @Schema(description = "주문 번호", example = "ORDER-1704614400000")
    private String orderNumber;

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "총 주문 금액 (할인 전)", example = "1570000")
    private BigDecimal totalAmount;

    @Schema(description = "할인 금액", example = "10000")
    private BigDecimal discountAmount;

    @Schema(description = "최종 결제 금액", example = "1560000")
    private BigDecimal finalAmount;

    @Schema(description = "주문 상태", example = "PAID")
    private OrderStatus status;

    @Schema(description = "쿠폰 사용자 ID", example = "1")
    private Long couponUserId;

    @Schema(description = "주문일시", example = "2025-01-07T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "주문 아이템 목록")
    private List<OrderItemResponse> items;

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .couponUserId(order.getCouponUserId())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    public static OrderResponse fromWithoutItems(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .couponUserId(order.getCouponUserId())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
