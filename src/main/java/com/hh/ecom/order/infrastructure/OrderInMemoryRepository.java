package com.hh.ecom.order.infrastructure;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderRepository;
import com.hh.ecom.order.infrastructure.persistence.entity.OrderEntity;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderInMemoryRepository implements OrderRepository {

    private final Map<Long, OrderEntity> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            Order orderWithId = order.withId(newId);
            OrderEntity entity = OrderEntity.from(orderWithId);
            storage.put(newId, entity);
            return entity.toDomain();
        } else {
            OrderEntity entity = OrderEntity.from(order);
            storage.put(order.getId(), entity);
            return entity.toDomain();
        }
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(storage.get(id))
                .map(OrderEntity::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return storage.values().stream()
                .map(OrderEntity::toDomain)
                .filter(order -> order.getUserId().equals(userId))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll() {
        return storage.values().stream()
                .map(OrderEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return storage.values().stream()
                .map(OrderEntity::toDomain)
                .filter(order -> order.getOrderNumber().equals(orderNumber))
                .findFirst();
    }

    /**
     * 테스트용 메서드: 저장소 초기화
     */
    public void clear() {
        storage.clear();
        idGenerator.set(1L);
    }

    /**
     * 테스트용 메서드: 저장된 데이터 개수
     */
    public int size() {
        return storage.size();
    }
}
