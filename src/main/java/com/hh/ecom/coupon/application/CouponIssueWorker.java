package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.infrastructure.redis.dto.CouponIssueQueueEntry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 쿠폰 발급 큐(redis)를 처리하기 위한 백그라운드 worker
 * - 테스트 환경에서는 비활성화 가능 (coupon.worker.enabled=false)
 * - 프로덕션에서는 기본 활성화
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "coupon.worker.enabled",
    havingValue = "true",
    matchIfMissing = true  // 설정 없으면 활성화 (프로덕션 기본값)
)
public class CouponIssueWorker {

    private final TransactionTemplate transactionTemplate;

    private final RedisCouponService redisCouponService;

    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    @Value("${coupon.worker.batch-size:50}")
    private int batchSize;

    @Value("${coupon.worker.retry-on-failure:false}")
    private boolean retryOnFailure;

    @Scheduled(fixedDelay = 100)
    public void processQueues() {
        try {
            couponRepository.findAll()
                    .stream()
                    .filter(c -> c.getId() != null)
                    .forEach(c -> processQueueForCoupon(c.getId()));
        } catch (Exception e) {
            log.error("쿠폰 발급 큐 처리 중 예상치 못한 오류 발생", e);
        }
    }

    private void processQueueForCoupon(Long couponId) {
        int processedCount = 0;
        List<CouponIssueQueueEntry> failedEntries = new ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            CouponIssueQueueEntry queueEntry = redisCouponService.dequeueUserRequest(couponId);
            if (queueEntry == null) {
                break;
            }
            try {
                final Long userId = queueEntry.getUserId();
                final Long parsedCouponId = queueEntry.getCouponId();

                if (!Objects.equals(couponId, parsedCouponId)) {
                    log.warn("큐 엔트리의 쿠폰 ID 불일치: expected={}, actual={}", couponId, parsedCouponId);
                    continue;
                }

                boolean success = processIssuance(userId, couponId);

                if (success) {
                    processedCount++;
                } else if (retryOnFailure) {
                    failedEntries.add(queueEntry);
                }

            } catch (Exception e) {
                log.error("큐 엔트리 처리 중 오류: {}", queueEntry, e);
                if (retryOnFailure) {
                    failedEntries.add(queueEntry);
                }
            }
        }

        // Requeue failed entries
        for (CouponIssueQueueEntry failedEntry : failedEntries) {
            try {
                redisCouponService.requeueUserRequest(failedEntry.getUserId(), failedEntry.getCouponId());
            } catch (Exception e) {
                log.error("재큐잉 실패: {}", failedEntry, e);
            }
        }

        if (processedCount > 0) {
            log.debug("쿠폰 발급 완료: couponId={}, count={}", couponId, processedCount);
        }
    }

    private boolean processIssuance(Long userId, Long couponId) {
        try {
            Boolean result = transactionTemplate.execute(status -> {
                try {
                    // Check for duplicate (idempotent operation)
                    boolean alreadyIssued = couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                        .isPresent();

                    if (alreadyIssued) {
                        log.debug("이미 발급된 쿠폰 건너뜀: userId={}, couponId={}", userId, couponId);
                        return true;
                    }

                    Coupon coupon = findById(couponId);
                    Coupon decreasedCoupon = coupon.decreaseQuantity();
                    couponRepository.save(decreasedCoupon);

                    CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
                    CouponUser saved = couponUserRepository.save(couponUser);

                    log.info("쿠폰 발급 성공: couponUserId={}, userId={}, couponId={}", saved.getId(), userId, couponId);

                    return true;

                } catch (DataIntegrityViolationException e) {
                    log.debug("중복 발급 감지 (DB constraint): userId={}, couponId={}", userId, couponId);
                    return true;
                } catch (CouponException e) {
                    log.warn("쿠폰 발급 실패: userId={}, couponId={}, error={}", userId, couponId, e.getMessage());
                    return false;
                } catch (Exception e) {
                    log.error("쿠폰 발급 중 예상치 못한 오류: userId={}, couponId={}", userId, couponId, e);
                    return false;
                }
            });

            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.error("트랜잭션 실행 실패: userId={}, couponId={}", userId, couponId, e);
            return false;
        }
    }

    private Coupon findById(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
    }
}
