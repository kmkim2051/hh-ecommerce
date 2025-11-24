package com.hh.ecom.coupon.infrastructure.persistence.entity;

import com.hh.ecom.coupon.domain.CouponUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_user",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CouponUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_id")
    private Long userId;

    @Column(nullable = false, name = "coupon_id")
    private Long couponId;

    @Column(name = "order_id")
    private Long orderId;

    @CreatedDate
    @Column(nullable = false, updatable = false, name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(nullable = false, name = "expire_date")
    private LocalDateTime expireDate;

    @Column(nullable = false, name = "is_used")
    private boolean isUsed;

    @Version
    @Column(nullable = false)
    private Long version;

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
                .isUsed(couponUser.isUsed())
                .build();
    }
}
