package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "내 쿠폰 응답")
public record MyCouponResponse(
        @Schema(description = "발급된 쿠폰 ID", example = "1")
        Long id,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "쿠폰 ID", example = "1")
        Long couponId,

        @Schema(description = "쿠폰명", example = "신규가입 할인 쿠폰")
        String couponName,

        @Schema(description = "할인 금액", example = "5000")
        BigDecimal discountAmount,

        @Schema(description = "사용 여부", example = "false")
        Boolean isUsed,

        @Schema(description = "발급일시", example = "2025-01-07T10:30:00")
        LocalDateTime issuedAt,

        @Schema(description = "사용일시", example = "2025-01-07T15:30:00")
        LocalDateTime usedAt,

        @Schema(description = "만료일", example = "2025-12-31T23:59:59")
        LocalDateTime expireDate
) {
    public static MyCouponResponse from(CouponUserWithCoupon couponUserWithCoupon) {
        return new MyCouponResponse(
                couponUserWithCoupon.getCouponUser().getId(),
                couponUserWithCoupon.getCouponUser().getUserId(),
                couponUserWithCoupon.getCouponUser().getCouponId(),
                couponUserWithCoupon.getCoupon().getName(),
                couponUserWithCoupon.getCoupon().getDiscountAmount(),
                couponUserWithCoupon.getCouponUser().isUsed(),
                couponUserWithCoupon.getCouponUser().getIssuedAt(),
                couponUserWithCoupon.getCouponUser().getUsedAt(),
                couponUserWithCoupon.getCouponUser().getExpireDate()
        );
    }
}
