package com.hh.ecom.coupon.application;

import com.hh.ecom.common.lock.LockKeyGenerator;
import com.hh.ecom.common.lock.RedisLockExecutor;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponCommandService {
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;
    private final RedisLockExecutor redisLockExecutor;
    private final LockKeyGenerator lockKeyGenerator;
    private final TransactionTemplate transactionTemplate;

    public CouponUser issueCoupon(Long userId, Long couponId) {
        final String lockKey = lockKeyGenerator.generateCouponIssueLockKey(couponId);
        log.debug("쿠폰 발급 락 획득 시도: lockKey={}, userId={}, couponId={}", lockKey, userId, couponId);

        return redisLockExecutor.executeWithLock(List.of(lockKey), () ->
            transactionTemplate.execute(status -> {
                try {
                    return executeIssueCoupon(userId, couponId);
                } catch (DataIntegrityViolationException e) {
                    log.debug("중복 발급 시도 감지. userId={}, couponId={}", userId, couponId);
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                }
            })
        );
    }

    private CouponUser executeIssueCoupon(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "couponId: " + couponId));

        coupon.validateIssuable();
        validateNotDuplicatedIssue(userId, couponId);

        Coupon decreasedCoupon = coupon.decreaseQuantity();
        couponRepository.save(decreasedCoupon);

        CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
        CouponUser savedCouponUser = couponUserRepository.save(couponUser);

        log.info("쿠폰 발급 완료: couponUserId={}, userId={}, couponId={}",
            savedCouponUser.getId(), userId, couponId);
        return savedCouponUser;
    }

    /**
     * 쿠폰 사용 (트랜잭션 내부에서 호출 - 주문 생성 시)
     * 주문 생성 트랜잭션과 락이 이미 있으므로 MANDATORY 사용
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CouponUser useCouponWithinTransaction(Long couponUserId, Long orderId) {
        return executeUseCoupon(couponUserId, orderId);
    }

    private CouponUser executeUseCoupon(Long couponUserId, Long orderId) {
        CouponUser couponUser = couponUserRepository.findById(couponUserId)
            .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_USER_NOT_FOUND));

        CouponUser usedCouponUser = couponUser.use(orderId);
        CouponUser savedCouponUser = couponUserRepository.save(usedCouponUser);

        log.debug("쿠폰 사용 완료: couponUserId={}, orderId={}", couponUserId, orderId);
        return savedCouponUser;
    }

    private void validateNotDuplicatedIssue(Long userId, Long couponId) {
        couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existing -> {
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                });
    }
}
