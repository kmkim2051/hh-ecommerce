package com.hh.ecom.order.infrastructure;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.order.infrastructure.persistence.entity.OrderItemEntity;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderItemInMemoryRepository implements OrderItemRepository {

    private final Map<Long, OrderItemEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            OrderItem orderItemWithId = orderItem.withId(newId);
            OrderItemEntity entity = OrderItemEntity.from(orderItemWithId);
            storage.put(newId, entity);
            return entity.toDomain();
        } else {
            OrderItemEntity entity = OrderItemEntity.from(orderItem);
            storage.put(orderItem.getId(), entity);
            return entity.toDomain();
        }
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return Optional.ofNullable(storage.get(id))
                .map(OrderItemEntity::toDomain);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return storage.values().stream()
                .map(OrderItemEntity::toDomain)
                .filter(item -> item.getOrderId().equals(orderId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> findAll() {
        return storage.values().stream()
                .map(OrderItemEntity::toDomain)
                .collect(Collectors.toList());
    }

    public void clear() {
        storage.clear();
        idGenerator.set(1L);
    }

    public int size() {
        return storage.size();
    }
}
