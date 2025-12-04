package com.hh.ecom.coupon.infrastructure.redis;

import com.hh.ecom.coupon.application.RedisCouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initialize Redis with coupon stock data on application startup.
 * Loads all active coupons from DB and sets their stock in Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRedisInitializer implements ApplicationRunner {

    private final CouponRepository couponRepository;
    private final RedisCouponService redisCouponService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("쿠폰 Redis 초기화 시작");

        try {
            List<Coupon> allCoupons = couponRepository.findAll();
            int initializedCount = 0;

            for (Coupon coupon : allCoupons) {
                if (coupon.getId() == null) {
                    log.warn("쿠폰 ID가 null입니다. 건너뜁니다: {}", coupon);
                    continue;
                }

                // Initialize stock in Redis (idempotent operation)
                redisCouponService.initializeCouponStock(
                    coupon.getId(),
                    coupon.getAvailableQuantity()
                );
                initializedCount++;
            }

            log.info("쿠폰 Redis 초기화 완료: 총 {}개 쿠폰 처리 완료", initializedCount);

        } catch (Exception e) {
            log.error("쿠폰 Redis 초기화 중 오류 발생", e);
            // Don't throw exception - allow application to start
            // Redis initialization failure should not prevent application startup
        }
    }
}
