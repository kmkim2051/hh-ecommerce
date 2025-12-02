package com.hh.ecom.coupon.application;

import com.hh.ecom.common.lock.LockDomain;
import com.hh.ecom.common.lock.LockKeyGenerator;
import com.hh.ecom.common.lock.RedisLockExecutor;
import com.hh.ecom.common.lock.SimpleLockResource;
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
        final String lockKey = SimpleLockResource.of(LockDomain.COUPON_ISSUE, couponId).getLockKey();
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
     * Reentrant Lock 동작:
     * - OrderService가 이미 lock:coupon:user:{couponUserId}를 획득한 상태라면
     * - 같은 스레드에서 재진입이 허용되어 데드락 없이 실행됨
     */
    public CouponUser useCoupon(Long couponUserId, Long orderId) {
        String lockKey = lockKeyGenerator.generateCouponUseLockKey(couponUserId);
        log.debug("쿠폰 사용 락 획득 시도: lockKey={}, couponUserId={}, orderId={}", lockKey, couponUserId, orderId);

        return redisLockExecutor.executeWithLock(List.of(lockKey), () ->
            transactionTemplate.execute(status -> {
                CouponUser couponUser = couponUserRepository.findById(couponUserId)
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_USER_NOT_FOUND));

                CouponUser usedCouponUser = couponUser.use(orderId);
                CouponUser savedCouponUser = couponUserRepository.save(usedCouponUser);

                log.info("쿠폰 사용 완료: couponUserId={}, orderId={}", couponUserId, orderId);
                return savedCouponUser;
            })
        );
    }

    private void validateNotDuplicatedIssue(Long userId, Long couponId) {
        couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existing -> {
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                });
    }
}
