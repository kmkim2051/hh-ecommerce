package com.hh.ecom.coupon.domain.event;

import java.time.LocalDateTime;

public record CouponIssuedEvent(
    String requestId,
    Long userId,
    Long couponId,
    Long couponUserId,       // 쿠폰-사용자 발급 ID (성공 시)
    IssueStatus status,
    String failureReason,
    LocalDateTime issuedAt
) {
    public enum IssueStatus {
        SUCCESS,         // 발급 성공
        FAILED,          // 발급 실패
        OUT_OF_STOCK,    // 재고 소진
        DUPLICATE,       // 중복 발급
        EXPIRED          // 만료된 쿠폰
    }

    public static CouponIssuedEvent success(String requestId, Long userId, Long couponId, Long couponUserId) {
        return new CouponIssuedEvent(
            requestId,
            userId,
            couponId,
            couponUserId,
            IssueStatus.SUCCESS,
            null,
            LocalDateTime.now()
        );
    }

    public static CouponIssuedEvent failure(String requestId, Long userId, Long couponId, IssueStatus status, String reason) {
        return new CouponIssuedEvent(
            requestId,
            userId,
            couponId,
            null,
            status,
            reason,
            LocalDateTime.now()
        );
    }
}
