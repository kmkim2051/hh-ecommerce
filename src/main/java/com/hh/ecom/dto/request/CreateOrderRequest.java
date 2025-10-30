package com.hh.ecom.dto.request;

import java.util.List;

public record CreateOrderRequest(
        List<Long> cartItemIds,
        Long couponId
) {
}
