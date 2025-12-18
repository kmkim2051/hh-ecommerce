package com.hh.ecom.coupon.infrastructure.persistence.jpa;

import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.infrastructure.persistence.entity.CouponUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponUserRepositoryImpl implements CouponUserRepository {

    private final CouponUserJpaRepository couponUserJpaRepository;

    @Override
    public CouponUser save(CouponUser couponUser) {
        CouponUserEntity savedEntity;

        if (couponUser.getId() == null) {
            CouponUserEntity entity = CouponUserEntity.from(couponUser);
            savedEntity = couponUserJpaRepository.save(entity);
        } else {
            CouponUserEntity existingEntity = couponUserJpaRepository.findById(couponUser.getId())
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_USER_NOT_FOUND,  couponUser.getId()));

            CouponUserEntity updatedEntity = CouponUserEntity.builder()
                    .id(existingEntity.getId())
                    .userId(couponUser.getUserId())
                    .couponId(couponUser.getCouponId())
                    .orderId(couponUser.getOrderId())
                    .issuedAt(couponUser.getIssuedAt())
                    .usedAt(couponUser.getUsedAt())
                    .expireDate(couponUser.getExpireDate())
                    .isUsed(couponUser.isUsed())
                    .version(existingEntity.getVersion())
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
                .toList();
    }

    @Override
    public List<CouponUser> findByUserIdAndIsUsed(Long userId, Boolean isUsed) {
        return couponUserJpaRepository.findByUserIdAndIsUsed(userId, isUsed).stream()
                .map(CouponUserEntity::toDomain)
                .toList();
    }

    @Override
    public List<CouponUser> findByCouponId(Long couponId) {
        return couponUserJpaRepository.findByCouponId(couponId).stream()
                .map(CouponUserEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        couponUserJpaRepository.deleteAll();
    }
}
