package com.hh.ecom.coupon.domain;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    Optional<Coupon> findByIdForUpdate(Long id); // 비관적 락을 사용한 조회 (수량 차감 시)
    List<Coupon> findAll();
    List<Coupon> findAllIssuable();
    void deleteAll(); // for testing
}
