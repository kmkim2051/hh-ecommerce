package com.hh.ecom.coupon.infrastructure.persistence.jpa;

import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponUserJpaRepository extends JpaRepository<CouponUserEntity, Long> {

    Optional<CouponUserEntity> findByUserIdAndCouponId(Long userId, Long couponId);

    List<CouponUserEntity> findByUserId(Long userId);

    List<CouponUserEntity> findByUserIdAndIsUsed(Long userId, Boolean isUsed);
}
