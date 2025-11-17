package com.hh.ecom.coupon.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CouponUserWithCoupon {
    private final CouponUser couponUser;
    private final Coupon coupon;

    public static CouponUserWithCoupon of(CouponUser couponUser, Coupon coupon) {
        if (couponUser == null || coupon == null) {
            log.info("couponUser: {}, coupon: {}", couponUser, coupon);
            throw new IllegalArgumentException("CouponUser or Coupon coupon are null");
        }
        return new CouponUserWithCoupon(couponUser, coupon);
    }

    public boolean isSameCouponId(Long couponId) {
        return Objects.equals(coupon.getId(), couponId);
    }
}
