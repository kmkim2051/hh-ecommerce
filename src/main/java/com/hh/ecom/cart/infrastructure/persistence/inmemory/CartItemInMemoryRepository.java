package com.hh.ecom.cart.infrastructure.persistence.inmemory;

import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemRepository;
import com.hh.ecom.cart.infrastructure.persistence.entity.CartItemEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Deprecated
public class CartItemInMemoryRepository implements CartItemRepository {
    private final Map<Long, CartItemEntity> cartItems = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CartItem save(CartItem cartItem) {
        CartItemEntity entity = CartItemEntity.from(cartItem);

        if (entity.getId() == null) {
            // New cart item - generate ID
            Long newId = idGenerator.getAndIncrement();
            entity = CartItemEntity.builder()
                    .id(newId)
                    .userId(entity.getUserId())
                    .productId(entity.getProductId())
                    .quantity(entity.getQuantity())
                    .createdAt(entity.getCreatedAt())
                    .build();
        }

        cartItems.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return Optional.ofNullable(cartItems.get(id))
                .map(CartItemEntity::toDomain);
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
        return cartItems.values().stream()
                .filter(entity -> entity.getUserId().equals(userId) && entity.getProductId().equals(productId))
                .findFirst()
                .map(CartItemEntity::toDomain);
    }

    @Override
    public List<CartItem> findAllByUserId(Long userId) {
        return cartItems.values().stream()
                .filter(entity -> entity.getUserId().equals(userId))
                .map(CartItemEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        cartItems.remove(id);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        cartItems.values().removeIf(entity -> entity.getUserId().equals(userId));
    }

    @Override
    public void deleteAllByUserIdAndProductIdIn(Long userId, List<Long> productIds) {
        cartItems.values().removeIf(entity ->
                entity.getUserId().equals(userId) && productIds.contains(entity.getProductId()));
    }

    @Override
    public void deleteAll() {
        cartItems.clear();
    }
}
