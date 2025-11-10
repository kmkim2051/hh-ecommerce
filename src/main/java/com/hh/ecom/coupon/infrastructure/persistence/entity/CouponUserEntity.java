package com.hh.ecom.coupon.infrastructure.persistence.entity;

import com.hh.ecom.coupon.domain.CouponUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponUserEntity {
    private Long id;
    private Long userId;
    private Long couponId;
    private Long orderId;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expireDate;
    private Boolean isUsed;

    public CouponUser toDomain() {
        return CouponUser.builder()
                .id(this.id)
                .userId(this.userId)
                .couponId(this.couponId)
                .orderId(this.orderId)
                .issuedAt(this.issuedAt)
                .usedAt(this.usedAt)
                .expireDate(this.expireDate)
                .isUsed(this.isUsed)
                .build();
    }

    public static CouponUserEntity from(CouponUser couponUser) {
        return CouponUserEntity.builder()
                .id(couponUser.getId())
                .userId(couponUser.getUserId())
                .couponId(couponUser.getCouponId())
                .orderId(couponUser.getOrderId())
                .issuedAt(couponUser.getIssuedAt())
                .usedAt(couponUser.getUsedAt())
                .expireDate(couponUser.getExpireDate())
                .isUsed(couponUser.getIsUsed())
                .build();
    }
}
