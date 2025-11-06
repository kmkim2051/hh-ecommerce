package com.hh.ecom.order.infrastructure;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderInMemoryRepository implements OrderRepository {

    private final Map<Long, Order> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            Order savedOrder = order.withId(newId);
            storage.put(newId, savedOrder);
            return savedOrder;
        } else {
            storage.put(order.getId(), order);
            return order;
        }
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return storage.values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return storage.values().stream()
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
