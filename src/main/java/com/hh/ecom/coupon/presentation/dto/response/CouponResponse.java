package com.hh.ecom.coupon.presentation.dto.response;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "쿠폰 응답")
public class CouponResponse {

    @Schema(description = "쿠폰 ID", example = "1")
    private Long id;

    @Schema(description = "쿠폰명", example = "신규가입 할인 쿠폰")
    private String name;

    @Schema(description = "할인 금액", example = "5000")
    private BigDecimal discountAmount;

    @Schema(description = "총 수량", example = "100")
    private Integer totalQuantity;

    @Schema(description = "남은 수량", example = "50")
    private Integer availableQuantity;

    @Schema(description = "쿠폰 상태", example = "ACTIVE")
    private CouponStatus status;

    @Schema(description = "발급 시작일", example = "2025-01-01T00:00:00")
    private LocalDateTime startDate;

    @Schema(description = "발급 종료일", example = "2025-12-31T23:59:59")
    private LocalDateTime endDate;

    @Schema(description = "활성화 여부", example = "true")
    private Boolean isActive;

    public static CouponResponse from(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .name(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .availableQuantity(coupon.getAvailableQuantity())
                .status(coupon.getStatus())
                .startDate(coupon.getStartDate())
                .endDate(coupon.getEndDate())
                .isActive(coupon.getIsActive())
                .build();
    }
}
