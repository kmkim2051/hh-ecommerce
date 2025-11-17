package com.hh.ecom.point.domain;

import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Point {
    private final Long id;
    private final Long userId;
    private final BigDecimal balance;
    private final LocalDateTime updatedAt;

    public static Point createWithUserId(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return Point.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .updatedAt(now)
                .build();
    }

    private Point withUpdate(PointBuilder builder) {
        return builder
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public boolean hasEnoughBalance(BigDecimal amount) {
        if (isNullOrZero(amount)) {
            return false;
        }
        return this.balance.compareTo(amount) >= 0;
    }

    private static boolean isNullOrZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    public Point charge(BigDecimal amount) {
        validateAmount(amount);
        return withUpdate(this.toBuilder()
                .balance(this.balance.add(amount))
        );
    }

    public Point use(BigDecimal amount) {
        validateAmount(amount);
        if (!hasEnoughBalance(amount)) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE,
                    "요청: " + amount + ", 현재 잔액: " + this.balance);
        }
        return withUpdate(this.toBuilder()
                .balance(this.balance.subtract(amount))
        );
    }

    public Point refund(BigDecimal amount) {
        validateAmount(amount);
        return withUpdate(this.toBuilder()
                .balance(this.balance.add(amount))
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new PointException(PointErrorCode.INVALID_AMOUNT, "null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PointException(PointErrorCode.INVALID_AMOUNT, "입력값: " + amount);
        }
    }

    public boolean isZeroBalance() {
        return this.balance.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositiveBalance() {
        return this.balance.compareTo(BigDecimal.ZERO) > 0;
    }
}
