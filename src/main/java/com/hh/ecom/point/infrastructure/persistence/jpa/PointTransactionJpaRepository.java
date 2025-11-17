package com.hh.ecom.point.infrastructure.persistence.jpa;

import com.hh.ecom.point.infrastructure.persistence.entity.PointTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionJpaRepository extends JpaRepository<PointTransactionEntity, Long> {
    List<PointTransactionEntity> findByPointId(Long pointId);
}
