package com.hh.ecom.coupon.infrastructure.persistence.jpa;

import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Primary
public class CouponUserRepositoryImpl implements CouponUserRepository {

    private final CouponUserJpaRepository couponUserJpaRepository;

    @Override
    public CouponUser save(CouponUser couponUser) {
        CouponUserEntity savedEntity;

        if (couponUser.getId() == null) {
            // 새로운 엔티티 생성 (id와 version은 JPA가 자동 생성)
            CouponUserEntity entity = CouponUserEntity.from(couponUser);
            savedEntity = couponUserJpaRepository.save(entity);
        } else {
            // 기존 엔티티 업데이트 - 기존 엔티티를 조회해서 version 유지
            CouponUserEntity existingEntity = couponUserJpaRepository.findById(couponUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("CouponUser not found: " + couponUser.getId()));

            // 기존 version을 유지하면서 새로운 엔티티 생성
            CouponUserEntity updatedEntity = CouponUserEntity.builder()
                    .id(existingEntity.getId())
                    .userId(couponUser.getUserId())
                    .couponId(couponUser.getCouponId())
                    .orderId(couponUser.getOrderId())
                    .issuedAt(couponUser.getIssuedAt())
                    .usedAt(couponUser.getUsedAt())
                    .expireDate(couponUser.getExpireDate())
                    .isUsed(couponUser.isUsed())
                    .version(existingEntity.getVersion())  // 기존 version 유지
                    .build();

            savedEntity = couponUserJpaRepository.save(updatedEntity);
        }

        return savedEntity.toDomain();
    }

    @Override
    public Optional<CouponUser> findById(Long id) {
        return couponUserJpaRepository.findById(id)
                .map(CouponUserEntity::toDomain);
    }

    @Override
    public Optional<CouponUser> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponUserJpaRepository.findByUserIdAndCouponId(userId, couponId)
                .map(CouponUserEntity::toDomain);
    }

    @Override
    public List<CouponUser> findByUserId(Long userId) {
        return couponUserJpaRepository.findByUserId(userId).stream()
                .map(CouponUserEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponUser> findByUserIdAndIsUsed(Long userId, Boolean isUsed) {
        return couponUserJpaRepository.findByUserIdAndIsUsed(userId, isUsed).stream()
                .map(CouponUserEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        couponUserJpaRepository.deleteAll();
    }
}
