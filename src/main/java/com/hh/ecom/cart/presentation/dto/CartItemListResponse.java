package com.hh.ecom.cart.presentation.dto;

import com.hh.ecom.cart.domain.CartItem;

import java.util.List;

public record CartItemListResponse(
        List<CartItemResponse> cartItems,
        Integer totalCount
) {
    public static CartItemListResponse from(List<CartItem> cartItems) {
        List<CartItemResponse> items = cartItems.stream()
                .map(CartItemResponse::from)
                .toList();

        return new CartItemListResponse(
                items,
                items.size()
        );
    }
}
