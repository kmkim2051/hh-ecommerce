package com.hh.ecom.order.infrastructure.persistence.jpa;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderRepository;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.order.infrastructure.persistence.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Primary
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        OrderEntity savedEntity;

        if (order.getId() == null) {
            // 새로운 엔티티 생성 (id와 version은 JPA가 자동 생성)
            OrderEntity entity = OrderEntity.from(order);
            savedEntity = orderJpaRepository.save(entity);
        } else {
            // 기존 엔티티 업데이트 - 기존 엔티티를 조회해서 version 유지
            OrderEntity existingEntity = orderJpaRepository.findById(order.getId())
                    .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND, order.getId()));

            // 기존 version과 createdAt을 유지하면서 새로운 엔티티 생성
            OrderEntity updatedEntity = OrderEntity.builder()
                    .id(existingEntity.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .totalAmount(order.getTotalAmount())
                    .discountAmount(order.getDiscountAmount())
                    .finalAmount(order.getFinalAmount())
                    .status(order.getStatus())
                    .couponUserId(order.getCouponUserId())
                    .createdAt(existingEntity.getCreatedAt())
                    .updatedAt(order.getUpdatedAt())
                    .version(existingEntity.getVersion())  // 기존 version 유지
                    .build();

            savedEntity = orderJpaRepository.save(updatedEntity);
        }

        return savedEntity.toDomain();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id)
                .map(OrderEntity::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OrderEntity::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAll()
                .stream()
                .map(OrderEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return orderJpaRepository.findByOrderNumber(orderNumber)
                .map(OrderEntity::toDomain);
    }

    // for only testing
    @Override
    public void deleteAll() {
        orderJpaRepository.deleteAll();
    }
}
