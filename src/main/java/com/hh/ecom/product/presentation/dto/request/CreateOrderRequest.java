package com.hh.ecom.product.presentation.dto.request;

import java.util.List;

public record CreateOrderRequest(
        List<Long> cartItemIds,
        Long couponId
) {
}
