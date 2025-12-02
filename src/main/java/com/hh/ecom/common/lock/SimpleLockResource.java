package com.hh.ecom.common.lock;

import java.util.Objects;

/**
 * 단일 도메인 리소스에 대한 락을 표현하는 클래스
 * LockDomain과 ID를 조합하여 락 키를 생성합니다.
 */
public record SimpleLockResource(LockDomain domain, Long id) implements LockableResource {
    public SimpleLockResource(LockDomain domain, Long id) {
        this.domain = Objects.requireNonNull(domain, "LockDomain cannot be null");
        this.id = Objects.requireNonNull(id, "Resource ID cannot be null");
    }

    public static SimpleLockResource of(LockDomain domain, Long id) {
        return new SimpleLockResource(domain, id);
    }

    @Override
    public String getLockKey() {
        return domain.formatKey(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleLockResource that)) return false;
        return domain == that.domain && Objects.equals(id, that.id);
    }

    @Override
    public String toString() {
        return "SimpleLockResource[domain=%s, id=%s, lockKey=%s]".formatted(domain, id, getLockKey());
    }
}
