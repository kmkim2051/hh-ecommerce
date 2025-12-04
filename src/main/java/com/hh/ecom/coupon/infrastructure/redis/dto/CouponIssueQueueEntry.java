package com.hh.ecom.coupon.infrastructure.redis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class CouponIssueQueueEntry {
    private final Long userId;
    private final Long couponId;
    private final Long enqueuedAt;

    @JsonCreator
    public CouponIssueQueueEntry(
            @JsonProperty("userId") Long userId,
            @JsonProperty("couponId") Long couponId,
            @JsonProperty("enqueuedAt") Long enqueuedAt) {
        this.userId = userId;
        this.couponId = couponId;
        this.enqueuedAt = enqueuedAt;
    }

    public static CouponIssueQueueEntry of(Long userId, Long couponId) {
        return new CouponIssueQueueEntry(
            userId,
            couponId,
            System.currentTimeMillis()
        );
    }

    public long getWaitingTimeMillis() {
        return System.currentTimeMillis() - enqueuedAt;
    }

    public boolean isExpired(long timeoutMillis) {
        return getWaitingTimeMillis() > timeoutMillis;
    }

    @Override
    public String toString() {
        return "CouponIssueQueueEntry{" +
                "userId=" + userId +
                ", couponId=" + couponId +
                ", enqueuedAt=" + enqueuedAt +
                ", waitingTime=" + getWaitingTimeMillis() + "ms" +
                '}';
    }
}
