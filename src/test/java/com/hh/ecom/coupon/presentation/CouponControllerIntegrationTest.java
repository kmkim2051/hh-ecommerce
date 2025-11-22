package com.hh.ecom.coupon.presentation;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.CouponCommandService;
import com.hh.ecom.coupon.application.CouponQueryService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.presentation.dto.response.CouponIssueResponse;
import com.hh.ecom.coupon.presentation.dto.response.CouponListResponse;
import com.hh.ecom.coupon.presentation.dto.response.MyCouponListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    private CouponController couponController;
    private Coupon testCoupon;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        couponController = new CouponController(couponQueryService, couponCommandService);

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
    @DisplayName("쿠폰을 성공적으로 발급받는다")
    void issueCoupon_Success() {
        // when
        ResponseEntity<CouponIssueResponse> response = couponController.issueCoupon(testUserId, testCoupon.getId());

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("쿠폰이 발급되었습니다.");
        assertThat(response.getBody().couponName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰을 다시 발급받으려고 하면 실패한다")
    void issueCoupon_AlreadyIssued() {
        // given
        couponCommandService.issueCoupon(testUserId, testCoupon.getId());

        // when & then
        assertThatThrownBy(() -> couponController.issueCoupon(testUserId, testCoupon.getId()))
                .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급받을 수 없다")
    void issueCoupon_OutOfStock() {
        // given - 쿠폰 수량을 모두 소진
        Coupon soldOutCoupon = Coupon.create(
                "품절 쿠폰",
                BigDecimal.valueOf(3000),
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        final Coupon finalSoldOutCoupon = couponRepository.save(soldOutCoupon);

        // 첫 번째 사용자가 발급받음
        couponCommandService.issueCoupon(999L, finalSoldOutCoupon.getId());

        // when & then - 두 번째 사용자가 발급 시도
        assertThatThrownBy(() -> couponController.issueCoupon(testUserId, finalSoldOutCoupon.getId()))
                .isInstanceOf(CouponException.class);
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
