package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.infrastructure.persistence.CouponInMemoryRepository;
import com.hh.ecom.coupon.infrastructure.persistence.CouponUserInMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CouponService 통합 테스트 (Service + Repository)")
class CouponServiceIntegrationTest {

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
    @DisplayName("통합 테스트 - 쿠폰 발급 후 조회")
    void integration_IssueCouponAndGet() {
        // Given
        Coupon coupon = createAndSaveCoupon("신규회원 할인 쿠폰", BigDecimal.valueOf(5000), 100);
        Long userId = 1L;

        // When
        CouponUser issuedCoupon = couponService.issueCoupon(userId, coupon.getId());

        // Then
        assertThat(issuedCoupon).isNotNull();
        assertThat(issuedCoupon.getId()).isNotNull();
        assertThat(issuedCoupon.getUserId()).isEqualTo(userId);
        assertThat(issuedCoupon.getCouponId()).isEqualTo(coupon.getId());
        assertThat(issuedCoupon.getIsUsed()).isFalse();

        // 쿠폰 수량 감소 확인
        Coupon updatedCoupon = couponService.getCoupon(coupon.getId());
        assertThat(updatedCoupon.getAvailableQuantity()).isEqualTo(99);

        // 보유 쿠폰 조회
        List<CouponService.CouponUserWithCoupon> myCoupons = couponService.getMyCoupons(userId);
        assertThat(myCoupons).hasSize(1);
        assertThat(myCoupons.get(0).getCouponUser().getId()).isEqualTo(issuedCoupon.getId());
        assertThat(myCoupons.get(0).getCoupon().getName()).isEqualTo("신규회원 할인 쿠폰");
    }

    @Test
    @DisplayName("통합 테스트 - 중복 발급 방지")
    void integration_PreventDuplicateIssuance() {
        // Given
        Coupon coupon = createAndSaveCoupon("중복 테스트 쿠폰", BigDecimal.valueOf(3000), 50);
        Long userId = 1L;

        // When
        couponService.issueCoupon(userId, coupon.getId());

        // Then - 중복 발급 시도 시 예외 발생
        assertThatThrownBy(() -> couponService.issueCoupon(userId, coupon.getId()))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_ALREADY_ISSUED);

