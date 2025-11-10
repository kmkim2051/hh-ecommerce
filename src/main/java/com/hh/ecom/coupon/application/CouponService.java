package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.domain.exception.OptimisticLockException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 50;

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
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                return tryIssueCoupon(userId, couponId);
            } catch (OptimisticLockException e) {
                retryCount++;

                if (retryCount >= MAX_RETRY_COUNT) {
                    log.warn("쿠폰 발급 최대 재시도 횟수 초과. userId={}, couponId={}, retryCount={}",
                            userId, couponId, retryCount);
                    throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED,
                            "동시 요청이 많아 쿠폰 발급에 실패했습니다. 다시 시도해주세요.");
                }

                // Exponential backoff
                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED,
                            "쿠폰 발급 중 인터럽트가 발생했습니다.");
                }

                log.debug("쿠폰 발급 재시도. userId={}, couponId={}, retryCount={}",
                        userId, couponId, retryCount);
            }
        }

        throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED,
                "쿠폰 발급에 실패했습니다.");
    }

    /**
     * 실제 쿠폰 발급 로직 (낙관적 락 적용)
     */
    private CouponUser tryIssueCoupon(Long userId, Long couponId) {
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

        // 4. 쿠폰 수량 차감 (버전 자동 증가)
        Coupon decreasedCoupon = coupon.decreaseQuantity();

        // 5. 쿠폰 저장 (낙관적 락 검증 - 버전 충돌 시 OptimisticLockException 발생)
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

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CouponUserWithCoupon {
        private final CouponUser couponUser;
        private final Coupon coupon;

        public static CouponUserWithCoupon of(CouponUser couponUser, Coupon coupon) {
            return new CouponUserWithCoupon(couponUser, coupon);
        }

        public boolean isSameCouponId(Long couponId) {
            return Objects.equals(coupon.getId(), couponId);
        }
    }
}
