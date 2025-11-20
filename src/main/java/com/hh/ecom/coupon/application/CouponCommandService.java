package com.hh.ecom.coupon.application;

import com.hh.ecom.common.transaction.OptimisticLockRetryExecutor;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponCommandService {
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;
    private final OptimisticLockRetryExecutor retryExecutor;

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

    public CouponUser useCoupon(Long couponUserId, Long orderId) {
        return retryExecutor.execute(() -> {
            CouponUser couponUser = couponUserRepository.findById(couponUserId)
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_USER_NOT_FOUND));

            CouponUser usedCouponUser = couponUser.use(orderId);
            return couponUserRepository.save(usedCouponUser);
        }, 5);
    }

    private void validateNotDuplicatedIssue(Long userId, Long couponId) {
        couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existing -> {
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                });
    }

    private Coupon findByIdWithLock(Long couponId) {
        return couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "couponId: " + couponId));
    }
}
