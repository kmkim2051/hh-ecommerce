package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "쿠폰 발급 응답")
public class CouponIssueResponse {

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

    @Schema(description = "발급일시", example = "2025-01-07T10:30:00")
    private LocalDateTime issuedAt;

    @Schema(description = "만료일", example = "2025-12-31T23:59:59")
    private LocalDateTime expireDate;

    @Schema(description = "메시지", example = "쿠폰이 발급되었습니다.")
    private String message;

    public static CouponIssueResponse from(CouponUser couponUser, Coupon coupon, String message) {
        return CouponIssueResponse.builder()
                .id(couponUser.getId())
                .userId(couponUser.getUserId())
                .couponId(couponUser.getCouponId())
                .couponName(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .issuedAt(couponUser.getIssuedAt())
                .expireDate(couponUser.getExpireDate())
                .message(message)
                .build();
    }
}
