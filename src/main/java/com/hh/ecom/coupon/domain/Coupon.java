package com.hh.ecom.coupon.domain;

import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Coupon {
    private final Long id;
    private final String name;

    private final BigDecimal discountAmount;
    private final Integer totalQuantity;
    private final Integer availableQuantity;
    private final CouponStatus status;

    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Coupon create(
            String name,
            BigDecimal discountAmount,
            Integer totalQuantity,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        validateCreateParams(name, discountAmount, totalQuantity, startDate, endDate);

        return Coupon.builder()
                .name(name)
                .discountAmount(discountAmount)
                .totalQuantity(totalQuantity)
                .availableQuantity(totalQuantity)
                .status(CouponStatus.ACTIVE)
                .startDate(startDate)
                .endDate(endDate)
                .isActive(true)
                .build();
    }

    private static void validateCreateParams(
            String name,
            BigDecimal discountAmount,
            Integer totalQuantity,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        if (name == null || name.isBlank()) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "쿠폰명은 필수입니다.");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "할인 금액은 0보다 커야 합니다.");
        }
        if (totalQuantity == null || totalQuantity <= 0) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "총 수량은 0보다 커야 합니다.");
        }
        if (startDate == null || endDate == null) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "시작일과 종료일은 필수입니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "종료일은 시작일보다 늦어야 합니다.");
        }
    }

    public void validateIssuable() {
        if (!isActive) {
            throw new CouponException(CouponErrorCode.COUPON_NOT_ACTIVE);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startDate) || now.isAfter(endDate)) {
            throw new CouponException(CouponErrorCode.COUPON_EXPIRED);
        }

        if (status == CouponStatus.SOLD_OUT || availableQuantity <= 0) {
            throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
        }

        if (status == CouponStatus.EXPIRED) {
            throw new CouponException(CouponErrorCode.COUPON_EXPIRED);
        }

        if (status == CouponStatus.DISABLED) {
            throw new CouponException(CouponErrorCode.COUPON_NOT_ACTIVE);
        }
    }

    public Coupon decreaseQuantity() {
        if (availableQuantity <= 0) {
            throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
        }

        int newAvailableQuantity = this.availableQuantity - 1;
        CouponStatus newStatus = (newAvailableQuantity == 0) ? CouponStatus.SOLD_OUT : this.status;

        return this.toBuilder()
                .availableQuantity(newAvailableQuantity)
                .status(newStatus)
                .build();
    }

    public Coupon increaseQuantity() {
        if (availableQuantity >= totalQuantity) {
            throw new CouponException(CouponErrorCode.INVALID_QUANTITY, "복원할 수량이 초과되었습니다.");
        }

        int newAvailableQuantity = this.availableQuantity + 1;
        CouponStatus newStatus = this.status == CouponStatus.SOLD_OUT ? CouponStatus.ACTIVE : this.status;

        return this.toBuilder()
                .availableQuantity(newAvailableQuantity)
                .status(newStatus)
                .build();
    }

    public Coupon disable() {
        return this.toBuilder()
                .status(CouponStatus.DISABLED)
                .build();
    }

    public boolean isIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return isActive
                && status == CouponStatus.ACTIVE
                && availableQuantity > 0
                && !now.isBefore(startDate)
                && !now.isAfter(endDate);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDate);
    }
}
