package com.hh.ecom.common.lock;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LockDomain {
    USER_POINT("lock:point:user"),
    PRODUCT("lock:product"),
    COUPON_USER("lock:coupon:user"),
    COUPON_ISSUE("lock:coupon:issue"),
    ;

    private final String prefix;

    /**
     * 도메인 prefix와 ID를 조합하여 락 키를 생성합니다.
     *
     * @param id 리소스 ID
     * @return 포맷된 락 키 (예: "lock:product:123")
     * @throws IllegalArgumentException ID가 null인 경우
     */
    public String formatKey(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Lock resource ID cannot be null for domain: " + this.name());
        }
        return prefix + ":" + id;
    }
}
