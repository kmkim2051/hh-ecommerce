package com.hh.ecom.coupon.infrastructure.persistence;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.domain.exception.OptimisticLockException;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
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
                    .version(entity.getVersion())
                    .build();
        } else {
            // 기존 쿠폰 업데이트 - 낙관적 락 검증
            entity = CouponEntity.from(coupon);
            CouponEntity existingEntity = coupons.get(entity.getId());

            if (existingEntity == null) {
                throw new OptimisticLockException("쿠폰이 존재하지 않습니다. ID: " + entity.getId());
            }

            // 버전 충돌 검사
            if (!existingEntity.getVersion().equals(coupon.getVersion() - 1)) {
                throw new OptimisticLockException(
                    String.format("버전 충돌: 예상 버전=%d, 실제 버전=%d, 쿠폰 ID=%d",
                        coupon.getVersion() - 1, existingEntity.getVersion(), coupon.getId())
                );
            }
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
