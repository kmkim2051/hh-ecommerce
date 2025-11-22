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
            CouponEntity existing = couponJpaRepository.findById(coupon.getId())
                    .orElseThrow(() -> new RuntimeException(
                            "수정할 쿠폰을 찾을 수 없습니다. id=" + coupon.getId()
                    ));
            updateEntity(existing, coupon);
            entity = existing;
        } else {
            entity = CouponEntity.from(coupon);
        }

        CouponEntity savedEntity = couponJpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    private void updateEntity(CouponEntity existing, Coupon coupon) {
        existing.updateFrom(coupon);
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
