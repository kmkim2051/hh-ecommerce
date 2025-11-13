package com.hh.ecom.order.infrastructure.persistence.jpa;

import com.hh.ecom.order.infrastructure.persistence.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderId(Long orderId);

    /**
     * 상품별 판매 수량 집계
     * - COMPLETED 상태의 주문만 집계
     * - product_id 기준으로 quantity 합계
     * - 판매량 내림차순 정렬
     */
    @Query(value = """
        SELECT 
          oi.product_id as productId, 
          SUM(oi.quantity) as salesCount
        FROM order_items oi
        INNER JOIN orders o ON oi.order_id = o.id
        WHERE o.status = 'COMPLETED'
        GROUP BY oi.product_id
        ORDER BY salesCount DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductSalesProjection> findTopProductsBySalesCount(@Param("limit") int limit);

    /**
     * 집계 결과 Projection 인터페이스
     */
    interface ProductSalesProjection {
        Long getProductId();
        Long getSalesCount();
    }
}
