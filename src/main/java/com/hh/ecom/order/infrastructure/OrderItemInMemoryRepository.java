package com.hh.ecom.order.infrastructure;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderItemInMemoryRepository implements OrderItemRepository {

    private final Map<Long, OrderItem> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            OrderItem savedOrderItem = orderItem.withId(newId);
            storage.put(newId, savedOrderItem);
            return savedOrderItem;
        } else {
            storage.put(orderItem.getId(), orderItem);
            return orderItem;
        }
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return storage.values().stream()
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
        return new ArrayList<>(storage.values());
    }

    public void clear() {
        storage.clear();
        idGenerator.set(1L);
    }

    public int size() {
        return storage.size();
    }
}
