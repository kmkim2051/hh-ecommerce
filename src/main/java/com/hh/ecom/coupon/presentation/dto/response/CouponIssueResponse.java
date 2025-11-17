package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "쿠폰 발급 응답")
public record CouponIssueResponse(
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

        @Schema(description = "발급일시", example = "2025-01-07T10:30:00")
        LocalDateTime issuedAt,

        @Schema(description = "만료일", example = "2025-12-31T23:59:59")
        LocalDateTime expireDate,

        @Schema(description = "메시지", example = "쿠폰이 발급되었습니다.")
        String message
) {
    public static CouponIssueResponse from(CouponUser couponUser, Coupon coupon, String message) {
        return new CouponIssueResponse(
                couponUser.getId(),
                couponUser.getUserId(),
                couponUser.getCouponId(),
                coupon.getName(),
                coupon.getDiscountAmount(),
                couponUser.getIssuedAt(),
                couponUser.getExpireDate(),
                message
        );
    }
}
