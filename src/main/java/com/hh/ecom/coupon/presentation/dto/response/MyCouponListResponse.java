package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 쿠폰 목록 응답")
public record MyCouponListResponse(
        @Schema(description = "내 쿠폰 목록")
        List<MyCouponResponse> coupons,

        @Schema(description = "전체 개수", example = "5")
        Integer totalCount
) {
    public static MyCouponListResponse from(List<CouponUserWithCoupon> couponUserWithCoupons) {
        List<MyCouponResponse> myCouponResponses = couponUserWithCoupons.stream()
                .map(MyCouponResponse::from)
                .toList();

        return new MyCouponListResponse(
                myCouponResponses,
                myCouponResponses.size()
        );
    }
}
