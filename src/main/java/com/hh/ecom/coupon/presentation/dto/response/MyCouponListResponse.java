package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "내 쿠폰 목록 응답")
public class MyCouponListResponse {

    @Schema(description = "내 쿠폰 목록")
    private List<MyCouponResponse> coupons;

    @Schema(description = "전체 개수", example = "5")
    private Integer totalCount;

    public static MyCouponListResponse from(List<CouponUserWithCoupon> couponUserWithCoupons) {
        List<MyCouponResponse> myCouponResponses = couponUserWithCoupons.stream()
                .map(MyCouponResponse::from)
                .collect(Collectors.toList());

        return MyCouponListResponse.builder()
                .coupons(myCouponResponses)
                .totalCount(myCouponResponses.size())
                .build();
    }
}
