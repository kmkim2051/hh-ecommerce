package com.hh.ecom.coupon.domain.event;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 이벤트
 * - Kafka Topic: coupon-issue로 발행됨
 * - Partition Key: couponId (동일 쿠폰은 동일 파티션에서 순차 처리)
 */
public record CouponIssueRequestEvent(
    String requestId, // UUID (멱등성 보장용)
    Long userId,
    Long couponId,
    LocalDateTime requestedAt
) {
    public static CouponIssueRequestEvent of(String requestId, Long userId, Long couponId) {
        return new CouponIssueRequestEvent(requestId, userId, couponId, LocalDateTime.now());
    }
}
