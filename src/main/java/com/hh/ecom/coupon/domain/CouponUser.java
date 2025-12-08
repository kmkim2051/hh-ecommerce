package com.hh.ecom.coupon.domain;

import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

import static java.util.Objects.*;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CouponUser {
    private final Long id;
    private final Long userId;
    private final Long couponId;
    private final Long orderId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime usedAt;
    private final LocalDateTime expireDate;
    private final boolean isUsed;

    public static CouponUser issue(Long userId, Long couponId, LocalDateTime expireDate) {
        requireNonNull(userId, "userId is required");
        requireNonNull(couponId, "couponId is required");
        requireNonNull(expireDate, "expireDate is required");

        LocalDateTime now = LocalDateTime.now();
        return CouponUser.builder()
                .userId(userId)
                .couponId(couponId)
                .issuedAt(now)
                .expireDate(expireDate)
                .isUsed(false)
                .build();
    }

    public void validateUsable() {
        if (isUsed) {
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_USED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expireDate)) {
            throw new CouponException(CouponErrorCode.COUPON_USER_EXPIRED);
        }
    }

    public CouponUser use(Long orderId) {
        validateUsable();

        if (orderId == null) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "주문 ID는 필수입니다.");
        }

        return this.toBuilder()
                .orderId(orderId)
                .usedAt(LocalDateTime.now())
                .isUsed(true)
                .build();
    }

    public boolean isUsable() {
        return !(isUsed || isExpired());
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireDate);
    }
}
