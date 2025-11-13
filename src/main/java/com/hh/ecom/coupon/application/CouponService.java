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
import java.util.stream.Collectors;

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
            // unique constraint violation - 중복 발급 시도
            log.debug("중복 발급 시도 감지. userId={}, couponId={}", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
    }

    /**
     * 실제 쿠폰 발급 로직 (비관적 락 적용)
     * 비관적 락(PESSIMISTIC_WRITE)으로 동시성 제어
     */
    private CouponUser tryIssueCoupon(Long userId, Long couponId) {
        // 1. 쿠폰 조회 (비관적 락 적용 - SELECT ... FOR UPDATE)
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "couponId: " + couponId));

        // 2. 쿠폰 발급 가능 여부 검증
        coupon.validateIssuable();

        // 3. 중복 발급 검증
        couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existing -> {
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                });

        // 4. 쿠폰 수량 차감
        Coupon decreasedCoupon = coupon.decreaseQuantity();

        // 5. 쿠폰 저장 (JPA가 @Version을 자동으로 증가)
        couponRepository.save(decreasedCoupon);

        // 6. 쿠폰 발급 기록
        CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
        return couponUserRepository.save(couponUser);
    }

    public List<CouponUserWithCoupon> getMyCoupons(Long userId) {
        List<CouponUser> couponUsers = couponUserRepository.findByUserIdAndIsUsed(userId, false);

        return couponUsers.stream()
                .map(couponUser -> {
                    Coupon coupon = couponRepository.findById(couponUser.getCouponId())
                            .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
                    return CouponUserWithCoupon.of(couponUser, coupon);
                })
                .collect(Collectors.toList());
    }

    public List<CouponUserWithCoupon> getAllMyCoupons(Long userId) {
        List<CouponUser> couponUsers = couponUserRepository.findByUserId(userId);

        return couponUsers.stream()
                .map(couponUser -> {
                    Coupon coupon = couponRepository.findById(couponUser.getCouponId())
                            .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
                    return CouponUserWithCoupon.of(couponUser, coupon);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponUser useCoupon(Long couponUserId, Long orderId) {
        // 1. 발급받은 쿠폰 조회
        CouponUser couponUser = couponUserRepository.findById(couponUserId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_USER_NOT_FOUND));

        // 2. 쿠폰 사용 처리
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
}
