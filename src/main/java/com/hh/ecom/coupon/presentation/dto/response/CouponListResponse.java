package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.Coupon;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "쿠폰 목록 응답")
public record CouponListResponse(
        @Schema(description = "쿠폰 목록")
        List<CouponResponse> coupons,

        @Schema(description = "전체 개수", example = "10")
        Integer totalCount
) {
    public static CouponListResponse from(List<Coupon> coupons) {
        List<CouponResponse> couponResponses = coupons.stream()
                .map(CouponResponse::from)
                .toList();

        return new CouponListResponse(
                couponResponses,
                couponResponses.size()
        );
    }
}
