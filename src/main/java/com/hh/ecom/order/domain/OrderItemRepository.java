package com.hh.ecom.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository {
    OrderItem save(OrderItem orderItem);

    Optional<OrderItem> findById(Long id);

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> saveAll(List<OrderItem> orderItems);

    List<OrderItem> findAll();

    /**
     * 상품별 판매 수량 집계 (DB 레벨 집계)
     * COMPLETED 상태 주문의 아이템만 집계
     *
     * @param limit 조회할 상위 상품 개수
     * @return 판매량 내림차순으로 정렬된 상품 ID 리스트
     */
    List<ProductSalesCount> findTopProductsBySalesCount(int limit);

    void deleteAll(); // testing
}
