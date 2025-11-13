package com.hh.ecom.coupon.infrastructure.persistence.jpa;

import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponEntity c WHERE c.id = :id")
    Optional<CouponEntity> findByIdWithLock(Long id);

    @Query("""
            SELECT c FROM CouponEntity c
            WHERE c.isActive = true
            AND c.status = :status
            AND c.startDate <= :now
            AND c.endDate > :now
            AND c.availableQuantity > 0
            """)
    List<CouponEntity> findAllIssuable(CouponStatus status, LocalDateTime now);
}
