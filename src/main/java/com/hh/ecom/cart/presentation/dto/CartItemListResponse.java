package com.hh.ecom.cart.presentation.dto;

import com.hh.ecom.cart.domain.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemListResponse {
    private List<CartItemResponse> cartItems;
    private Integer totalCount;

    public static CartItemListResponse from(List<CartItem> cartItems) {
        List<CartItemResponse> items = cartItems.stream()
                .map(CartItemResponse::from)
                .collect(Collectors.toList());

        return CartItemListResponse.builder()
                .cartItems(items)
                .totalCount(items.size())
                .build();
    }
}
