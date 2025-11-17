package com.hh.ecom.order.application.dto;

import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;

import java.util.List;

public record CreateOrderCommand(
        List<Long> cartItemIds,
        Long couponId
) {
    public void validate() {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new OrderException(OrderErrorCode.EMPTY_ORDER_CART_ITEM);
        }
    }
}
