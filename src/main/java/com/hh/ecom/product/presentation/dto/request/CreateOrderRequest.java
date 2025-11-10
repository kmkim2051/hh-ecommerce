package com.hh.ecom.product.presentation.dto.request;

import com.hh.ecom.order.application.dto.CreateOrderCommand;

import java.util.List;

public record CreateOrderRequest(
        List<Long> cartItemIds,
        Long couponId
) {
    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(cartItemIds, couponId);
    }
}
