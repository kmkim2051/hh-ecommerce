package com.hh.ecom.order.presentation.dto.response;

import com.hh.ecom.order.domain.Order;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주문 목록 응답")
public record OrderListResponse(
        @Schema(description = "주문 목록")
        List<OrderResponse> orders,

        @Schema(description = "전체 개수", example = "10")
        Integer totalCount
) {
    public static OrderListResponse from(List<Order> orders) {
        List<OrderResponse> orderResponses = orders.stream()
                .map(OrderResponse::fromWithoutItems)
                .toList();

        return new OrderListResponse(
                orderResponses,
                orderResponses.size()
        );
    }
}
