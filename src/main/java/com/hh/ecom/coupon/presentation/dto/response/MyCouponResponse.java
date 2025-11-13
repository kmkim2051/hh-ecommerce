package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "내 쿠폰 응답")
public class MyCouponResponse {

    @Schema(description = "발급된 쿠폰 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "쿠폰 ID", example = "1")
    private Long couponId;

    @Schema(description = "쿠폰명", example = "신규가입 할인 쿠폰")
    private String couponName;

    @Schema(description = "할인 금액", example = "5000")
    private BigDecimal discountAmount;

    @Schema(description = "사용 여부", example = "false")
    private Boolean isUsed;

    @Schema(description = "발급일시", example = "2025-01-07T10:30:00")
    private LocalDateTime issuedAt;

    @Schema(description = "사용일시", example = "2025-01-07T15:30:00")
    private LocalDateTime usedAt;

    @Schema(description = "만료일", example = "2025-12-31T23:59:59")
    private LocalDateTime expireDate;

    public static MyCouponResponse from(CouponUserWithCoupon couponUserWithCoupon) {
        return MyCouponResponse.builder()
                .id(couponUserWithCoupon.getCouponUser().getId())
                .userId(couponUserWithCoupon.getCouponUser().getUserId())
                .couponId(couponUserWithCoupon.getCouponUser().getCouponId())
                .couponName(couponUserWithCoupon.getCoupon().getName())
                .discountAmount(couponUserWithCoupon.getCoupon().getDiscountAmount())
                .isUsed(couponUserWithCoupon.getCouponUser().isUsed())
                .issuedAt(couponUserWithCoupon.getCouponUser().getIssuedAt())
                .usedAt(couponUserWithCoupon.getCouponUser().getUsedAt())
                .expireDate(couponUserWithCoupon.getCouponUser().getExpireDate())
                .build();
    }
}
