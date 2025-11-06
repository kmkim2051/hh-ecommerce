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
public class PointTransaction {
    private final Long id;
    private final Long pointId;
    private final BigDecimal amount;
    private final TransactionType type;
    private final Long orderId;
    private final BigDecimal balanceAfter;
    private final LocalDateTime createdAt;

    public static PointTransaction create(
            Long pointId,
            BigDecimal amount,
            TransactionType type,
            Long orderId,
            BigDecimal balanceAfter
    ) {
        validateAmount(amount);
        return PointTransaction.builder()
                .pointId(pointId)
                .amount(amount)
                .type(type)
                .orderId(orderId)
                .balanceAfter(balanceAfter)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PointException(PointErrorCode.INVALID_AMOUNT,
                    "거래 금액은 0보다 커야 합니다. 입력값: " + amount);
        }
    }
    public boolean isNew() {
        return id == null;
    }

    public boolean isCharge() {
        return type == TransactionType.CHARGE;
    }

    public boolean isUse() {
        return type == TransactionType.USE;
    }

    public boolean isRefund() {
        return type == TransactionType.REFUND;
    }

    public boolean hasOrder() {
        return orderId != null;
    }
}
