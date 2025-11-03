package com.hh.ecom.presentation.dto.request;

public record CreateCartItemRequest(
        Long productId,
        int quantity
) {
}
