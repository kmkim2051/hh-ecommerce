package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "쿠폰 응답")
public record CouponResponse(
        @Schema(description = "쿠폰 ID", example = "1")
        Long id,

        @Schema(description = "쿠폰명", example = "신규가입 할인 쿠폰")
        String name,

        @Schema(description = "할인 금액", example = "5000")
        BigDecimal discountAmount,

        @Schema(description = "총 수량", example = "100")
        Integer totalQuantity,

        @Schema(description = "남은 수량", example = "50")
        Integer availableQuantity,

        @Schema(description = "쿠폰 상태", example = "ACTIVE")
        CouponStatus status,

        @Schema(description = "발급 시작일", example = "2025-01-01T00:00:00")
        LocalDateTime startDate,

        @Schema(description = "발급 종료일", example = "2025-12-31T23:59:59")
        LocalDateTime endDate,

        @Schema(description = "활성화 여부", example = "true")
        Boolean isActive
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountAmount(),
                coupon.getTotalQuantity(),
                coupon.getAvailableQuantity(),
                coupon.getStatus(),
                coupon.getStartDate(),
                coupon.getEndDate(),
                coupon.getIsActive()
        );
    }
}
