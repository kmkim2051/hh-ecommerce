package com.hh.ecom.coupon.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest
@TestPropertySource(properties = {
    "coupon.worker.batch-size=10",
    "coupon.worker.retry-on-failure=false"
})
class CouponIssueWorkerTest extends TestContainersConfig {

    @Autowired
    private RedisCouponService redisCouponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @Autowired
    @Qualifier("customStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        couponUserRepository.deleteAll();

        testCoupon = Coupon.create(
            "워커 테스트 쿠폰",
            BigDecimal.valueOf(5000),
            50,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30)
        );
        testCoupon = couponRepository.save(testCoupon);

        // Initialize Redis stock
        redisCouponService.initializeCouponStock(testCoupon.getId(), testCoupon.getAvailableQuantity());
    }

    @AfterEach
    void tearDown() {
        if (testCoupon != null && testCoupon.getId() != null) {
            redisTemplate.delete("coupon:issue:async:stock:" + testCoupon.getId());
            redisTemplate.delete("coupon:issue:async:participants:" + testCoupon.getId());
            redisTemplate.delete("coupon:issue:async:queue:" + testCoupon.getId());
        }
    }

    @Test
    @DisplayName("워커가 큐에서 요청을 꺼내 DB에 저장한다")
    void worker_ProcessesQueueAndSavesToDB() {
        // given
        Long userId = 100L;
        redisCouponService.enqueueUserIfEligible(userId, testCoupon.getId());

        // when - wait for worker to process (worker runs every 100ms)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            // then
            Optional<CouponUser> couponUser = couponUserRepository.findByUserIdAndCouponId(userId, testCoupon.getId());
            assertThat(couponUser).isPresent();
            assertThat(couponUser.get().getUserId()).isEqualTo(userId);
            assertThat(couponUser.get().getCouponId()).isEqualTo(testCoupon.getId());
            assertThat(couponUser.get().isUsed()).isFalse();
        });

        // Queue should be empty after processing
        Long queueSize = redisCouponService.getQueueSize(testCoupon.getId());
        assertThat(queueSize).isEqualTo(0L);
    }

    @Test
    @DisplayName("워커가 여러 요청을 순차적으로 처리한다")
    void worker_ProcessesMultipleRequests() {
        // given
        int requestCount = 5;
        for (int i = 1; i <= requestCount; i++) {
            redisCouponService.enqueueUserIfEligible((long) i, testCoupon.getId());
        }

        // when - wait for worker to process all
        await().atMost(5, SECONDS).untilAsserted(() -> {
            // then
            long issuedCount = 0;
            for (int i = 1; i <= requestCount; i++) {
                if (couponUserRepository.findByUserIdAndCouponId((long) i, testCoupon.getId()).isPresent()) {
                    issuedCount++;
                }
            }
            assertThat(issuedCount).isEqualTo(requestCount);
        });

        // Queue should be empty
        Long queueSize = redisCouponService.getQueueSize(testCoupon.getId());
        assertThat(queueSize).isEqualTo(0L);
    }

    @Test
    @DisplayName("워커는 중복 요청을 건너뛴다")
    void worker_SkipsDuplicateRequests() {
        // given
        Long userId = 200L;

        // Manually insert a coupon_user record (simulate already issued)
        CouponUser existing = CouponUser.issue(userId, testCoupon.getId(), testCoupon.getEndDate());
        couponUserRepository.save(existing);

        // Enqueue the same user (shouldn't happen in normal flow, but testing idempotency)
        String queueKey = "coupon:issue:async:queue:" + testCoupon.getId();
        redisTemplate.opsForList().rightPush(queueKey, userId + ":" + testCoupon.getId());

        // when - wait for worker
        await().pollDelay(1, SECONDS).atMost(3, SECONDS).untilAsserted(() -> {
            // then - worker should process and skip without error
            Long queueSize = redisCouponService.getQueueSize(testCoupon.getId());
            assertThat(queueSize).isEqualTo(0L);
        });

        // Should still have only one record
        long count = couponUserRepository.findByUserIdAndCouponId(userId, testCoupon.getId())
            .stream().count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("워커가 DB에 쿠폰을 저장하면 재고가 감소한다")
    void worker_DecreasesStockWhenIssuingCoupon() {
        // given
        Long userId = 300L;
        int initialStock = testCoupon.getAvailableQuantity();
        redisCouponService.enqueueUserIfEligible(userId, testCoupon.getId());

        // when - wait for worker
        await().atMost(5, SECONDS).untilAsserted(() -> {
            // then
            Optional<CouponUser> couponUser = couponUserRepository.findByUserIdAndCouponId(userId, testCoupon.getId());
            assertThat(couponUser).isPresent();
        });

        // Check DB stock decreased
        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getAvailableQuantity()).isEqualTo(initialStock - 1);
    }
}
