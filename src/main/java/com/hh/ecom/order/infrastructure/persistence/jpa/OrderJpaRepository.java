package com.hh.ecom.order.infrastructure.persistence.jpa;

import com.hh.ecom.order.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<OrderEntity> findByOrderNumber(String orderNumber);
}
