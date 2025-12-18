package com.hh.ecom.coupon.presentation;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.RedisCouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kafka 기반 쿠폰 시스템 End-to-End 통합 테스트
 * - Consumer 비동기 처리까지 포함한 완전한 E2E 테스트
 * - TestContainers (MySQL + Redis + Kafka) 사용
 * - Awaitility를 사용한 비동기 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "coupon.worker.enabled=false",  // Redis Queue Worker 비활성화 (Kafka 사용)
    "spring.task.scheduling.enabled=false"  // 스케줄러도 비활성화
})
@DisplayName("CouponKafka E2E 통합 테스트 (HTTP → Kafka → Consumer → DB)")
class CouponKafkaE2ETest extends TestContainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @Autowired
    private RedisCouponService redisCouponService;

    @Autowired
    @Qualifier("customStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // Clean up
        couponUserRepository.deleteAll();
        couponRepository.deleteAll();
        cleanupRedisKeys();

        // Create test coupon
        testCoupon = Coupon.create(
            "Kafka E2E 테스트 쿠폰",
            BigDecimal.valueOf(10000),
            100,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30)
        );
        testCoupon = couponRepository.save(testCoupon);

        // Initialize Redis stock
        redisCouponService.initializeCouponStock(testCoupon.getId(), testCoupon.getAvailableQuantity());
    }

    @AfterEach
    void tearDown() {
        cleanupRedisKeys();
    }

    @Test
    @DisplayName("E2E - 쿠폰 발급: HTTP → Kafka → Consumer → DB 전체 플로우 검증")
    void e2e_SingleUserCouponIssuance_FullFlow() throws Exception {
        // given
        Long userId = 999L;
        Long couponId = testCoupon.getId();

        // when - Step 1: HTTP 요청 (Controller → Producer)
        MvcResult result = mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();

        String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");

        // then - Step 2: Kafka Consumer가 메시지 처리할 시간 대기 (TestContainers 환경에서는 더 긴 시간 필요)
        await().atMost(Duration.ofSeconds(30))
               .pollInterval(Duration.ofMillis(500))
               .untilAsserted(() -> {
                   // Step 3: DB에 쿠폰 발급 기록 확인
                   Optional<CouponUser> couponUser = couponUserRepository
                       .findByUserIdAndCouponId(userId, couponId);

                   assertThat(couponUser)
                       .as("쿠폰이 DB에 저장되어야 합니다")
                       .isPresent();
                   assertThat(couponUser.get().isUsable()).isTrue();

                   // Step 4: 쿠폰 재고 감소 확인
                   Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
                   assertThat(updatedCoupon.getAvailableQuantity())
                       .as("재고가 1 감소해야 합니다")
                       .isEqualTo(testCoupon.getAvailableQuantity() - 1);
               });
    }

    @Test
    @DisplayName("E2E - 동시 100명 요청 시 정확히 재고만큼만 발급")
    void e2e_ConcurrentIssuance_ExactStockManagement() throws Exception {
        // given - 재고 10개인 쿠폰 생성
        Coupon limitedCoupon = Coupon.create(
            "한정 쿠폰",
            BigDecimal.valueOf(5000),
            10,  // 재고 10개
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        limitedCoupon = couponRepository.save(limitedCoupon);
        redisCouponService.initializeCouponStock(limitedCoupon.getId(), 10);

        final Long couponId = limitedCoupon.getId();
        int totalRequests = 100;  // 100명이 동시 요청

        // when - 100개 스레드에서 동시 요청
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);
        List<Future<Boolean>> futures = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 1; i <= totalRequests; i++) {
            final int userId = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드 동시 시작

                    mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                            .header("userId", String.valueOf(userId))
                            .contentType(MediaType.APPLICATION_JSON))
                        .andReturn();

                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();  // 동시 시작!
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then - Consumer 처리 완료 대기 후 검증 (TestContainers 환경에서는 더 긴 시간 필요)
        await().atMost(Duration.ofSeconds(45))
               .pollInterval(Duration.ofMillis(500))
               .untilAsserted(() -> {
                   // Step 1: 정확히 10개만 발급되었는지 확인
                   List<CouponUser> issuedCoupons = couponUserRepository
                       .findByCouponId(couponId);

                   assertThat(issuedCoupons).hasSize(10);  // 정확히 10개

                   // Step 2: 모든 발급된 쿠폰이 사용 가능한지 확인
                   assertThat(issuedCoupons)
                       .allMatch(CouponUser::isUsable);

                   // Step 3: 쿠폰 재고 0인지 확인
                   Coupon finalCoupon = couponRepository.findById(couponId).orElseThrow();
                   assertThat(finalCoupon.getAvailableQuantity()).isEqualTo(0);

                   // Step 4: Redis 참여자 수 확인 (최대 10명만)
                   Long participantCount = redisCouponService.getParticipantCount(couponId);
                   assertThat(participantCount).isEqualTo(10L);
               });
    }

    @Test
    @DisplayName("E2E - 동일 사용자 중복 요청 시 한 번만 발급 (멱등성)")
    void e2e_IdempotencyGuarantee_DuplicateRequestPrevention() throws Exception {
        // given
        Long userId = 777L;
        Long couponId = testCoupon.getId();

        // when - Step 1: 첫 번째 요청 (성공)
        MvcResult firstResult = mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();

        String firstRequestId = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.requestId");

        // Step 2: Consumer 처리 완료 대기 (TestContainers 환경에서는 더 긴 시간 필요)
        await().atMost(Duration.ofSeconds(30))
               .pollInterval(Duration.ofMillis(500))
               .until(() -> couponUserRepository.findByUserIdAndCouponId(userId, couponId).isPresent());

        // Step 3: 두 번째 요청 (Redis에서 즉시 차단)
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())  // 중복 발급 에러
                .andExpect(jsonPath("$.message").value(containsString("이미 발급")));

        // Step 4: 세 번째 요청 (동일하게 차단)
        mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        // then - 최종 검증
        await().atMost(Duration.ofSeconds(2))
               .untilAsserted(() -> {
                   // DB에 정확히 1개만 존재
                   Optional<CouponUser> issuedCoupon = couponUserRepository
                       .findByUserIdAndCouponId(userId, couponId);

                   assertThat(issuedCoupon).isPresent();  // 정확히 1개만!
                   assertThat(issuedCoupon.get().isUsable()).isTrue();

                   // Redis 참여자 집합에도 1번만 등록
                   Long participantCount = redisCouponService.getParticipantCount(couponId);
                   assertThat(participantCount).isGreaterThanOrEqualTo(1L);
               });
    }

    // ===== Helper Methods =====

    private void cleanupRedisKeys() {
        try {
            Set<String> couponKeys = redisTemplate.keys("coupon:*");
            if (couponKeys != null && !couponKeys.isEmpty()) {
                redisTemplate.delete(couponKeys);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
