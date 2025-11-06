package com.hh.ecom.cart.domain;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {
    CartItem save(CartItem cartItem);
    Optional<CartItem> findById(Long id);
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    List<CartItem> findAllByUserId(Long userId);
    void deleteById(Long id);
    void deleteAllByUserId(Long userId);
    void deleteAllByUserIdAndProductIdIn(Long userId, List<Long> productIds);
}
