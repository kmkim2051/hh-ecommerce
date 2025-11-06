package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {
    /**
     * ### FR-C-001: 선착순 쿠폰 발급
     * - 설명: 사용자가 선착순으로 쿠폰을 발급받을 수 있다
     * - 입력: 사용자 ID, 쿠폰 ID
     * - 출력: 발급된 쿠폰 정보
     * - 비고:
     *   - 수량이 소진되면 발급 불가
     *   - 동일 쿠폰 중복 발급 불가
     *   - 낙관적 락 또는 비관적 락 기반 동시성 제어
     *
     * ### FR-C-002: 보유 쿠폰 조회
     * - 설명: 사용자가 보유한 쿠폰 목록을 조회할 수 있다
     * - 입력: 사용자 ID
     * - 출력: 미사용 쿠폰 목록
     */

    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    /**
     * 발급 가능한 쿠폰 목록 조회
     */
    public List<Coupon> getAvailableCoupons() {
        return couponRepository.findAllIssuable();
    }

    /**
     * 모든 쿠폰 목록 조회
     */
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    /**
     * FR-C-001: 선착순 쿠폰 발급
     */
    @Transactional
    public CouponUser issueCoupon(Long userId, Long couponId) {
        // 1. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
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
        couponRepository.save(decreasedCoupon);

        // 5. 쿠폰 발급 기록
        CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
        return couponUserRepository.save(couponUser);
    }

    /**
     * FR-C-002: 보유 쿠폰 조회 (미사용만)
     */
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

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CouponUserWithCoupon {
        private final CouponUser couponUser;
        private final Coupon coupon;

        public static CouponUserWithCoupon of(CouponUser couponUser, Coupon coupon) {
            return new CouponUserWithCoupon(couponUser, coupon);
        }
    }
}
