package com.hh.ecom.dto.request;

public record CreateCartItemRequest(
        Long productId,
        int quantity
) {
}
