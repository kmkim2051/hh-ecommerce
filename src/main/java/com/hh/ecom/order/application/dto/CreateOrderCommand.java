package com.hh.ecom.order.application.dto;

import java.util.List;

public record CreateOrderCommand(
        List<Long> cartItemIds,
        Long couponId
) {
}
