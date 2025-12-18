package com.hh.ecom.coupon.infrastructure.persistence.redis;

import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.infrastructure.persistence.redis.dto.CouponUserCacheDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@Primary
public class CouponUserRedisRepository implements CouponUserRepository {

    private final RedisTemplate<String, Object> couponRedisTemplate;

    public CouponUserRedisRepository(@Qualifier("couponRedisTemplate") RedisTemplate<String, Object> couponRedisTemplate) {
        this.couponRedisTemplate = couponRedisTemplate;
    }

    private static final String COUPON_USER_PREFIX = "coupon:user:";
    private static final String COUPON_USER_ID_GENERATOR_KEY = "coupon:user:id:generator";
    private static final String USER_COUPONS_PREFIX = "user:coupons:";
    private static final String COUPON_ISSUED_PREFIX = "coupon:issued:";

    @Override
    public CouponUser save(CouponUser couponUser) {
        if (couponUser.getId() == null) {
            Long newId = couponRedisTemplate.opsForValue().increment(COUPON_USER_ID_GENERATOR_KEY);
            couponUser = couponUser.toBuilder().id(newId).build();
            log.debug("쿠폰 발급 생성: id={}, userId={}, couponId={}", newId, couponUser.getUserId(), couponUser.getCouponId());
        } else {
            log.debug("쿠폰 발급 수정: id={}", couponUser.getId());
        }

        saveCouponUserToRedis(couponUser);
        return couponUser;
    }

    @Override
    public Optional<CouponUser> findById(Long id) {
        String key = getCouponUserKey(id);
        CouponUserCacheDto dto = (CouponUserCacheDto) couponRedisTemplate.opsForValue().get(key);

        if (dto == null) {
            log.debug("쿠폰 발급 이력을 찾을 수 없음: id={}", id);
            return Optional.empty();
        }

        return Optional.of(dto.toDomain());
    }

    @Override
    public Optional<CouponUser> findByUserIdAndCouponId(Long userId, Long couponId) {
        // coupon:issued:{couponId} Set에서 중복 발급 체크
        String issuedKey = getCouponIssuedKey(couponId);
        Boolean isMember = couponRedisTemplate.opsForSet().isMember(issuedKey, String.valueOf(userId));

        if (Boolean.FALSE.equals(isMember)) {
            return Optional.empty();
        }

        // 실제 데이터는 user:coupons:{userId} Set에서 조회
        String userCouponsKey = getUserCouponsKey(userId);
        Set<Object> couponUserIds = couponRedisTemplate.opsForSet().members(userCouponsKey);

        if (couponUserIds == null || couponUserIds.isEmpty()) {
            return Optional.empty();
        }

        // 각 CouponUser를 조회해서 couponId가 일치하는 것 찾기
        return couponUserIds.stream()
                .map(id -> findById(Long.parseLong(String.valueOf(id))))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(cu -> cu.getCouponId().equals(couponId))
                .findFirst();
    }

    @Override
    public List<CouponUser> findByUserId(Long userId) {
        String userCouponsKey = getUserCouponsKey(userId);
        Set<Object> couponUserIds = couponRedisTemplate.opsForSet().members(userCouponsKey);

        if (couponUserIds == null || couponUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        return couponUserIds.stream()
                .map(id -> findById(Long.parseLong(String.valueOf(id))))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponUser> findByUserIdAndIsUsed(Long userId, Boolean isUsed) {
        return findByUserId(userId).stream()
                .filter(cu -> cu.isUsed() == isUsed)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponUser> findByCouponId(Long couponId) {
        // coupon:issued:{couponId} Set에서 이 쿠폰을 받은 모든 사용자 ID 조회
        String issuedKey = getCouponIssuedKey(couponId);
        Set<Object> userIds = couponRedisTemplate.opsForSet().members(issuedKey);

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 각 사용자에 대해 해당 쿠폰 조회
        return userIds.stream()
                .map(userIdObj -> Long.parseLong(String.valueOf(userIdObj)))
                .map(userId -> findByUserIdAndCouponId(userId, couponId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        // 모든 CouponUser 데이터 삭제
        Set<String> couponUserKeys = couponRedisTemplate.keys(COUPON_USER_PREFIX + "*");
        if (!couponUserKeys.isEmpty()) {
            couponRedisTemplate.delete(couponUserKeys);
        }

        // 모든 사용자의 쿠폰 목록 삭제
        Set<String> userCouponsKeys = couponRedisTemplate.keys(USER_COUPONS_PREFIX + "*");
        if (!userCouponsKeys.isEmpty()) {
            couponRedisTemplate.delete(userCouponsKeys);
        }

        // 모든 쿠폰의 발급 목록 삭제
        Set<String> couponIssuedKeys = couponRedisTemplate.keys(COUPON_ISSUED_PREFIX + "*");
        if (!couponIssuedKeys.isEmpty()) {
            couponRedisTemplate.delete(couponIssuedKeys);
        }

        // ID 생성기 삭제
        couponRedisTemplate.delete(COUPON_USER_ID_GENERATOR_KEY);

        log.debug("모든 쿠폰 발급 데이터 삭제 완료");
    }

    private void saveCouponUserToRedis(CouponUser couponUser) {
        String key = getCouponUserKey(couponUser.getId());

        // 도메인 -> DTO 변환 후 JSON 직렬화하여 저장
        CouponUserCacheDto dto = CouponUserCacheDto.from(couponUser);
        couponRedisTemplate.opsForValue().set(key, dto);

        // 사용자별 보유 쿠폰 목록에 추가
        String userCouponsKey = getUserCouponsKey(couponUser.getUserId());
        couponRedisTemplate.opsForSet().add(userCouponsKey, String.valueOf(couponUser.getId()));

        // 쿠폰별 발급된 사용자 목록에 추가 (중복 발급 체크용)
        String issuedKey = getCouponIssuedKey(couponUser.getCouponId());
        couponRedisTemplate.opsForSet().add(issuedKey, String.valueOf(couponUser.getUserId()));

        log.debug("Redis에 쿠폰 발급 저장 완료: key={}, userId={}, couponId={}",
                key, couponUser.getUserId(), couponUser.getCouponId());
    }

    private String getCouponUserKey(Long id) {
        return COUPON_USER_PREFIX + id;
    }

    private String getUserCouponsKey(Long userId) {
        return USER_COUPONS_PREFIX + userId;
    }

    private String getCouponIssuedKey(Long couponId) {
        return COUPON_ISSUED_PREFIX + couponId;
    }
}
