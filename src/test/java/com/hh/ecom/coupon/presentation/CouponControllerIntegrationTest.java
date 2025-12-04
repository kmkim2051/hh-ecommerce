package com.hh.ecom.coupon.presentation;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.CouponCommandService;
import com.hh.ecom.coupon.application.CouponQueryService;
import com.hh.ecom.coupon.application.RedisCouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.presentation.dto.response.CouponIssueResponse;
import com.hh.ecom.coupon.presentation.dto.response.CouponListResponse;
import com.hh.ecom.coupon.presentation.dto.response.MyCouponListResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("CouponController 통합 테스트 (Controller + Service + Repository)")
class CouponControllerIntegrationTest extends TestContainersConfig {

    @Autowired
    private CouponQueryService couponQueryService;

    @Autowired
    private CouponCommandService couponCommandService;

    @Autowired
    private RedisCouponService redisCouponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @Autowired
    @Qualifier("customStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    private CouponController couponController;
    private Coupon testCoupon;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        couponController = new CouponController(couponQueryService, redisCouponService);

        couponUserRepository.deleteAll();
        couponRepository.deleteAll();

        testUserId = 1L;
        testCoupon = Coupon.create(
                "테스트 쿠폰",
                BigDecimal.valueOf(5000),
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        testCoupon = couponRepository.save(testCoupon);

        redisCouponService.initializeCouponStock(testCoupon.getId(), testCoupon.getAvailableQuantity());
    }

    @AfterEach
    void tearDown() {
        // Clean up Redis keys
        if (testCoupon != null && testCoupon.getId() != null) {
            redisTemplate.delete("coupon:issue:async:stock:" + testCoupon.getId());
            redisTemplate.delete("coupon:issue:async:participants:" + testCoupon.getId());
            redisTemplate.delete("coupon:issue:async:queue:" + testCoupon.getId());
        }
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 목록을 조회한다")
    void getAvailableCoupons() {
        // when
        ResponseEntity<CouponListResponse> response = couponController.getAvailableCoupons();

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().coupons()).hasSize(1);
        assertThat(response.getBody().coupons().get(0).id()).isEqualTo(testCoupon.getId());
        assertThat(response.getBody().coupons().get(0).name()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().coupons().get(0).discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("쿠폰을 성공적으로 발급받는다 (비동기 큐 등록)")
    void issueCoupon_Success() {
        // when
        ResponseEntity<CouponIssueResponse> response = couponController.issueCoupon(testUserId, testCoupon.getId());

        // then - 비동기 플로우이므로 즉시 QUEUED 응답
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("QUEUED");
        assertThat(response.getBody().message()).isEqualTo("쿠폰 발급 요청이 접수되었습니다. 곧 처리됩니다.");
        assertThat(response.getBody().userId()).isEqualTo(testUserId);
        assertThat(response.getBody().couponId()).isEqualTo(testCoupon.getId());

        // Verify the request was added to the queue
        Long queueSize = redisCouponService.getQueueSize(testCoupon.getId());
        assertThat(queueSize).isGreaterThan(0L);
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰을 다시 발급받으려고 하면 실패한다 (Redis 중복 체크)")
    void issueCoupon_AlreadyIssued() {
        // given - 첫 번째 요청
        couponController.issueCoupon(testUserId, testCoupon.getId());

        // when & then - 중복 요청 시도
        assertThatThrownBy(() -> couponController.issueCoupon(testUserId, testCoupon.getId()))
                .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급받을 수 없다 (Redis 재고 체크)")
    void issueCoupon_OutOfStock() {
        // given - 수량이 1개인 쿠폰 생성
        Coupon soldOutCoupon = Coupon.create(
                "품절 쿠폰",
                BigDecimal.valueOf(3000),
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        final Coupon finalSoldOutCoupon = couponRepository.save(soldOutCoupon);

        // Initialize Redis stock
        redisCouponService.initializeCouponStock(finalSoldOutCoupon.getId(), finalSoldOutCoupon.getAvailableQuantity());

        // 첫 번째 사용자가 발급받음 (큐에 등록)
        couponController.issueCoupon(999L, finalSoldOutCoupon.getId());

        // when & then - 두 번째 사용자가 발급 시도 (재고 소진으로 실패)
        assertThatThrownBy(() -> couponController.issueCoupon(testUserId, finalSoldOutCoupon.getId()))
                .isInstanceOf(CouponException.class);

        // cleanup Redis keys
        redisTemplate.delete("coupon:issue:async:stock:" + finalSoldOutCoupon.getId());
        redisTemplate.delete("coupon:issue:async:participants:" + finalSoldOutCoupon.getId());
        redisTemplate.delete("coupon:issue:async:queue:" + finalSoldOutCoupon.getId());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰을 발급받으려고 하면 실패한다")
    void issueCoupon_CouponNotFound() {
        // when & then
        assertThatThrownBy(() -> couponController.issueCoupon(testUserId, 999999L))
                .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("사용자가 보유한 미사용 쿠폰 목록을 조회한다")
    void getMyCoupons() {
        // given - 쿠폰 발급
        couponCommandService.issueCoupon(testUserId, testCoupon.getId());

        // when
        ResponseEntity<MyCouponListResponse> response = couponController.getMyCoupons(testUserId);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().coupons()).hasSize(1);
        assertThat(response.getBody().coupons().get(0).couponName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().coupons().get(0).isUsed()).isFalse();
    }

    @Test
    @DisplayName("사용된 쿠폰은 보유 쿠폰 목록에서 조회되지 않는다")
    void getMyCoupons_UsedCouponNotIncluded() {
        // given - 쿠폰 발급 후 사용
        CouponUser issuedCouponUser = couponCommandService.issueCoupon(testUserId, testCoupon.getId());
        CouponUser usedCouponUser = issuedCouponUser.use(1L);
        couponUserRepository.save(usedCouponUser);

        // when
        ResponseEntity<MyCouponListResponse> response = couponController.getMyCoupons(testUserId);

        // then - 미사용 쿠폰만 조회
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().coupons()).isEmpty();
    }
}
