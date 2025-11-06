package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.infrastructure.persistence.CouponInMemoryRepository;
import com.hh.ecom.coupon.infrastructure.persistence.CouponUserInMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("낙관적 락 기본 동작 테스트")
class CouponServiceOptimisticLockTest {

    private CouponService couponService;
    private CouponInMemoryRepository couponRepository;
    private CouponUserInMemoryRepository couponUserRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new CouponInMemoryRepository();
        couponUserRepository = new CouponUserInMemoryRepository();
        couponService = new CouponService(couponRepository, couponUserRepository);

        couponRepository.deleteAll();
        couponUserRepository.deleteAll();
    }

    @Test
    @DisplayName("낙관적 락 - 순차적 발급은 정상 작동")
    void optimisticLock_SequentialIssue() {
        // given
        Coupon coupon = Coupon.create(
                "테스트 쿠폰",
                BigDecimal.valueOf(5000),
                5,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        Long couponId = coupon.getId();

        // when - 순차적으로 5명에게 발급
        for (long i = 1; i <= 5; i++) {
            couponService.issueCoupon(i, couponId);
        }

        // then
        Coupon finalCoupon = couponService.getCoupon(couponId);
        assertThat(finalCoupon.getAvailableQuantity()).isEqualTo(0);
        assertThat(finalCoupon.getVersion()).isEqualTo(5L); // 버전이 증가했는지 확인
    }

    @Test
    @DisplayName("낙관적 락 - 2개 스레드 동시 발급 (간단한 케이스)")
    void optimisticLock_TwoThreads() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
                "2개 쿠폰",
                BigDecimal.valueOf(3000),
                2,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        // when - 2개 스레드가 동시에 발급 시도
        Thread thread1 = new Thread(() -> {
            try {
                couponService.issueCoupon(1L, couponId);
                System.out.println("Thread 1: 발급 성공");
            } catch (Exception e) {
                System.out.println("Thread 1: 발급 실패 - " + e.getMessage());
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                couponService.issueCoupon(2L, couponId);
                System.out.println("Thread 2: 발급 성공");
            } catch (Exception e) {
                System.out.println("Thread 2: 발급 실패 - " + e.getMessage());
            }
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // then - 2개 모두 발급되어야 함
        Coupon finalCoupon = couponService.getCoupon(couponId);
        assertThat(finalCoupon.getAvailableQuantity()).isEqualTo(0);

        // 발급 내역 확인
        assertThat(couponService.getMyCoupons(1L)).hasSize(1);
        assertThat(couponService.getMyCoupons(2L)).hasSize(1);
    }

    @Test
    @DisplayName("낙관적 락 - 10개 스레드 소규모 동시성 테스트")
    void optimisticLock_TenThreads() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
                "10개 쿠폰",
                BigDecimal.valueOf(1000),
                5,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        // when - 10개 스레드가 동시 발급 (5개만 성공해야 함)
        Thread[] threads = new Thread[10];
        int[] successCount = {0};
        int[] failCount = {0};

        for (int i = 0; i < 10; i++) {
            final long userId = i + 1;
            threads[i] = new Thread(() -> {
                try {
                    couponService.issueCoupon(userId, couponId);
                    synchronized (successCount) {
                        successCount[0]++;
                    }
                } catch (Exception e) {
                    synchronized (failCount) {
                        failCount[0]++;
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // then
        System.out.println("성공: " + successCount[0] + ", 실패: " + failCount[0]);

        Coupon finalCoupon = couponService.getCoupon(couponId);
        assertThat(finalCoupon.getAvailableQuantity()).isEqualTo(0);
        assertThat(successCount[0]).isEqualTo(5);
        assertThat(failCount[0]).isEqualTo(5);
    }
}
