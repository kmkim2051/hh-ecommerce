package com.hh.ecom.cart.presentation.dto;

import com.hh.ecom.cart.domain.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장바구니 작업 결과와 메시지를 포함하는 응답 DTO
 * 추가, 수정, 삭제 등의 작업 결과에 사용
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemMessageResponse {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime createdAt;
    private String message;

    public static CartItemMessageResponse of(CartItem cartItem, String message) {
        return CartItemMessageResponse.builder()
                .id(cartItem.getId())
                .userId(cartItem.getUserId())
                .productId(cartItem.getProductId())
                .quantity(cartItem.getQuantity())
                .createdAt(cartItem.getCreatedAt())
                .message(message)
                .build();
    }

    public static CartItemMessageResponse ofDeleted(Long id, String message) {
        return CartItemMessageResponse.builder()
                .id(id)
                .message(message)
                .build();
    }
}
