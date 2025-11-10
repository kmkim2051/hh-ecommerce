package com.hh.ecom.coupon.infrastructure.persistence.entity;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponEntity {
    private Long id;
    private String name;
    private BigDecimal discountAmount;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private CouponStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public Coupon toDomain() {
        return Coupon.builder()
                .id(this.id)
                .name(this.name)
                .discountAmount(this.discountAmount)
                .totalQuantity(this.totalQuantity)
                .availableQuantity(this.availableQuantity)
                .status(this.status)
                .startDate(this.startDate)
                .endDate(this.endDate)
                .isActive(this.isActive)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .version(this.version)
                .build();
    }

    public static CouponEntity from(Coupon coupon) {
        return CouponEntity.builder()
                .id(coupon.getId())
                .name(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .availableQuantity(coupon.getAvailableQuantity())
                .status(coupon.getStatus())
                .startDate(coupon.getStartDate())
                .endDate(coupon.getEndDate())
                .isActive(coupon.getIsActive())
                .createdAt(coupon.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt())
                .version(coupon.getVersion())
                .build();
    }
}
