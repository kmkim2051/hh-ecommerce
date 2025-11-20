package com.hh.ecom.point.infrastructure.persistence.jpa;

import com.hh.ecom.point.infrastructure.persistence.entity.PointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointJpaRepository extends JpaRepository<PointEntity, Long> {
    Optional<PointEntity> findByUserId(Long userId);
}
