package com.hh.ecom.order.presentation;

import com.hh.ecom.order.application.OrderService;
import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.presentation.api.OrderApi;
import com.hh.ecom.order.presentation.dto.response.OrderListResponse;
import com.hh.ecom.order.presentation.dto.response.OrderResponse;
import com.hh.ecom.product.presentation.dto.request.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final OrderService orderService;

    @Override
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("userId") Long userId,
            @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.createOrder(userId, request.toCommand());
        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<OrderListResponse> getOrders(
            @RequestHeader("userId") Long userId
    ) {
        List<Order> orders = orderService.getOrders(userId);
        OrderListResponse response = OrderListResponse.from(orders);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader("userId") Long userId,
            @PathVariable Long id
    ) {
        Order order = orderService.getOrder(id, userId);
        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.ok(response);
    }
}