        // 쿠폰 수량이 한 번만 차감되었는지 확인
        Coupon updatedCoupon = couponService.getCoupon(coupon.getId());
        assertThat(updatedCoupon.getAvailableQuantity()).isEqualTo(49);
    }

    @Test
    @DisplayName("통합 테스트 - 쿠폰 소진 시 발급 불가")
    void integration_CannotIssueWhenSoldOut() {
        // Given
        Coupon coupon = createAndSaveCoupon("한정수량 쿠폰", BigDecimal.valueOf(10000), 2);

        // When - 2개 모두 발급
        couponService.issueCoupon(1L, coupon.getId());
        couponService.issueCoupon(2L, coupon.getId());

        // Then - 추가 발급 불가
        assertThatThrownBy(() -> couponService.issueCoupon(3L, coupon.getId()))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_SOLD_OUT);

        // 쿠폰 상태 확인
        Coupon soldOutCoupon = couponService.getCoupon(coupon.getId());
        assertThat(soldOutCoupon.getStatus()).isEqualTo(CouponStatus.SOLD_OUT);
        assertThat(soldOutCoupon.getAvailableQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("통합 테스트 - 여러 사용자의 쿠폰 발급 및 조회")
    void integration_MultipleUsersIssuance() {
        // Given
        Coupon coupon1 = createAndSaveCoupon("쿠폰A", BigDecimal.valueOf(5000), 100);
        Coupon coupon2 = createAndSaveCoupon("쿠폰B", BigDecimal.valueOf(10000), 100);

        Long user1 = 1L;
        Long user2 = 2L;

        // When
        couponService.issueCoupon(user1, coupon1.getId());
        couponService.issueCoupon(user1, coupon2.getId());
        couponService.issueCoupon(user2, coupon1.getId());

        // Then
        List<CouponService.CouponUserWithCoupon> user1Coupons = couponService.getMyCoupons(user1);
        List<CouponService.CouponUserWithCoupon> user2Coupons = couponService.getMyCoupons(user2);

        assertThat(user1Coupons).hasSize(2);
        assertThat(user2Coupons).hasSize(1);

        assertThat(user1Coupons)
                .extracting(c -> c.getCoupon().getName())
                .containsExactlyInAnyOrder("쿠폰A", "쿠폰B");

        assertThat(user2Coupons.get(0).getCoupon().getName()).isEqualTo("쿠폰A");
    }

    @Test
    @DisplayName("통합 테스트 - 쿠폰 사용 처리")
    void integration_UseCoupon() {
        // Given
        Coupon coupon = createAndSaveCoupon("사용 테스트 쿠폰", BigDecimal.valueOf(7000), 50);
        Long userId = 1L;
        Long orderId = 100L;

        CouponUser issuedCoupon = couponService.issueCoupon(userId, coupon.getId());

        // When
        CouponUser usedCoupon = couponService.useCoupon(issuedCoupon.getId(), orderId);

        // Then
        assertThat(usedCoupon.getIsUsed()).isTrue();
        assertThat(usedCoupon.getOrderId()).isEqualTo(orderId);
        assertThat(usedCoupon.getUsedAt()).isNotNull();

        // 미사용 쿠폰 조회 시 제외됨
        List<CouponService.CouponUserWithCoupon> unusedCoupons = couponService.getMyCoupons(userId);
        assertThat(unusedCoupons).isEmpty();

        // 전체 쿠폰 조회 시 포함됨
        List<CouponService.CouponUserWithCoupon> allCoupons = couponService.getAllMyCoupons(userId);
        assertThat(allCoupons).hasSize(1);
        assertThat(allCoupons.get(0).getCouponUser().getIsUsed()).isTrue();
    }

    @Test
    @DisplayName("통합 테스트 - 사용된 쿠폰 재사용 불가")
    void integration_CannotReuseUsedCoupon() {
        // Given
        Coupon coupon = createAndSaveCoupon("일회용 쿠폰", BigDecimal.valueOf(5000), 50);
        Long userId = 1L;
        Long orderId1 = 100L;
        Long orderId2 = 200L;

        CouponUser issuedCoupon = couponService.issueCoupon(userId, coupon.getId());
        couponService.useCoupon(issuedCoupon.getId(), orderId1);

        // When & Then - 이미 사용된 쿠폰 재사용 시도
        assertThatThrownBy(() -> couponService.useCoupon(issuedCoupon.getId(), orderId2))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    @DisplayName("통합 테스트 - 만료된 쿠폰 발급 불가")
    void integration_CannotIssueExpiredCoupon() {
        // Given - 어제 종료된 쿠폰
        Coupon expiredCoupon = Coupon.create(
                "만료 쿠폰",
                BigDecimal.valueOf(5000),
                100,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1)
        );
        Coupon savedCoupon = couponRepository.save(expiredCoupon);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(1L, savedCoupon.getId()))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("통합 테스트 - 발급 가능 쿠폰 목록 조회")
    void integration_GetAvailableCoupons() {
        // Given
        createAndSaveCoupon("발급 가능 쿠폰1", BigDecimal.valueOf(5000), 100);
        createAndSaveCoupon("발급 가능 쿠폰2", BigDecimal.valueOf(10000), 50);

        // 소진된 쿠폰
        Coupon soldOutCoupon = createAndSaveCoupon("소진 쿠폰", BigDecimal.valueOf(3000), 1);
        couponService.issueCoupon(1L, soldOutCoupon.getId());

        // 만료된 쿠폰
        Coupon expiredCoupon = Coupon.create(
                "만료 쿠폰",
                BigDecimal.valueOf(2000),
                100,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1)
        );
        couponRepository.save(expiredCoupon);

        // When
        List<Coupon> availableCoupons = couponService.getAvailableCoupons();

        // Then
        assertThat(availableCoupons).hasSize(2);
        assertThat(availableCoupons)
                .extracting(Coupon::getName)
                .containsExactlyInAnyOrder("발급 가능 쿠폰1", "발급 가능 쿠폰2");

        assertThat(availableCoupons)
                .allMatch(c -> c.getStatus() == CouponStatus.ACTIVE)
                .allMatch(c -> c.getAvailableQuantity() > 0)
                .allMatch(Coupon::isIssuable);
    }

    @Test
    @DisplayName("통합 테스트 - 존재하지 않는 쿠폰 발급 시도")
    void integration_IssueNonexistentCoupon() {
        // Given
        Long nonexistentCouponId = 99999L;
        Long userId = 1L;

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(userId, nonexistentCouponId))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("통합 테스트 - 존재하지 않는 발급 쿠폰 사용 시도")
    void integration_UseNonexistentCouponUser() {
        // Given
        Long nonexistentCouponUserId = 99999L;
        Long orderId = 100L;

        // When & Then
        assertThatThrownBy(() -> couponService.useCoupon(nonexistentCouponUserId, orderId))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("통합 테스트 - 모든 쿠폰 목록 조회")
    void integration_GetAllCoupons() {
        // Given
        createAndSaveCoupon("쿠폰1", BigDecimal.valueOf(5000), 100);
        createAndSaveCoupon("쿠폰2", BigDecimal.valueOf(10000), 50);
        createAndSaveCoupon("쿠폰3", BigDecimal.valueOf(3000), 30);

        // When
        List<Coupon> allCoupons = couponService.getAllCoupons();

        // Then
        assertThat(allCoupons).hasSize(3);
        assertThat(allCoupons)
                .extracting(Coupon::getName)
                .containsExactlyInAnyOrder("쿠폰1", "쿠폰2", "쿠폰3");
    }

    @Test
    @DisplayName("통합 테스트 - 다양한 시나리오 복합 테스트")
    void integration_ComplexScenario() {
        // Given
        Coupon coupon = createAndSaveCoupon("복합 테스트 쿠폰", BigDecimal.valueOf(10000), 5);
        Long user1 = 1L;
        Long user2 = 2L;
        Long user3 = 3L;

        // When - 3명의 사용자가 쿠폰 발급
        CouponUser user1Coupon = couponService.issueCoupon(user1, coupon.getId());
        CouponUser user2Coupon = couponService.issueCoupon(user2, coupon.getId());
        CouponUser user3Coupon = couponService.issueCoupon(user3, coupon.getId());

        // user1이 쿠폰 사용
        couponService.useCoupon(user1Coupon.getId(), 100L);

        // Then
        // 쿠폰 수량 확인
        Coupon updatedCoupon = couponService.getCoupon(coupon.getId());
        assertThat(updatedCoupon.getAvailableQuantity()).isEqualTo(2);

        // user1 - 미사용 쿠폰 0개 (사용함)
        assertThat(couponService.getMyCoupons(user1)).isEmpty();
        assertThat(couponService.getAllMyCoupons(user1)).hasSize(1);
        assertThat(couponService.getAllMyCoupons(user1).get(0).getCouponUser().getIsUsed()).isTrue();

        // user2 - 미사용 쿠폰 1개
        assertThat(couponService.getMyCoupons(user2)).hasSize(1);
        assertThat(couponService.getMyCoupons(user2).get(0).getCouponUser().getIsUsed()).isFalse();

        // user3 - 미사용 쿠폰 1개
        assertThat(couponService.getMyCoupons(user3)).hasSize(1);

        // 발급 가능 쿠폰 목록에 여전히 포함됨 (수량 남음)
        List<Coupon> availableCoupons = couponService.getAvailableCoupons();
        assertThat(availableCoupons).hasSize(1);
        assertThat(availableCoupons.get(0).getId()).isEqualTo(coupon.getId());
    }

    @Test
    @DisplayName("통합 테스트 - CouponUserWithCoupon 기능 검증")
    void integration_CouponUserWithCouponFeatures() {
        // Given
        Coupon coupon1 = createAndSaveCoupon("쿠폰A", BigDecimal.valueOf(5000), 100);
        Coupon coupon2 = createAndSaveCoupon("쿠폰B", BigDecimal.valueOf(8000), 100);
        Long userId = 1L;

        couponService.issueCoupon(userId, coupon1.getId());
        couponService.issueCoupon(userId, coupon2.getId());

        // When
        List<CouponService.CouponUserWithCoupon> myCoupons = couponService.getMyCoupons(userId);

        // Then
        assertThat(myCoupons).hasSize(2);

        // isSameCouponId 메서드 테스트
        CouponService.CouponUserWithCoupon firstCoupon = myCoupons.get(0);
        assertThat(firstCoupon.isSameCouponId(firstCoupon.getCoupon().getId())).isTrue();
        assertThat(firstCoupon.isSameCouponId(99999L)).isFalse();

        // Getter 메서드 테스트
        assertThat(firstCoupon.getCoupon()).isNotNull();
        assertThat(firstCoupon.getCouponUser()).isNotNull();
        assertThat(firstCoupon.getCoupon().getDiscountAmount()).isIn(
                BigDecimal.valueOf(5000), BigDecimal.valueOf(8000)
        );
    }

    // Helper methods
    private Coupon createAndSaveCoupon(String name, BigDecimal discountAmount, Integer quantity) {
        Coupon coupon = Coupon.create(
                name,
                discountAmount,
                quantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        return couponRepository.save(coupon);
    }
}
