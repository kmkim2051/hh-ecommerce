package com.hh.ecom.cart.infrastructure.persistence.jpa;

import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemRepository;
import com.hh.ecom.cart.infrastructure.persistence.entity.CartItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartItemRepository {
    private final CartItemJpaRepository cartItemJpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        CartItemEntity savedEntity;

        if (cartItem.getId() == null) {
            // New cart item - JPA will auto-generate id and version
            CartItemEntity entity = CartItemEntity.from(cartItem);
            savedEntity = cartItemJpaRepository.save(entity);
        } else {
            // Update existing cart item - must preserve version for optimistic locking
            CartItemEntity existingEntity = cartItemJpaRepository.findById(cartItem.getId())
                    .orElseThrow(() -> new IllegalArgumentException("CartItem not found: " + cartItem.getId()));

            CartItemEntity updatedEntity = CartItemEntity.builder()
                    .id(existingEntity.getId())
                    .userId(cartItem.getUserId())
                    .productId(cartItem.getProductId())
                    .quantity(cartItem.getQuantity())
                    .createdAt(existingEntity.getCreatedAt())
                    .version(existingEntity.getVersion())
                    .build();

            savedEntity = cartItemJpaRepository.save(updatedEntity);
        }

        return savedEntity.toDomain();
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return cartItemJpaRepository.findById(id)
                .map(CartItemEntity::toDomain);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItemJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(CartItemEntity::toDomain);
    }

    @Override
    public List<CartItem> findAllByUserId(Long userId) {
        return cartItemJpaRepository.findAllByUserId(userId).stream()
                .map(CartItemEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        cartItemJpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        cartItemJpaRepository.deleteAllByUserId(userId);
    }

    @Override
    public void deleteAllByUserIdAndProductIdIn(Long userId, List<Long> productIds) {
        cartItemJpaRepository.deleteAllByUserIdAndProductIdIn(userId, productIds);
    }

    @Override
    public void deleteAll() {
        cartItemJpaRepository.deleteAll();
    }
}
