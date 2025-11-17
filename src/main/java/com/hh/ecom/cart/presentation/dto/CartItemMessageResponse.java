package com.hh.ecom.cart.presentation.dto;

import com.hh.ecom.cart.domain.CartItem;

import java.time.LocalDateTime;

/**
 * 장바구니 작업 결과와 메시지를 포함하는 응답 DTO
 * 추가, 수정, 삭제 등의 작업 결과에 사용
 */
public record CartItemMessageResponse(
        Long id,
        Long userId,
        Long productId,
        Integer quantity,
        LocalDateTime createdAt,
        String message
) {
    public static CartItemMessageResponse of(CartItem cartItem, String message) {
        return new CartItemMessageResponse(
                cartItem.getId(),
                cartItem.getUserId(),
                cartItem.getProductId(),
                cartItem.getQuantity(),
                cartItem.getCreatedAt(),
                message
        );
    }

    public static CartItemMessageResponse ofDeleted(Long id, String message) {
        return new CartItemMessageResponse(
                id,
                null,
                null,
                null,
                null,
                message
        );
    }
}
