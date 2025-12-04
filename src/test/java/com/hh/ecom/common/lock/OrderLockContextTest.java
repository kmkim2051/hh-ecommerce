package com.hh.ecom.common.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderLockContext 테스트")
class OrderLockContextTest {

    @Test
    @DisplayName("사용자 포인트, 상품, 쿠폰 락 키를 정렬하여 생성한다")
    void shouldGenerateSortedLockKeys() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(100L, 200L, 150L);
        Long couponUserId = 50L;

        // when
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .withCoupon(couponUserId)
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys).hasSize(5); // point + 3 products + coupon
        assertThat(lockKeys).containsExactly(
            "lock:coupon:user:50",
            "lock:point:user:1",
            "lock:product:100",
            "lock:product:150",
            "lock:product:200"
        );

        // 정렬 검증
        assertThat(lockKeys).isSorted();
    }

    @Test
    @DisplayName("쿠폰이 없는 경우 쿠폰 락 키를 생성하지 않는다")
    void shouldNotIncludeCouponLockWhenCouponUserIdIsNull() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(100L);

        // when
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .withCoupon(null) // null 쿠폰
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys).hasSize(2); // point + 1 product only
        assertThat(lockKeys).containsExactly(
            "lock:point:user:1",
            "lock:product:100"
        );
    }

    @Test
    @DisplayName("중복된 상품 ID는 한 번만 락 키를 생성한다")
    void shouldDeduplicateProductIds() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(100L, 100L, 200L, 100L);

        // when
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys).hasSize(3); // point + 2 unique products
        assertThat(lockKeys).containsExactly(
            "lock:point:user:1",
            "lock:product:100",
            "lock:product:200"
        );
    }

    @Test
    @DisplayName("메서드 체이닝이 정상 작동한다")
    void shouldSupportMethodChaining() {
        // given & when
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(10L)
            .withProducts(List.of(1L, 2L))
            .withCoupon(5L)
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys).isNotEmpty();
    }

    @Test
    @DisplayName("빈 상품 리스트로도 락 키를 생성할 수 있다")
    void shouldHandleEmptyProductList() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of();

        // when
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys).hasSize(1); // point only
        assertThat(lockKeys).containsExactly("lock:point:user:1");
    }

    @Test
    @DisplayName("락 키 형식이 올바르게 생성된다")
    void shouldGenerateCorrectLockKeyFormat() {
        // given & when
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(123L)
            .withProducts(List.of(456L))
            .withCoupon(789L)
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys)
            .allMatch(key -> key.startsWith("lock:"))
            .allMatch(key -> key.contains(":"));
    }

    @Test
    @DisplayName("동일한 파라미터로 생성하면 동일한 락 키가 생성된다")
    void shouldGenerateConsistentLockKeys() {
        // given
        Long userId = 1L;
        List<Long> productIds = List.of(100L, 200L);
        Long couponUserId = 50L;

        // when
        List<String> lockKeys1 = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .withCoupon(couponUserId)
            .buildSortedLockKeys();

        List<String> lockKeys2 = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .withCoupon(couponUserId)
            .buildSortedLockKeys();

        // then
        assertThat(lockKeys1).isEqualTo(lockKeys2);
    }
}
