package com.hh.ecom.product.infrastructure.persistence.jpa;

import com.hh.ecom.product.infrastructure.persistence.entity.ProductViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductViewJpaRepository extends JpaRepository<ProductViewEntity, Long> {

    @Query("""
            SELECT pv.productId as productId, COUNT(pv) as viewCount
            FROM ProductViewEntity pv
            WHERE pv.viewedAt >= :startDate
            GROUP BY pv.productId
            ORDER BY COUNT(pv) DESC
            LIMIT :limit
            """)
    List<ProductViewCountProjection> findTopProductsByViewCountSince(
            @Param("startDate") LocalDateTime startDate,
            @Param("limit") Integer limit
    );

    interface ProductViewCountProjection {
        Long getProductId();
        Long getViewCount();
    }
}
