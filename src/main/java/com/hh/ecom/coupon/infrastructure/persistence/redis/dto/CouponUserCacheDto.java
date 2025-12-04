package com.hh.ecom.coupon.infrastructure.persistence.redis.dto;

import com.hh.ecom.coupon.domain.CouponUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Redis 캐싱을 위한 CouponUser DTO
 * - 도메인 객체와 직렬화 로직 완전 분리
 * - Jackson 호환성을 위한 기본 생성자 제공
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponUserCacheDto {
    private Long id;
    private Long userId;
    private Long couponId;
    private Long orderId;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expireDate;

    @JsonProperty("isUsed")
    private boolean isUsed;
    public static CouponUserCacheDto from(CouponUser couponUser) {
        return new CouponUserCacheDto(
                couponUser.getId(),
                couponUser.getUserId(),
                couponUser.getCouponId(),
                couponUser.getOrderId(),
                couponUser.getIssuedAt(),
                couponUser.getUsedAt(),
                couponUser.getExpireDate(),
                couponUser.isUsed()
        );
    }

    public CouponUser toDomain() {
        return CouponUser.builder()
                .id(id)
                .userId(userId)
                .couponId(couponId)
                .orderId(orderId)
                .issuedAt(issuedAt)
                .usedAt(usedAt)
                .expireDate(expireDate)
                .isUsed(isUsed)
                .build();
    }
}
