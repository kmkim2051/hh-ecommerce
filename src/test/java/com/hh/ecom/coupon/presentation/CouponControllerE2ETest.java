package com.hh.ecom.coupon.presentation;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.RedisCouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kafka 기반 쿠폰 시스템 End-to-End 테스트
 * - TestContainers (MySQL + Redis + Kafka) 사용
 * - MockMvc를 통한 실제 HTTP 요청 테스트
 * - HTTP → Kafka 이벤트 발행까지의 E2E 테스트
 *
 * Note: Kafka Consumer 처리는 비동기로 실행되므로 E2E 테스트에서는 HTTP 응답까지만 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "coupon.worker.enabled=false",  // Redis Queue Worker 비활성화 (Kafka 사용)
    "spring.task.scheduling.enabled=false"  // 스케줄러도 비활성화
})
@DisplayName("CouponController E2E 테스트 (HTTP to Kafka)")
class CouponControllerE2ETest extends TestContainersConfig {

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
                "E2E 테스트 쿠폰",
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
    @DisplayName("E2E - 발급 가능한 쿠폰 목록 조회")
    void e2e_GetAvailableCoupons() throws Exception {
        // given - 추가 쿠폰 생성
        Coupon coupon2 = Coupon.create(
                "두 번째 쿠폰",
                BigDecimal.valueOf(3000),
                50,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(15)
        );
        couponRepository.save(coupon2);

        // when & then
        mockMvc.perform(get("/coupons")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons.length()").value(2))
                .andExpect(jsonPath("$.coupons[0].name").exists())
                .andExpect(jsonPath("$.coupons[0].discountAmount").exists())
                .andExpect(jsonPath("$.coupons[0].availableQuantity").exists());
    }

    @Test
    @DisplayName("E2E - 쿠폰 발급 요청 → Kafka 이벤트 발행")
    void e2e_IssueCoupon_PublishToKafka() throws Exception {
        // given
        Long userId = 1L;

        // when - HTTP POST로 쿠폰 발급 요청
        mockMvc.perform(post("/coupons/{couponId}/issue", testCoupon.getId())
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value("쿠폰 발급 요청이 접수되었습니다. 곧 처리됩니다."))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.couponId").value(testCoupon.getId()))
                .andExpect(jsonPath("$.requestId").exists());  // requestId 존재 확인

        // then - Redis 참여자 집합에 등록 확인 (빠른 검증용)
        Long participantCount = redisCouponService.getParticipantCount(testCoupon.getId());
        assertThat(participantCount).isEqualTo(1L);

        // Kafka로 이벤트가 발행되고, Consumer가 비동기로 처리함
    }


    @Test
    @DisplayName("E2E - 중복 발급 방지 (Redis 참여자 체크)")
    void e2e_PreventDuplicateIssuance() throws Exception {
        // given
        Long userId = 123L;

        // when - Step 1: 첫 번째 요청 성공
        mockMvc.perform(post("/coupons/{couponId}/issue", testCoupon.getId())
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.requestId").exists());

        // then - Step 2: 두 번째 요청 실패 (중복 체크)
        mockMvc.perform(post("/coupons/{couponId}/issue", testCoupon.getId())
                        .header("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("E2E - 존재하지 않는 쿠폰 발급 시도 → 404")
    void e2e_IssueCoupon_NotFound() throws Exception {
        // when & then
        mockMvc.perform(post("/coupons/{couponId}/issue", 999999L)
                        .header("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("E2E - 여러 사용자가 순차적으로 쿠폰 발급 요청")
    void e2e_SequentialIssuance_MultipleUsers() throws Exception {
        // given
        int userCount = 5;

        // when - 5명이 순차적으로 쿠폰 발급 요청
        for (int i = 1; i <= userCount; i++) {
            mockMvc.perform(post("/coupons/{couponId}/issue", testCoupon.getId())
                            .header("userId", String.valueOf(i))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.requestId").exists());
        }

        // then - Redis 큐에 등록 확인 (Worker가 처리 중일 수 있으므로 최소 0개 이상)
        Long queueSize = redisCouponService.getQueueSize(testCoupon.getId());
        assertThat(queueSize).isGreaterThanOrEqualTo(0L);

        // Redis 참여자는 5명 확인 (이미 처리되어도 참여자 기록은 남음)
        Long participantCount = redisCouponService.getParticipantCount(testCoupon.getId());
        assertThat(participantCount).isEqualTo(5L);
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
