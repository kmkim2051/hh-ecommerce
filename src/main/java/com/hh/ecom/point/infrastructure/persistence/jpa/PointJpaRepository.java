package com.hh.ecom.point.infrastructure.persistence.jpa;

import com.hh.ecom.point.infrastructure.persistence.entity.PointEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PointJpaRepository extends JpaRepository<PointEntity, Long> {
    Optional<PointEntity> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointEntity p WHERE p.userId = :userId")
    Optional<PointEntity> findByUserIdWithLock(Long userId);
}
