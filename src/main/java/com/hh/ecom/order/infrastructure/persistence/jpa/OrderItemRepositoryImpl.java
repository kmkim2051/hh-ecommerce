package com.hh.ecom.order.infrastructure.persistence.jpa;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.order.domain.ProductSalesCount;
import com.hh.ecom.order.infrastructure.persistence.entity.OrderItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Primary
public class OrderItemRepositoryImpl implements OrderItemRepository {
    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderItem save(OrderItem orderItem) {
        OrderItemEntity entity = OrderItemEntity.from(orderItem);
        OrderItemEntity savedEntity = orderItemJpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return orderItemJpaRepository.findById(id)
                .map(OrderItemEntity::toDomain);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return orderItemJpaRepository.findByOrderId(orderId)
                .stream()
                .map(OrderItemEntity::toDomain)
                .toList();
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        List<OrderItemEntity> entities = orderItems.stream()
                .map(OrderItemEntity::from)
                .toList();

        List<OrderItemEntity> savedEntities = orderItemJpaRepository.saveAll(entities);

        return savedEntities.stream()
                .map(OrderItemEntity::toDomain)
                .toList();
    }

    @Override
    public List<OrderItem> findAll() {
        return orderItemJpaRepository.findAll()
                .stream()
                .map(OrderItemEntity::toDomain)
                .toList();
    }

    @Override
    public List<ProductSalesCount> findTopProductsBySalesCount(int limit) {
        return orderItemJpaRepository.findTopProductsBySalesCount(limit).stream()
                .map(projection -> ProductSalesCount.of(
                        projection.getProductId(),
                        projection.getSalesCount()
                ))
                .toList();
    }

    @Override
    public void deleteAll() {
        orderItemJpaRepository.deleteAll();
    }
}
