package com.hh.ecom.cart.infrastructure.persistence.entity;

import com.hh.ecom.cart.domain.CartItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_items")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CartItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

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
