package com.hh.ecom.cart.infrastructure.persistence.jpa;

import com.hh.ecom.cart.infrastructure.persistence.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItemEntity, Long> {
    Optional<CartItemEntity> findByUserIdAndProductId(Long userId, Long productId);
    List<CartItemEntity> findAllByUserId(Long userId);
    void deleteAllByUserId(Long userId);
    void deleteAllByUserIdAndProductIdIn(Long userId, List<Long> productIds);
}
