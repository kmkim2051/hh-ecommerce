package com.hh.ecom.coupon.infrastructure.persistence.redis;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.infrastructure.persistence.redis.dto.CouponCacheDto;
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
public class CouponRedisRepository implements CouponRepository {

    private final RedisTemplate<String, Object> couponRedisTemplate;

    public CouponRedisRepository(@Qualifier("couponRedisTemplate") RedisTemplate<String, Object> couponRedisTemplate) {
        this.couponRedisTemplate = couponRedisTemplate;
    }

    private static final String COUPON_PREFIX = "coupon:";
    private static final String ID_GENERATOR_KEY = "coupon:id:generator";
    private static final String ALL_COUPONS_KEY = "coupon:all";

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            Long newId = couponRedisTemplate.opsForValue().increment(ID_GENERATOR_KEY);
            coupon = coupon.toBuilder().id(newId).build();
            log.debug("쿠폰 생성: id={}, name={}", newId, coupon.getName());
        } else {
            log.debug("쿠폰 수정: id={}", coupon.getId());
        }

        saveCouponToRedis(coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        String key = getCouponKey(id);
        CouponCacheDto dto = (CouponCacheDto) couponRedisTemplate.opsForValue().get(key);

        if (dto == null) {
            log.debug("쿠폰을 찾을 수 없음: id={}", id);
            return Optional.empty();
        }

        return Optional.of(dto.toDomain());
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(Long id) {
        // Redis + 분산락 환경에서는 일반 조회와 동일
        return findById(id);
    }

    @Override
    public List<Coupon> findAll() {
        Set<Object> couponIds = couponRedisTemplate.opsForSet().members(ALL_COUPONS_KEY);

        if (couponIds == null || couponIds.isEmpty()) {
            return Collections.emptyList();
        }

        return couponIds.stream()
                .map(id -> findById(Long.parseLong(String.valueOf(id))))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<Coupon> findAllIssuable() {
        return findAll().stream()
                .filter(Coupon::isIssuable)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        Set<String> keys = couponRedisTemplate.keys(COUPON_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            couponRedisTemplate.delete(keys);
        }
        couponRedisTemplate.delete(ID_GENERATOR_KEY);
        couponRedisTemplate.delete(ALL_COUPONS_KEY);
        log.debug("모든 쿠폰 데이터 삭제 완료");
    }

    private void saveCouponToRedis(Coupon coupon) {
        String key = getCouponKey(coupon.getId());

        // 도메인 -> DTO 변환 후 JSON 직렬화하여 저장
        CouponCacheDto dto = CouponCacheDto.from(coupon);
        couponRedisTemplate.opsForValue().set(key, dto);

        // 전체 쿠폰 목록에 추가
        couponRedisTemplate.opsForSet().add(ALL_COUPONS_KEY, String.valueOf(coupon.getId()));

        log.debug("Redis에 쿠폰 저장 완료: key={}, availableQuantity={}", key, coupon.getAvailableQuantity());
    }

    private String getCouponKey(Long id) {
        return COUPON_PREFIX + id;
    }
}
