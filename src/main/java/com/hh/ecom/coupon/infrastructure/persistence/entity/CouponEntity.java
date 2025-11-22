package com.hh.ecom.coupon.infrastructure.persistence.entity;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CouponEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer availableQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
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
                .build();
    }

    public void updateFrom(Coupon coupon) {
        this.name = coupon.getName();
        this.discountAmount = coupon.getDiscountAmount();
        this.totalQuantity = coupon.getTotalQuantity();
        this.availableQuantity = coupon.getAvailableQuantity();
        this.status = coupon.getStatus();
        this.startDate = coupon.getStartDate();
        this.endDate = coupon.getEndDate();
        this.isActive = coupon.getIsActive();
    }
}
