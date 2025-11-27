package com.hh.ecom.common.lock;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class LockKeyGenerator {
    private static final String DELIMITER = ":";

    private static final String LOCK_PREFIX = "lock";
    private static final String PRODUCT_PREFIX = "product";
    private static final String POINT_PREFIX = "point:user";
    private static final String COUPON_PREFIX = "coupon:user";

    /**
     * 주문 생성을 위한 락 키 생성
     * Product, Point, Coupon(옵션) 키를 생성하고 정렬합니다.
     *
     * @param userId 사용자 ID
     * @param productIds 상품 ID 리스트
     * @param couponUserId 쿠폰 유저 ID (nullable - 쿠폰을 사용하지 않는 경우)
     * @return 정렬된 락 키 리스트 (정렬: default 오름차순)
     */
    public List<String> generateOrderLockKeys(Long userId, List<Long> productIds, Long couponUserId) {
        List<String> lockKeys = new ArrayList<>();

        lockKeys.add(buildUserPointLockKey(userId));

        productIds.stream()
            .distinct()
            .forEach(productId -> lockKeys.add(buildProductLockKey(productId)));

        if (couponUserId != null) {
            lockKeys.add(buildCouponLockKey(couponUserId));
        }

        Collections.sort(lockKeys);
        return lockKeys;
    }

    public String generateCouponIssueLockKey(Long couponId) {
        return buildKey(LOCK_PREFIX, "coupon:issue", String.valueOf(couponId));
    }

    public String generatePointLockKey(Long userId) {
        return buildUserPointLockKey(userId);
    }

    public String generateCouponUseLockKey(Long couponUserId) {
        return buildCouponLockKey(couponUserId);
    }

    public List<String> generateProductLockKeys(List<Long> productIds) {
        return productIds.stream()
            .distinct()
            .map(this::buildProductLockKey)
            .sorted()
            .toList();
    }

    // ============== Private Helper Methods ==============
    private String buildProductLockKey(Long productId) {
        return buildKey(LOCK_PREFIX, PRODUCT_PREFIX, String.valueOf(productId));
    }

    private String buildUserPointLockKey(Long userId) {
        return buildKey(LOCK_PREFIX, POINT_PREFIX, String.valueOf(userId));
    }

    private String buildCouponLockKey(Long couponUserId) {
        return buildKey(LOCK_PREFIX, COUPON_PREFIX, String.valueOf(couponUserId));
    }

    private String buildKey(String... parts) {
        return String.join(DELIMITER, parts);
    }
}
