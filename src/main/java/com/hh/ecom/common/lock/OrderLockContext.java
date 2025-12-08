package com.hh.ecom.common.lock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 주문 생성 시 필요한 분산락 리소스를 빌더 패턴으로 구성하는 컨텍스트 클래스
 *
 * <p>사용 예시:
 * <pre>
 * List&lt;String&gt; lockKeys = new OrderLockContext()
 *     .withUserPoint(userId)
 *     .withProducts(productIds)
 *     .withCoupon(couponUserId)
 *     .buildSortedLockKeys();
 * </pre>
 */
public class OrderLockContext {
    private final Set<LockableResource> resources = new HashSet<>();

    public OrderLockContext withUserPoint(Long userId) {
        addToResources(SimpleLockResource.of(LockDomain.USER_POINT, userId));
        return this;
    }

    public OrderLockContext withProducts(List<Long> productIds) {
        productIds.stream()
            .distinct()
            .forEach(productId -> addToResources(SimpleLockResource.of(LockDomain.PRODUCT, productId)));
        return this;
    }

    public OrderLockContext withCoupon(Long couponUserId) {
        if (couponUserId != null) {
            addToResources(SimpleLockResource.of(LockDomain.COUPON_USER, couponUserId));
        }
        return this;
    }

    private void addToResources(LockableResource resource) {
        resources.add(resource);
    }

    // 데드락 방지를 위한 락 리소스 키 정렬
    public List<String> buildSortedLockKeys() {
        return resources.stream()
            .map(LockableResource::getLockKey)
            .sorted()
            .toList();
    }
}
