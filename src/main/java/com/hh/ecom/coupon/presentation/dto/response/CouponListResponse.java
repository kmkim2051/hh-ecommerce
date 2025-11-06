package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.Coupon;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "쿠폰 목록 응답")
public class CouponListResponse {

    @Schema(description = "쿠폰 목록")
    private List<CouponResponse> coupons;

    @Schema(description = "전체 개수", example = "10")
    private Integer totalCount;

    public static CouponListResponse from(List<Coupon> coupons) {
        List<CouponResponse> couponResponses = coupons.stream()
                .map(CouponResponse::from)
                .collect(Collectors.toList());

        return CouponListResponse.builder()
                .coupons(couponResponses)
                .totalCount(couponResponses.size())
                .build();
    }
}
