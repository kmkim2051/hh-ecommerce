package com.hh.ecom.point.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionType {
    CHARGE("충전", 1),
    USE("사용", -1),
    REFUND("환불", 1);

    private final String description;
    private final int sign; // 잔액 증가(+1) 또는 감소(-1)

    public boolean isPositive() {
        return sign > 0;
    }

    public boolean isNegative() {
        return sign < 0;
    }
}
