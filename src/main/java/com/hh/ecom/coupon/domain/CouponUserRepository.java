package com.hh.ecom.coupon.domain;

import java.util.List;
import java.util.Optional;

public interface CouponUserRepository {
    CouponUser save(CouponUser couponUser);
    Optional<CouponUser> findById(Long id);
    Optional<CouponUser> findByUserIdAndCouponId(Long userId, Long couponId);
    List<CouponUser> findByUserId(Long userId);
    List<CouponUser> findByUserIdAndIsUsed(Long userId, Boolean isUsed);
    List<CouponUser> findByCouponId(Long couponId);  // for testing concurrent scenarios
    void deleteAll(); // for testing
}
