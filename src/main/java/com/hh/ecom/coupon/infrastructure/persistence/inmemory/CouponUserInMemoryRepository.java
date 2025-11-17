package com.hh.ecom.coupon.infrastructure.persistence.inmemory;

import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponUserEntity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CouponUserInMemoryRepository implements CouponUserRepository {
    private final Map<Long, CouponUserEntity> couponUsers = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponUser save(CouponUser couponUser) {
        CouponUserEntity entity;

        if (couponUser.getId() == null) {
            // 새로운 쿠폰 발급
            Long newId = idGenerator.getAndIncrement();
            entity = CouponUserEntity.from(couponUser);
            entity = CouponUserEntity.builder()
                    .id(newId)
                    .userId(entity.getUserId())
                    .couponId(entity.getCouponId())
                    .orderId(entity.getOrderId())
                    .issuedAt(entity.getIssuedAt())
                    .usedAt(entity.getUsedAt())
                    .expireDate(entity.getExpireDate())
                    .isUsed(entity.isUsed())
                    .build();
        } else {
            // 기존 쿠폰 업데이트
            entity = CouponUserEntity.from(couponUser);
        }

        couponUsers.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public Optional<CouponUser> findById(Long id) {
        return Optional.ofNullable(couponUsers.get(id))
                .map(CouponUserEntity::toDomain);
    }

    @Override
    public Optional<CouponUser> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponUsers.values().stream()
                .filter(entity -> entity.getUserId().equals(userId) && entity.getCouponId().equals(couponId))
                .findFirst()
                .map(CouponUserEntity::toDomain);
    }

    @Override
    public List<CouponUser> findByUserId(Long userId) {
        return couponUsers.values().stream()
                .filter(entity -> entity.getUserId().equals(userId))
                .map(CouponUserEntity::toDomain)
                .toList();
    }

    @Override
    public List<CouponUser> findByUserIdAndIsUsed(Long userId, Boolean isUsed) {
        return couponUsers.values().stream()
                .filter(entity -> Objects.equals(entity.getUserId(), userId) && entity.isUsed() == isUsed)
                .map(CouponUserEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        couponUsers.clear();
        idGenerator.set(1);
    }
}
