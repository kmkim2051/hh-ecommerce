package com.hh.ecom.coupon.presentation;

import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.infrastructure.persistence.CouponInMemoryRepository;
import com.hh.ecom.coupon.infrastructure.persistence.CouponUserInMemoryRepository;
import com.hh.ecom.coupon.presentation.dto.response.CouponIssueResponse;
import com.hh.ecom.coupon.presentation.dto.response.CouponListResponse;
import com.hh.ecom.coupon.presentation.dto.response.MyCouponListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CouponController 통합 테스트 (Controller + Service + Repository)")
class CouponControllerIntegrationTest {

    private CouponController couponController;
    private CouponService couponService;
    private CouponInMemoryRepository couponRepository;
    private CouponUserInMemoryRepository couponUserRepository;

    private Coupon testCoupon;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        couponRepository = new CouponInMemoryRepository();
        couponUserRepository = new CouponUserInMemoryRepository();
        couponService = new CouponService(couponRepository, couponUserRepository);
        couponController = new CouponController(couponService);

        couponRepository.deleteAll();
        couponUserRepository.deleteAll();

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
        assertThat(response.getBody().getCoupons()).hasSize(1);
        assertThat(response.getBody().getCoupons().get(0).getId()).isEqualTo(testCoupon.getId());
        assertThat(response.getBody().getCoupons().get(0).getName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().getCoupons().get(0).getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("쿠폰을 성공적으로 발급받는다")
    void issueCoupon_Success() {
        // when
        ResponseEntity<CouponIssueResponse> response = couponController.issueCoupon(testUserId, testCoupon.getId());

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("쿠폰이 발급되었습니다.");
        assertThat(response.getBody().getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰을 다시 발급받으려고 하면 실패한다")
    void issueCoupon_AlreadyIssued() {
        // given
        couponService.issueCoupon(testUserId, testCoupon.getId());

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
        couponService.issueCoupon(999L, finalSoldOutCoupon.getId());

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
        couponService.issueCoupon(testUserId, testCoupon.getId());

        // when
        ResponseEntity<MyCouponListResponse> response = couponController.getMyCoupons(testUserId);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCoupons()).hasSize(1);
        assertThat(response.getBody().getCoupons().get(0).getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().getCoupons().get(0).getIsUsed()).isFalse();
    }

    @Test
    @DisplayName("사용된 쿠폰은 보유 쿠폰 목록에서 조회되지 않는다")
    void getMyCoupons_UsedCouponNotIncluded() {
        // given - 쿠폰 발급 후 사용
        CouponUser issuedCouponUser = couponService.issueCoupon(testUserId, testCoupon.getId());
        CouponUser usedCouponUser = issuedCouponUser.use(1L);
        couponUserRepository.save(usedCouponUser);

        // when
        ResponseEntity<MyCouponListResponse> response = couponController.getMyCoupons(testUserId);

        // then - 미사용 쿠폰만 조회
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCoupons()).isEmpty();
    }
}
