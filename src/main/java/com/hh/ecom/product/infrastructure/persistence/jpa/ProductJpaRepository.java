package com.hh.ecom.product.infrastructure.persistence.jpa;

import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByIdIn(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids")
    List<ProductEntity> findByIdInForUpdate(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM ProductEntity p WHERE p.viewCount IS NOT NULL ORDER BY p.viewCount DESC")
    List<ProductEntity> findTopByViewCount(@Param("limit") Integer limit);
}
