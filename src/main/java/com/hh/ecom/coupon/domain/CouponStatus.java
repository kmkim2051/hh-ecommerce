package com.hh.ecom.coupon.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CouponStatus {
    ACTIVE("활성화(발급 가능)"),
    SOLD_OUT("수량 소진"),
    EXPIRED("유효기간 만료"),
    DISABLED("비활성화"),
    ;

    private final String description;
}
