package com.hh.ecom.common.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LockDomain 테스트")
class LockDomainTest {

    @Test
    @DisplayName("USER_POINT 도메인의 락 키를 올바르게 생성한다")
    void shouldFormatUserPointLockKey() {
        // given
        Long userId = 123L;

        // when
        String lockKey = LockDomain.USER_POINT.formatKey(userId);

        // then
        assertThat(lockKey).isEqualTo("lock:point:user:123");
    }

    @Test
    @DisplayName("PRODUCT 도메인의 락 키를 올바르게 생성한다")
    void shouldFormatProductLockKey() {
        // given
        Long productId = 456L;

        // when
        String lockKey = LockDomain.PRODUCT.formatKey(productId);

        // then
        assertThat(lockKey).isEqualTo("lock:product:456");
    }

    @Test
    @DisplayName("COUPON_USER 도메인의 락 키를 올바르게 생성한다")
    void shouldFormatCouponUserLockKey() {
        // given
        Long couponUserId = 789L;

        // when
        String lockKey = LockDomain.COUPON_USER.formatKey(couponUserId);

        // then
        assertThat(lockKey).isEqualTo("lock:coupon:user:789");
    }

    @Test
    @DisplayName("null ID로 락 키를 생성하면 예외가 발생한다")
    void shouldThrowExceptionWhenIdIsNull() {
        // when & then
        assertThatThrownBy(() -> LockDomain.USER_POINT.formatKey(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Lock resource ID cannot be null")
            .hasMessageContaining("USER_POINT");
    }

    @Test
    @DisplayName("모든 도메인의 prefix를 조회할 수 있다")
    void shouldGetPrefix() {
        // when & then
        assertThat(LockDomain.USER_POINT.getPrefix()).isEqualTo("lock:point:user");
        assertThat(LockDomain.PRODUCT.getPrefix()).isEqualTo("lock:product");
        assertThat(LockDomain.COUPON_USER.getPrefix()).isEqualTo("lock:coupon:user");
    }

    @Test
    @DisplayName("동일한 ID로 생성한 락 키는 항상 동일하다")
    void shouldGenerateConsistentLockKey() {
        // given
        Long id = 100L;

        // when
        String lockKey1 = LockDomain.PRODUCT.formatKey(id);
        String lockKey2 = LockDomain.PRODUCT.formatKey(id);

        // then
        assertThat(lockKey1).isEqualTo(lockKey2);
    }
}
