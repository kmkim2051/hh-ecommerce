package com.hh.ecom.common.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SimpleLockResource 테스트")
class SimpleLockResourceTest {

    @Test
    @DisplayName("락 키를 올바르게 생성한다")
    void shouldGenerateLockKey() {
        // given
        SimpleLockResource resource = new SimpleLockResource(LockDomain.PRODUCT, 100L);

        // when
        String lockKey = resource.getLockKey();

        // then
        assertThat(lockKey).isEqualTo("lock:product:100");
    }

    @Test
    @DisplayName("null 도메인으로 생성하면 예외가 발생한다")
    void shouldThrowExceptionWhenDomainIsNull() {
        // when & then
        assertThatThrownBy(() -> new SimpleLockResource(null, 100L))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("LockDomain cannot be null");
    }

    @Test
    @DisplayName("null ID로 생성하면 예외가 발생한다")
    void shouldThrowExceptionWhenIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new SimpleLockResource(LockDomain.PRODUCT, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Resource ID cannot be null");
    }

    @Test
    @DisplayName("동일한 도메인과 ID를 가진 리소스는 동등하다")
    void shouldBeEqualWhenSameDomainAndId() {
        // given
        SimpleLockResource resource1 = new SimpleLockResource(LockDomain.PRODUCT, 100L);
        SimpleLockResource resource2 = new SimpleLockResource(LockDomain.PRODUCT, 100L);

        // when & then
        assertThat(resource1).isEqualTo(resource2);
        assertThat(resource1.hashCode()).isEqualTo(resource2.hashCode());
    }

    @Test
    @DisplayName("다른 도메인을 가진 리소스는 동등하지 않다")
    void shouldNotBeEqualWhenDifferentDomain() {
        // given
        SimpleLockResource resource1 = new SimpleLockResource(LockDomain.PRODUCT, 100L);
        SimpleLockResource resource2 = new SimpleLockResource(LockDomain.USER_POINT, 100L);

        // when & then
        assertThat(resource1).isNotEqualTo(resource2);
    }

    @Test
    @DisplayName("다른 ID를 가진 리소스는 동등하지 않다")
    void shouldNotBeEqualWhenDifferentId() {
        // given
        SimpleLockResource resource1 = new SimpleLockResource(LockDomain.PRODUCT, 100L);
        SimpleLockResource resource2 = new SimpleLockResource(LockDomain.PRODUCT, 200L);

        // when & then
        assertThat(resource1).isNotEqualTo(resource2);
    }

    @Test
    @DisplayName("자기 자신과는 동등하다")
    void shouldBeEqualToSelf() {
        // given
        SimpleLockResource resource = new SimpleLockResource(LockDomain.PRODUCT, 100L);

        // when & then
        assertThat(resource).isEqualTo(resource);
    }

    @Test
    @DisplayName("다른 타입의 객체와는 동등하지 않다")
    void shouldNotBeEqualToDifferentType() {
        // given
        SimpleLockResource resource = new SimpleLockResource(LockDomain.PRODUCT, 100L);
        String otherType = "lock:product:100";

        // when & then
        assertThat(resource).isNotEqualTo(otherType);
    }
}
