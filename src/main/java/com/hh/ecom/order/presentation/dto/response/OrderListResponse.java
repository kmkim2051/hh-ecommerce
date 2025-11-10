package com.hh.ecom.order.presentation.dto.response;

import com.hh.ecom.order.domain.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "주문 목록 응답")
public class OrderListResponse {

    @Schema(description = "주문 목록")
    private List<OrderResponse> orders;

    @Schema(description = "전체 개수", example = "10")
    private Integer totalCount;

    public static OrderListResponse from(List<Order> orders) {
        List<OrderResponse> orderResponses = orders.stream()
                .map(OrderResponse::fromWithoutItems)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .orders(orderResponses)
                .totalCount(orderResponses.size())
                .build();
    }
}
