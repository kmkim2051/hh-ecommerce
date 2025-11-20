package com.hh.ecom.coupon.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.domain.*;
import com.hh.ecom.coupon.domain.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("CouponService 동시성 테스트")
class CouponServiceConcurrencyTest extends TestContainersConfig {

    @Autowired
    private CouponCommandService couponCommandService;

    @Autowired
    private CouponQueryService couponQueryService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @BeforeEach
    void setUp() {
        System.out.println("=== 테스트 시작 전 데이터 정리 ===");
        System.out.println("삭제 전 coupon 개수: " + couponRepository.findAll().size());
        System.out.println("삭제 전 couponUser 개수: " + couponUserRepository.findByUserId(1L).size());
        couponUserRepository.deleteAll();
        couponRepository.deleteAll();
        System.out.println("삭제 후 coupon 개수: " + couponRepository.findAll().size());
        System.out.println("삭제 후 couponUser 개수: " + couponUserRepository.findByUserId(1L).size());
    }

    @Test
    @DisplayName("동시에 여러 사용자가 쿠폰 발급 시도 - 수량 제한 검증")
    void concurrentCouponIssuance_QuantityLimit() throws InterruptedException {
        // given
        int totalQuantity = 10;
        int concurrentUsers = 50;

        Coupon coupon = Coupon.create(
                "선착순 쿠폰",
                BigDecimal.valueOf(5000),
                totalQuantity,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> successUserIds = new CopyOnWriteArrayList<>();

        // when - 50명의 사용자가 동시에 10개 쿠폰 발급 시도
        IntStream.range(0, concurrentUsers).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long userId = (long) (i + 1);
                    couponCommandService.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                    successUserIds.add(userId);
                } catch (CouponException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 정확히 10명만 발급 성공
        System.out.println("=== 쿠폰 발급 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());
        System.out.println("총 시도: " + concurrentUsers);

        assertThat(successCount.get()).isEqualTo(totalQuantity);
        assertThat(failCount.get()).isEqualTo(concurrentUsers - totalQuantity);
        assertThat(successUserIds).hasSize(totalQuantity);

        // 쿠폰 수량 확인
        Coupon updatedCoupon = couponQueryService.getCoupon(couponId);
        assertThat(updatedCoupon.getAvailableQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 사용자의 동시 중복 발급 시도 - 1개만 발급되어야 함")
    void concurrentCouponIssuance_SameUser() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
                "중복 방지 테스트 쿠폰",
                BigDecimal.valueOf(3000),
                100,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();
        final Long userId = 1L;

        int concurrentAttempts = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentAttempts);
        CountDownLatch latch = new CountDownLatch(concurrentAttempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 같은 사용자가 동시에 10번 발급 시도
        IntStream.range(0, concurrentAttempts).forEach(i -> {
            executorService.submit(() -> {
                try {
                    couponCommandService.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                } catch (CouponException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 정확히 1개만 발급 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(concurrentAttempts - 1);

        // 해당 사용자의 쿠폰 발급 내역 확인
        List<CouponUserWithCoupon> userCoupons = couponQueryService.getMyCoupons(userId);
        assertThat(userCoupons).hasSize(1);
    }
    @Test
    @DisplayName("수량 1개 쿠폰에 대한 대량 동시 발급 시도")
    void concurrentIssuance_SingleCoupon() throws InterruptedException {
        // given - 수량 1개 쿠폰
        Coupon coupon = Coupon.create(
                "초특가 쿠폰",
                BigDecimal.valueOf(10000),
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        int concurrentUsers = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> successUserIds = new ConcurrentLinkedQueue<>();

        // when - 100명이 동시에 1개 쿠폰 발급 시도
        IntStream.range(0, concurrentUsers).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long userId = (long) (i + 1);
                    couponCommandService.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                    successUserIds.add(userId);
                } catch (CouponException e) {
                    // 발급 실패
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 정확히 1명만 발급 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(successUserIds).hasSize(1);

        // 쿠폰 수량 확인
        Coupon finalCoupon = couponQueryService.getCoupon(couponId);
        assertThat(finalCoupon.getAvailableQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("순차적 발급 후 동시 조회 - 데이터 일관성 검증")
    void sequentialIssuanceThenConcurrentQuery() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
                "일관성 테스트 쿠폰",
                BigDecimal.valueOf(1500),
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        // 순차적으로 10개 발급
        for (long i = 1; i <= 10; i++) {
            couponCommandService.issueCoupon(i, couponId);
        }

        int queryThreads = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(queryThreads);
        CountDownLatch latch = new CountDownLatch(queryThreads);

        List<Integer> availableQuantities = new CopyOnWriteArrayList<>();

        // when - 50개 스레드가 동시에 조회
        IntStream.range(0, queryThreads).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Coupon queriedCoupon = couponQueryService.getCoupon(couponId);
                    availableQuantities.add(queriedCoupon.getAvailableQuantity());
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 모든 조회 결과가 동일해야 함 (수량 0)
        assertThat(availableQuantities).hasSize(queryThreads);
        assertThat(availableQuantities).allMatch(quantity -> quantity == 0);
    }

    @Test
    @DisplayName("동일 쿠폰에 대한 동시 사용 시도 - 이중 사용 방지")
    void concurrentCouponUsage_SameCoupon() throws InterruptedException {
        // given - 1명이 쿠폰 발급
        Coupon coupon = Coupon.create(
                "이중 사용 방지 테스트 쿠폰",
                BigDecimal.valueOf(5000),
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();
        final Long userId = 1L;

        CouponUser issuedCoupon = couponCommandService.issueCoupon(userId, couponId);
        final Long couponUserId = issuedCoupon.getId();

        int concurrentAttempts = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentAttempts);
        CountDownLatch latch = new CountDownLatch(concurrentAttempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> successOrderIds = new CopyOnWriteArrayList<>();

        // when - 같은 쿠폰을 10개의 서로 다른 주문에서 동시 사용 시도
        IntStream.range(0, concurrentAttempts).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long orderId = (long) (i + 1);
                    couponCommandService.useCoupon(couponUserId, orderId);
                    successCount.incrementAndGet();
                    successOrderIds.add(orderId);
                } catch (CouponException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 정확히 1개 주문만 성공해야 함
        System.out.println("=== 쿠폰 이중 사용 방지 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());
        System.out.println("성공한 주문 ID: " + successOrderIds);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(concurrentAttempts - 1);
        assertThat(successOrderIds).hasSize(1);

        // 쿠폰이 사용됨 상태인지 확인
        CouponUser finalCouponUser = couponUserRepository.findById(couponUserId)
                .orElseThrow();
        assertThat(finalCouponUser.isUsed()).isTrue();
        assertThat(finalCouponUser.getOrderId()).isIn(successOrderIds);
    }

    @Test
    @DisplayName("여러 사용자의 각자 쿠폰 동시 사용 - 낙관적 락 재시도 검증")
    void concurrentCouponUsage_MultipleUsers() throws InterruptedException {
        // given - 10명의 사용자가 각각 쿠폰 발급
        Coupon coupon = Coupon.create(
                "다중 사용자 테스트 쿠폰",
                BigDecimal.valueOf(3000),
                50,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        int userCount = 10;
        List<Long> couponUserIds = new ArrayList<>();

        // 10명이 각각 쿠폰 발급
        for (long i = 1; i <= userCount; i++) {
            CouponUser issued = couponCommandService.issueCoupon(i, couponId);
            couponUserIds.add(issued.getId());
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 10명이 동시에 각자의 쿠폰 사용
        IntStream.range(0, userCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long couponUserId = couponUserIds.get(i);
                    Long orderId = (long) (i + 100);
                    couponCommandService.useCoupon(couponUserId, orderId);
                    successCount.incrementAndGet();
                } catch (CouponException e) {
                    failCount.incrementAndGet();
                    System.err.println("쿠폰 사용 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 모두 성공해야 함 (각자의 쿠폰이므로 충돌 없음)
        System.out.println("=== 다중 사용자 쿠폰 사용 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(userCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 모든 쿠폰이 사용됨 상태인지 확인
        couponUserIds.forEach(couponUserId -> {
            CouponUser finalCouponUser = couponUserRepository.findById(couponUserId)
                    .orElseThrow();
            assertThat(finalCouponUser.isUsed()).isTrue();
            assertThat(finalCouponUser.getOrderId()).isNotNull();
        });
    }

    @Test
    @DisplayName("쿠폰 발급과 사용이 동시에 발생 - race condition 방지")
    void concurrentIssuanceAndUsage_Mixed() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
                "발급-사용 혼합 테스트 쿠폰",
                BigDecimal.valueOf(2000),
                20,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        // 먼저 5명에게 쿠폰 발급 (사용할 대상)
        List<Long> preIssuedCouponUserIds = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            CouponUser issued = couponCommandService.issueCoupon(i, couponId);
            preIssuedCouponUserIds.add(issued.getId());
        }

        int issuanceThreads = 10;  // 추가로 10명이 발급 시도
        int usageThreads = 5;      // 5명이 사용 시도

        ExecutorService executorService = Executors.newFixedThreadPool(issuanceThreads + usageThreads);
        CountDownLatch latch = new CountDownLatch(issuanceThreads + usageThreads);

        AtomicInteger issuanceSuccessCount = new AtomicInteger(0);
        AtomicInteger usageSuccessCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 발급과 사용이 동시에 실행
        // 발급 스레드
        IntStream.range(0, issuanceThreads).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long userId = (long) (i + 6); // 6번부터 시작
                    couponCommandService.issueCoupon(userId, couponId);
                    issuanceSuccessCount.incrementAndGet();
                } catch (CouponException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        // 사용 스레드
        IntStream.range(0, usageThreads).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long couponUserId = preIssuedCouponUserIds.get(i);
                    Long orderId = (long) (i + 200);
                    couponCommandService.useCoupon(couponUserId, orderId);
                    usageSuccessCount.incrementAndGet();
                } catch (CouponException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(15, TimeUnit.SECONDS);

        // then
        System.out.println("=== 발급-사용 혼합 테스트 결과 ===");
        System.out.println("발급 성공: " + issuanceSuccessCount.get());
        System.out.println("사용 성공: " + usageSuccessCount.get());
        System.out.println("실패: " + failCount.get());

        // 발급: 초기 5개 + 추가 10개 시도 = 총 15개 발급됨, 남은 수량 5개이므로 5개만 추가 발급 성공
        assertThat(issuanceSuccessCount.get()).isLessThanOrEqualTo(10);

        // 사용: 5개 모두 성공해야 함
        assertThat(usageSuccessCount.get()).isEqualTo(5);

        // 쿠폰 수량 확인 (총 20개 중 최소 10개는 발급되었음)
        Coupon finalCoupon = couponQueryService.getCoupon(couponId);
        int totalIssued = 5 + issuanceSuccessCount.get();
        assertThat(finalCoupon.getAvailableQuantity()).isEqualTo(20 - totalIssued);
    }

    @Test
    @DisplayName("대량 동시 쿠폰 사용 - 낙관적 락 재시도 메커니즘 검증")
    void concurrentCouponUsage_HighConcurrency() throws InterruptedException {
        // given - 50명의 사용자가 각각 쿠폰 발급
        Coupon coupon = Coupon.create(
                "대량 동시 사용 테스트 쿠폰",
                BigDecimal.valueOf(1000),
                100,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
        final Long couponId = coupon.getId();

        int userCount = 50;
        List<Long> couponUserIds = new CopyOnWriteArrayList<>();

        // 50명이 각각 쿠폰 발급
        for (long i = 1; i <= userCount; i++) {
            CouponUser issued = couponCommandService.issueCoupon(i, couponId);
            couponUserIds.add(issued.getId());
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50명이 동시에 각자의 쿠폰 사용 (높은 동시성)
        IntStream.range(0, userCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long couponUserId = couponUserIds.get(i);
                    Long orderId = (long) (i + 1000);
                    couponCommandService.useCoupon(couponUserId, orderId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("쿠폰 사용 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then - 모두 성공해야 함 (낙관적 락 재시도 메커니즘이 정상 작동)
        System.out.println("=== 대량 동시 사용 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(userCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 모든 쿠폰이 사용됨 상태인지 확인
        long usedCount = couponUserIds.stream()
                .map(id -> couponUserRepository.findById(id).orElseThrow())
                .filter(CouponUser::isUsed)
                .count();
        assertThat(usedCount).isEqualTo(userCount);
    }
}
