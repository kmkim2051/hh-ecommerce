package com.hh.ecom.coupon.domain;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    List<Coupon> findAll();
    List<Coupon> findAllIssuable();
    void deleteAll(); // for testing
}
