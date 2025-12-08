package com.hh.ecom.coupon.infrastructure.redis;

import org.springframework.stereotype.Component;

/**
 * Redis 키 생성기 - 비동기 쿠폰 발급 전용
 *
 * 용도: 선착순 쿠폰 발급 큐 처리
 * 접두사: coupon:issue:async:{type}:{couponId}
 *
 * 다른 쿠폰 관련 Redis 키와 명확히 구분하기 위해
 * 'issue:async' 네임스페이스 사용
 */
@Component
public class RedisCouponKeyGenerator {

    private static final String BASE_PREFIX = "coupon:issue:async";
    private static final String STOCK_PREFIX = BASE_PREFIX + ":stock:%d";
    private static final String USERS_SET_PREFIX = BASE_PREFIX + ":participants:%d";
    private static final String QUEUE_PREFIX = BASE_PREFIX + ":queue:%d";

    /**
     * 재고 카운트 저장용 키 생성
     * Format: coupon:issue:async:stock:{couponId}
     */
    public String generateStockKey(Long couponId) {
        validateCouponId(couponId);
        return String.format(STOCK_PREFIX, couponId);
    }

    /**
     * 중복 체크 및 참여자 카운팅용 Set 키 생성
     * Format: coupon:issue:async:participants:{couponId}
     */
    public String generateUsersSetKey(Long couponId) {
        validateCouponId(couponId);
        return String.format(USERS_SET_PREFIX, couponId);
    }

    /**
     * FIFO 큐 처리용 List 키 생성
     * Format: coupon:issue:async:queue:{couponId}
     */
    public String generateQueueKey(Long couponId) {
        validateCouponId(couponId);
        return String.format(QUEUE_PREFIX, couponId);
    }

    private void validateCouponId(Long couponId) {
        if (couponId == null) {
            throw new IllegalArgumentException("Coupon ID cannot be null");
        }
    }
}
