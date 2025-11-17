package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.*;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;

import com.hh.ecom.order.application.dto.DiscountInfo;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    public List<Coupon> getAvailableCoupons() {
        return couponRepository.findAllIssuable();
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @Transactional
    public CouponUser issueCoupon(Long userId, Long couponId) {
        try {
            return tryIssueCoupon(userId, couponId);
        } catch (DataIntegrityViolationException e) {
            log.debug("중복 발급 시도 감지. userId={}, couponId={}", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
    }

    private CouponUser tryIssueCoupon(Long userId, Long couponId) {
        Coupon coupon = findByIdWithLock(couponId);

        coupon.validateIssuable();
        validateNotDuplicatedIssue(userId, couponId);

        Coupon decreasedCoupon = coupon.decreaseQuantity();
        couponRepository.save(decreasedCoupon);

        CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
        return couponUserRepository.save(couponUser);
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

    @Transactional
    public CouponUser useCoupon(Long couponUserId, Long orderId) {
        CouponUser couponUser = couponUserRepository.findById(couponUserId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_USER_NOT_FOUND));

        CouponUser usedCouponUser = couponUser.use(orderId);
        return couponUserRepository.save(usedCouponUser);
    }

    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "couponId: " + couponId));
    }

    @Transactional(readOnly = true)
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

    private void validateNotDuplicatedIssue(Long userId, Long couponId) {
        couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existing -> {
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                });
    }

    private Coupon findByIdWithoutLock(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
    }

    private Coupon findByIdWithLock(Long couponId) {
        return couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "couponId: " + couponId));
    }
}
