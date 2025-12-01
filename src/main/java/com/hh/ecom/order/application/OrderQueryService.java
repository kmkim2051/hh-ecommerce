package com.hh.ecom.order.application;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.order.domain.OrderRepository;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public List<Order> getOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public Order getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        order.validateOwner(userId);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return order.setOrderItems(orderItems);
    }

    public Order getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return order.setOrderItems(orderItems);
    }
}
