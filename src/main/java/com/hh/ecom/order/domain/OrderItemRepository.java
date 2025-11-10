package com.hh.ecom.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository {
    OrderItem save(OrderItem orderItem);

    Optional<OrderItem> findById(Long id);

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> saveAll(List<OrderItem> orderItems);

    List<OrderItem> findAll();
}
