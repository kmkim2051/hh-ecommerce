package com.hh.ecom.order.presentation.dto.response;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "주문 아이템 응답")
public record OrderItemResponse(
        @Schema(description = "주문 아이템 ID", example = "1")
        Long id,

        @Schema(description = "상품 ID", example = "100")
        Long productId,

        @Schema(description = "상품명", example = "노트북")
        String productName,

        @Schema(description = "상품 가격", example = "1500000")
        BigDecimal price,

        @Schema(description = "수량", example = "2")
        Integer quantity,

        @Schema(description = "주문 아이템 상태", example = "NORMAL")
        OrderItemStatus status,

        @Schema(description = "생성일시", example = "2025-01-07T10:30:00")
        LocalDateTime createdAt
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getPrice(),
                orderItem.getQuantity(),
                orderItem.getStatus(),
                orderItem.getCreatedAt()
        );
    }
}
