package com.hh.ecom.order.presentation.dto.response;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "주문 아이템 응답")
public class OrderItemResponse {

    @Schema(description = "주문 아이템 ID", example = "1")
    private Long id;

    @Schema(description = "상품 ID", example = "100")
    private Long productId;

    @Schema(description = "상품명", example = "노트북")
    private String productName;

    @Schema(description = "상품 가격", example = "1500000")
    private BigDecimal price;

    @Schema(description = "수량", example = "2")
    private Integer quantity;

    @Schema(description = "주문 아이템 상태", example = "NORMAL")
    private OrderItemStatus status;

    @Schema(description = "생성일시", example = "2025-01-07T10:30:00")
    private LocalDateTime createdAt;

    public static OrderItemResponse from(OrderItem orderItem) {
        return OrderItemResponse.builder()
                .id(orderItem.getId())
                .productId(orderItem.getProductId())
                .productName(orderItem.getProductName())
                .price(orderItem.getPrice())
                .quantity(orderItem.getQuantity())
                .status(orderItem.getStatus())
                .createdAt(orderItem.getCreatedAt())
                .build();
    }
}
