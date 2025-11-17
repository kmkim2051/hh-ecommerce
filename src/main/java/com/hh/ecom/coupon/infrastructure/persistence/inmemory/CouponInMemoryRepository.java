package com.hh.ecom.coupon.infrastructure.persistence.inmemory;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.domain.exception.OptimisticLockException;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class CouponInMemoryRepository implements CouponRepository {
    private final Map<Long, CouponEntity> coupons = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        CouponEntity entity;

        if (coupon.getId() == null) {
            // 새로운 쿠폰 생성
            Long newId = idGenerator.getAndIncrement();
            entity = CouponEntity.from(coupon);
            entity = CouponEntity.builder()
                    .id(newId)
                    .name(entity.getName())
                    .discountAmount(entity.getDiscountAmount())
                    .totalQuantity(entity.getTotalQuantity())
                    .availableQuantity(entity.getAvailableQuantity())
                    .status(entity.getStatus())
                    .startDate(entity.getStartDate())
                    .endDate(entity.getEndDate())
                    .isActive(entity.getIsActive())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    // version은 Entity에서 자동 관리
                    .build();
        } else {
            // 기존 쿠폰 업데이트
            CouponEntity existingEntity = coupons.get(coupon.getId());

            if (existingEntity == null) {
                throw new OptimisticLockException("쿠폰이 존재하지 않습니다. ID: " + coupon.getId());
            }

            entity = CouponEntity.from(coupon);
            // ID와 version 유지
            entity = CouponEntity.builder()
                    .id(coupon.getId())
                    .name(entity.getName())
                    .discountAmount(entity.getDiscountAmount())
                    .totalQuantity(entity.getTotalQuantity())
                    .availableQuantity(entity.getAvailableQuantity())
                    .status(entity.getStatus())
                    .startDate(entity.getStartDate())
                    .endDate(entity.getEndDate())
                    .isActive(entity.getIsActive())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    // version은 InMemory에서는 무시 (JPA에서만 의미 있음)
                    .build();
        }

        coupons.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(coupons.get(id))
                .map(CouponEntity::toDomain);
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(Long id) {
        // In-memory에서는 락이 필요없으므로 findById와 동일
        return findById(id);
    }

    @Override
    public List<Coupon> findAll() {
        return coupons.values().stream()
                .map(CouponEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Coupon> findAllIssuable() {
        LocalDateTime now = LocalDateTime.now();
        return coupons.values().stream()
                .map(CouponEntity::toDomain)
                .filter(coupon -> coupon.getIsActive()
                        && coupon.getStatus() == CouponStatus.ACTIVE
                        && coupon.getAvailableQuantity() > 0
                        && !now.isBefore(coupon.getStartDate())
                        && !now.isAfter(coupon.getEndDate()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        coupons.clear();
        idGenerator.set(1);
    }
}
