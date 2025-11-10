package com.hh.ecom.product.presentation.dto.request;

public record CreateCartItemRequest(
        Long productId,
        int quantity
) {
}
