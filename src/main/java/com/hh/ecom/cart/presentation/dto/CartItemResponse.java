package com.hh.ecom.cart.presentation.dto;

import com.hh.ecom.cart.domain.CartItem;

import java.time.LocalDateTime;

public record CartItemResponse(
        Long id,
        Long userId,
        Long productId,
        Integer quantity,
        LocalDateTime createdAt
) {
    public static CartItemResponse from(CartItem cartItem) {
        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getUserId(),
                cartItem.getProductId(),
                cartItem.getQuantity(),
                cartItem.getCreatedAt()
        );
    }
}
