package com.hh.ecom.coupon.infrastructure.persistence.redis.dto;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Redis 캐싱을 위한 Coupon DTO
 * - Lombok을 사용하여 간결한 코드 유지
 * - 도메인 객체와 직렬화 로직 완전 분리
 * - Jackson 호환성을 위한 기본 생성자 제공
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponCacheDto {
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
    public static CouponCacheDto from(Coupon coupon) {
        return new CouponCacheDto(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountAmount(),
                coupon.getTotalQuantity(),
                coupon.getAvailableQuantity(),
                coupon.getStatus(),
                coupon.getStartDate(),
                coupon.getEndDate(),
                coupon.getIsActive(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }

    public Coupon toDomain() {
        return Coupon.builder()
                .id(id)
                .name(name)
                .discountAmount(discountAmount)
                .totalQuantity(totalQuantity)
                .availableQuantity(availableQuantity)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .isActive(isActive)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
