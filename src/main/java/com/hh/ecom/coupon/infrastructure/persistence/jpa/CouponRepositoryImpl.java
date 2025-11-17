package com.hh.ecom.coupon.infrastructure.persistence.jpa;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Primary
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        CouponEntity entity;
        if (coupon.getId() != null) {
            // UPDATE: 기존 엔티티를 조회하여 version 보존
            entity = couponJpaRepository.findById(coupon.getId())
                    .map(existing -> updateEntity(existing, coupon))
                    .orElse(CouponEntity.from(coupon));
        } else {
            // INSERT: 새 엔티티 생성
            entity = CouponEntity.from(coupon);
        }
        CouponEntity savedEntity = couponJpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    private CouponEntity updateEntity(CouponEntity existing, Coupon coupon) {
        return CouponEntity.builder()
                .id(existing.getId())
                .name(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .availableQuantity(coupon.getAvailableQuantity())
                .status(coupon.getStatus())
                .startDate(coupon.getStartDate())
                .endDate(coupon.getEndDate())
                .isActive(coupon.getIsActive())
                .createdAt(existing.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt())
                .build();
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findById(id)
                .map(CouponEntity::toDomain);
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(Long id) {
        return couponJpaRepository.findByIdWithLock(id)
                .map(CouponEntity::toDomain);
    }

    @Override
    public List<Coupon> findAll() {
        return couponJpaRepository.findAll().stream()
                .map(CouponEntity::toDomain)
                .toList();
    }

    @Override
    public List<Coupon> findAllIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return couponJpaRepository.findAllIssuable(CouponStatus.ACTIVE, now).stream()
                .map(CouponEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        couponJpaRepository.deleteAll();
    }
}
