package com.hh.ecom.order.infrastructure.persistence.inmemory;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.order.domain.ProductSalesCount;
import com.hh.ecom.order.infrastructure.persistence.entity.OrderItemEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Deprecated
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
                .toList();
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(this::save)
                .toList();
    }

    @Override
    public List<OrderItem> findAll() {
        return storage.values().stream()
                .map(OrderItemEntity::toDomain)
                .toList();
    }

    @Override
    public List<ProductSalesCount> findTopProductsBySalesCount(int limit) {
        // InMemory 구현: 메모리에서 집계
        Map<Long, Long> salesMap = storage.values().stream()
                .map(OrderItemEntity::toDomain)
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.summingLong(item -> item.getQuantity().longValue())
                ));

        return salesMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> ProductSalesCount.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public void deleteAll() {
        clear();
    }

    public void clear() {
        storage.clear();
        idGenerator.set(1L);
    }

    public int size() {
        return storage.size();
    }
}
