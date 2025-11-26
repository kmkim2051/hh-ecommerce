package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.*;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.order.application.dto.DiscountInfo;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CouponQueryService {
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    public List<Coupon> getAvailableCoupons() {
        return couponRepository.findAllIssuable();
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "couponId: " + couponId));
    }

    public List<CouponUserWithCoupon> getMyCoupons(Long userId) {
        List<CouponUser> couponUsers = couponUserRepository.findByUserIdAndIsUsed(userId, false);

        return couponUsers.stream()
                .map(couponUser -> {
                    Coupon coupon = findByIdWithoutLock(couponUser.getCouponId());
                    return CouponUserWithCoupon.of(couponUser, coupon);
                })
                .toList();
    }

    public List<CouponUserWithCoupon> getAllMyCoupons(Long userId) {
        List<CouponUser> couponUsers = couponUserRepository.findByUserId(userId);

        return couponUsers.stream()
                .map(couponUser -> {
                    Coupon coupon = findByIdWithoutLock(couponUser.getCouponId());
                    return CouponUserWithCoupon.of(couponUser, coupon);
                })
                .toList();
    }

    public DiscountInfo calculateDiscountInfo(Long userId, Long couponId) {
        if (couponId == null) {
            return DiscountInfo.NONE;
        }

        Coupon coupon = getCoupon(couponId);
        CouponUser validCouponUser = findValidCouponUser(userId, couponId);
        return DiscountInfo.of(coupon.getDiscountAmount(), validCouponUser.getId());
    }

    private CouponUser findValidCouponUser(Long userId, Long couponId) {
        List<CouponUserWithCoupon> userCoupons =
                Optional.ofNullable(getAllMyCoupons(userId))
                        .orElse(Collections.emptyList());

        return userCoupons.stream()
                .filter(cwc -> cwc.isSameCouponId(couponId))
                .map(CouponUserWithCoupon::getCouponUser)
                .filter(Objects::nonNull)
                .filter(CouponUser::isUsable)
                .findFirst()
                .orElseThrow(() -> new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "사용 가능한 쿠폰이 없습니다. id=" + couponId));
    }

    private Coupon findByIdWithoutLock(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
    }
}
