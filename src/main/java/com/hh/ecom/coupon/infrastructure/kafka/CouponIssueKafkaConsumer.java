package com.hh.ecom.coupon.infrastructure.kafka;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.event.CouponIssueRequestEvent;
import com.hh.ecom.coupon.domain.event.CouponIssuedEvent;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.outbox.domain.MessagePublisher;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 쿠폰 발급 요청 Kafka Consumer
 * - Topic: coupon-issue
 * - Consumer Group: coupon-issue-group
 * - Partition Key: couponId → 동일 쿠폰은 동일 파티션에서 순차 처리
 * - Concurrency: 3 (3개의 consumer thread, 각각 다른 파티션 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueKafkaConsumer {

    private final TransactionTemplate transactionTemplate;
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;
    private final MessagePublisher messagePublisher;

    /**
     * 쿠폰 발급 요청 메시지 처리
     * - Kafka partition 순차 처리 → 동시성 제어 불필요
     * - DB 중복 체크로 idempotency 보장
     * - 성공/실패 결과를 coupon-issued 토픽으로 발행 (Optional)
     */
    @KafkaListener(
        topics = KafkaTopics.COUPON_ISSUE,
        groupId = "coupon-issue-group",
        concurrency = "3"
    )
    public void consumeCouponIssueRequest(CouponIssueRequestEvent event) {
        log.info("쿠폰 발급 요청 수신: requestId={}, userId={}, couponId={}",
            event.requestId(), event.userId(), event.couponId());

        try {
            processIssuance(event);
        } catch (Exception e) {
            log.error("쿠폰 발급 처리 실패: requestId={}, userId={}, couponId={}",
                event.requestId(), event.userId(), event.couponId(), e);

            // 실패 이벤트 발행
            publishFailureEvent(event, e);
        }
    }

    private void processIssuance(CouponIssueRequestEvent event) {
        Boolean result = transactionTemplate.execute(status -> {
            try {
                Long userId = event.userId();
                Long couponId = event.couponId();

                // 1. 중복 발급 체크 (Idempotency)
                boolean alreadyIssued = couponUserRepository
                    .findByUserIdAndCouponId(userId, couponId)
                    .isPresent();

                if (alreadyIssued) {
                    log.debug("이미 발급된 쿠폰 건너뜀: requestId={}, userId={}, couponId={}",
                        event.requestId(), userId, couponId);

                    // 중복은 성공으로 처리 (idempotent)
                    publishDuplicateEvent(event);
                    return true;
                }

                // 2. 쿠폰 조회
                Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

                // 3. 쿠폰 수량 감소
                Coupon decreasedCoupon = coupon.decreaseQuantity();
                couponRepository.save(decreasedCoupon);

                // 4. 쿠폰 발급 (CouponUser 생성)
                CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
                CouponUser savedCouponUser = couponUserRepository.save(couponUser);

                log.info("쿠폰 발급 성공: requestId={}, couponUserId={}, userId={}, couponId={}",
                    event.requestId(), savedCouponUser.getId(), userId, couponId);

                // 5. 성공 이벤트 발행
                publishSuccessEvent(event, savedCouponUser.getId());
                return true;

            } catch (DataIntegrityViolationException e) {
                // DB constraint violation (race condition 방지)
                log.debug("중복 발급 감지 (DB constraint): requestId={}, userId={}, couponId={}",
                    event.requestId(), event.userId(), event.couponId());

                publishDuplicateEvent(event);
                return true;

            } catch (CouponException e) {
                log.warn("쿠폰 발급 실패: requestId={}, userId={}, couponId={}, error={}",
                    event.requestId(), event.userId(), event.couponId(), e.getMessage());
                throw e;

            } catch (Exception e) {
                log.error("쿠폰 발급 중 예상치 못한 오류: requestId={}, userId={}, couponId={}",
                    event.requestId(), event.userId(), event.couponId(), e);
                throw e;
            }
        });

        if (!Boolean.TRUE.equals(result)) {
            throw new RuntimeException("쿠폰 발급 트랜잭션 실패");
        }
    }

    private void publishSuccessEvent(CouponIssueRequestEvent request, Long couponUserId) {
        CouponIssuedEvent successEvent = CouponIssuedEvent.success(
            request.requestId(),
            request.userId(),
            request.couponId(),
            couponUserId
        );

        messagePublisher.publish(
            KafkaTopics.COUPON_ISSUED,
            request.couponId().toString(),
            successEvent
        );

        log.debug("쿠폰 발급 성공 이벤트 발행: requestId={}", request.requestId());
    }

    private void publishDuplicateEvent(CouponIssueRequestEvent request) {
        CouponIssuedEvent duplicateEvent = CouponIssuedEvent.failure(
            request.requestId(),
            request.userId(),
            request.couponId(),
            CouponIssuedEvent.IssueStatus.DUPLICATE,
            "이미 발급된 쿠폰입니다"
        );

        messagePublisher.publish(
            KafkaTopics.COUPON_ISSUED,
            request.couponId().toString(),
            duplicateEvent
        );

        log.debug("쿠폰 중복 발급 이벤트 발행: requestId={}", request.requestId());
    }

    private void publishFailureEvent(CouponIssueRequestEvent request, Exception e) {
        CouponIssuedEvent.IssueStatus status = determineFailureStatus(e);
        String reason = e.getMessage();

        CouponIssuedEvent failureEvent = CouponIssuedEvent.failure(
            request.requestId(),
            request.userId(),
            request.couponId(),
            status,
            reason
        );

        messagePublisher.publish(
            KafkaTopics.COUPON_ISSUED,
            request.couponId().toString(),
            failureEvent
        );

        log.debug("쿠폰 발급 실패 이벤트 발행: requestId={}, status={}", request.requestId(), status);
    }

    private CouponIssuedEvent.IssueStatus determineFailureStatus(Exception e) {
        if (e instanceof CouponException ce) {
            return switch (ce.getErrorCode()) {
                case COUPON_SOLD_OUT -> CouponIssuedEvent.IssueStatus.OUT_OF_STOCK;
                case COUPON_ALREADY_ISSUED -> CouponIssuedEvent.IssueStatus.DUPLICATE;
                case COUPON_NOT_FOUND, COUPON_EXPIRED -> CouponIssuedEvent.IssueStatus.EXPIRED;
                default -> CouponIssuedEvent.IssueStatus.FAILED;
            };
        }
        return CouponIssuedEvent.IssueStatus.FAILED;
    }
}
