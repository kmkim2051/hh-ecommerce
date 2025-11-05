package com.hh.ecom.order.presentation;

import com.hh.ecom.order.presentation.api.OrderApi;
import com.hh.ecom.product.presentation.dto.request.CreateOrderRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/orders")
public class OrderController implements OrderApi {

    @Override
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest request) {
        Map<String, Object> response = new HashMap<>();

        String orderNumber = "ORDER-" + System.currentTimeMillis();

        response.put("id", System.currentTimeMillis() % 10000);
        response.put("orderNumber", orderNumber);
        response.put("userId", 1L);
        response.put("cartItemIds", request.cartItemIds());
        response.put("couponId", request.couponId());
        response.put("totalAmount", 890000);
        response.put("discountAmount", request.couponId() != null ? 10000 : 0);
        response.put("status", "PAID");
        response.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrders() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> orders = new ArrayList<>();

        Map<String, Object> order1 = new HashMap<>();
        order1.put("id", 1L);
        order1.put("orderNumber", "ORDER-1234567890");
        order1.put("totalAmount", 1570000);
        order1.put("status", "PAID");
        order1.put("createdAt", "2025-10-30T10:30:00");
        orders.add(order1);

        Map<String, Object> order2 = new HashMap<>();
        order2.put("id", 2L);
        order2.put("orderNumber", "ORDER-1234567891");
        order2.put("totalAmount", 350000);
        order2.put("status", "COMPLETED");
        order2.put("createdAt", "2025-10-29T15:20:00");
        orders.add(order2);

        response.put("orders", orders);
        response.put("totalCount", orders.size());

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", id);
        response.put("orderNumber", "ORDER-" + id);
        response.put("userId", 1L);
        response.put("totalAmount", 1570000);
        response.put("discountAmount", 10000);
        response.put("status", "PAID");
        response.put("createdAt", "2025-10-30T10:30:00");

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("productId", 1L);
        item1.put("productName", "노트북");
        item1.put("price", 1500000);
        item1.put("quantity", 1);
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("productId", 2L);
        item2.put("productName", "무선 마우스");
        item2.put("price", 35000);
        item2.put("quantity", 2);
        items.add(item2);

        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", id);
        response.put("orderNumber", "ORDER-" + id);
        response.put("status", "CANCELED");
        response.put("refundAmount", 1570000);
        response.put("canceledAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("message", "주문이 취소되었습니다. 포인트가 환불되었습니다.");

        return ResponseEntity.ok(response);
    }
}