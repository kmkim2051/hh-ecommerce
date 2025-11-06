package com.hh.ecom.cart.infrastructure.persistence.entity;

import com.hh.ecom.cart.domain.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemEntity {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime createdAt;

    public CartItem toDomain() {
        return CartItem.builder()
                .id(this.id)
                .userId(this.userId)
                .productId(this.productId)
                .quantity(this.quantity)
                .createdAt(this.createdAt)
                .build();
    }

    public static CartItemEntity from(CartItem cartItem) {
        return CartItemEntity.builder()
                .id(cartItem.getId())
                .userId(cartItem.getUserId())
                .productId(cartItem.getProductId())
                .quantity(cartItem.getQuantity())
                .createdAt(cartItem.getCreatedAt())
                .build();
    }
}
