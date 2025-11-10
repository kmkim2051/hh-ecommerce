package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserRepository;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUserRepository couponUserRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon testCoupon;
    private CouponUser testCouponUser;

    @BeforeEach
    void setUp() {
        testCoupon = Coupon.create(
                "신규회원 할인 쿠폰",
                BigDecimal.valueOf(5000),
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );

        testCouponUser = CouponUser.issue(
                1L,
                1L,
                LocalDateTime.now().plusDays(30)
        );
    }

    @Nested
    @DisplayName("발급 가능한 쿠폰 목록 조회 테스트")
    class GetAvailableCouponsTest {

        @Test
        @DisplayName("발급 가능한 쿠폰 목록을 조회한다")
        void getAvailableCoupons_success() {
            // given
            List<Coupon> expectedCoupons = List.of(
                    Coupon.create("쿠폰1", BigDecimal.valueOf(3000), 50,
                            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10)),
                    Coupon.create("쿠폰2", BigDecimal.valueOf(5000), 100,
                            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(20))
            );
            given(couponRepository.findAllIssuable()).willReturn(expectedCoupons);

            // when
            List<Coupon> result = couponService.getAvailableCoupons();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting("name")
                    .containsExactly("쿠폰1", "쿠폰2");
            verify(couponRepository).findAllIssuable();
        }

        @Test
        @DisplayName("발급 가능한 쿠폰이 없으면 빈 리스트를 반환한다")
        void getAvailableCoupons_empty() {
            // given
            given(couponRepository.findAllIssuable()).willReturn(List.of());

            // when
            List<Coupon> result = couponService.getAvailableCoupons();

            // then
            assertThat(result).isEmpty();
            verify(couponRepository).findAllIssuable();
        }
    }

    @Nested
    @DisplayName("모든 쿠폰 목록 조회 테스트")
    class GetAllCouponsTest {

        @Test
        @DisplayName("모든 쿠폰 목록을 조회한다")
        void getAllCoupons_success() {
            // given
            List<Coupon> expectedCoupons = List.of(testCoupon);
            given(couponRepository.findAll()).willReturn(expectedCoupons);

            // when
            List<Coupon> result = couponService.getAllCoupons();

            // then
            assertThat(result).hasSize(1);
            verify(couponRepository).findAll();
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 테스트")
    class IssueCouponTest {

        @Test
        @DisplayName("쿠폰 발급에 성공한다")
        void issueCoupon_success() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            Coupon decreasedCoupon = testCoupon.decreaseQuantity();

            given(couponRepository.findById(anyLong())).willReturn(Optional.of(testCoupon));
            given(couponUserRepository.findByUserIdAndCouponId(anyLong(), anyLong()))
                    .willReturn(Optional.empty());
            given(couponRepository.save(any(Coupon.class))).willReturn(decreasedCoupon);
            given(couponUserRepository.save(any(CouponUser.class))).willReturn(testCouponUser);

            // when
            CouponUser result = couponService.issueCoupon(userId, couponId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getCouponId()).isEqualTo(couponId);
            assertThat(result.getIsUsed()).isFalse();

            verify(couponRepository).findById(couponId);
            verify(couponUserRepository).findByUserIdAndCouponId(userId, couponId);
            verify(couponRepository).save(any(Coupon.class));
            verify(couponUserRepository).save(any(CouponUser.class));
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 발급 시 예외가 발생한다")
        void issueCoupon_couponNotFound() {
            // given
            Long userId = 1L;
            Long couponId = 999L;
            given(couponRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);

            verify(couponRepository).findById(couponId);
        }

        @Test
        @DisplayName("이미 발급받은 쿠폰을 재발급 시도하면 예외가 발생한다")
        void issueCoupon_alreadyIssued() {
            // given
            Long userId = 1L;
            Long couponId = 1L;

            given(couponRepository.findById(anyLong())).willReturn(Optional.of(testCoupon));
            given(couponUserRepository.findByUserIdAndCouponId(anyLong(), anyLong()))
                    .willReturn(Optional.of(testCouponUser));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_ALREADY_ISSUED);

            verify(couponRepository).findById(couponId);
            verify(couponUserRepository).findByUserIdAndCouponId(userId, couponId);
        }

        @Test
        @DisplayName("수량이 소진된 쿠폰 발급 시 예외가 발생한다")
        void issueCoupon_soldOut() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            Coupon soldOutCoupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    1,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            ).decreaseQuantity();

            given(couponRepository.findById(anyLong())).willReturn(Optional.of(soldOutCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_SOLD_OUT);

            verify(couponRepository).findById(couponId);
        }

        @Test
        @DisplayName("비활성화된 쿠폰 발급 시 예외가 발생한다")
        void issueCoupon_notActive() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            Coupon disabledCoupon = testCoupon.disable();

            given(couponRepository.findById(anyLong())).willReturn(Optional.of(disabledCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_NOT_ACTIVE);

            verify(couponRepository).findById(couponId);
        }

        @Test
        @DisplayName("발급 기간이 지난 쿠폰 발급 시 예외가 발생한다")
        void issueCoupon_expired() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            Coupon expiredCoupon = Coupon.create(
                    "만료된 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now().minusDays(1)
            );

            given(couponRepository.findById(anyLong())).willReturn(Optional.of(expiredCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_EXPIRED);

            verify(couponRepository).findById(couponId);
        }
    }

    @Nested
    @DisplayName("보유 쿠폰 조회 테스트")
    class GetMyCouponsTest {

        @Test
        @DisplayName("미사용 쿠폰 목록을 조회한다")
        void getMyCoupons_success() {
            // given
            Long userId = 1L;
            List<CouponUser> couponUsers = List.of(
                    CouponUser.issue(userId, 1L, LocalDateTime.now().plusDays(30)),
                    CouponUser.issue(userId, 2L, LocalDateTime.now().plusDays(30))
            );

            Coupon coupon1 = Coupon.create("쿠폰1", BigDecimal.valueOf(3000), 50,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
            Coupon coupon2 = Coupon.create("쿠폰2", BigDecimal.valueOf(5000), 100,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(20));

            given(couponUserRepository.findByUserIdAndIsUsed(anyLong(), anyBoolean()))
                    .willReturn(couponUsers);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon1));
            given(couponRepository.findById(2L)).willReturn(Optional.of(coupon2));

            // when
            List<CouponService.CouponUserWithCoupon> result = couponService.getMyCoupons(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(cwc -> cwc.getCoupon().getName())
                    .containsExactly("쿠폰1", "쿠폰2");
            verify(couponUserRepository).findByUserIdAndIsUsed(userId, false);
        }

        @Test
        @DisplayName("보유한 쿠폰이 없으면 빈 리스트를 반환한다")
        void getMyCoupons_empty() {
            // given
            Long userId = 1L;
            given(couponUserRepository.findByUserIdAndIsUsed(anyLong(), anyBoolean()))
                    .willReturn(List.of());

            // when
            List<CouponService.CouponUserWithCoupon> result = couponService.getMyCoupons(userId);

            // then
            assertThat(result).isEmpty();
            verify(couponUserRepository).findByUserIdAndIsUsed(userId, false);
        }

        @Test
        @DisplayName("쿠폰 정보가 존재하지 않으면 예외가 발생한다")
        void getMyCoupons_couponNotFound() {
            // given
            Long userId = 1L;
            List<CouponUser> couponUsers = List.of(
                    CouponUser.issue(userId, 999L, LocalDateTime.now().plusDays(30))
            );

            given(couponUserRepository.findByUserIdAndIsUsed(anyLong(), anyBoolean()))
                    .willReturn(couponUsers);
            given(couponRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponService.getMyCoupons(userId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);

            verify(couponUserRepository).findByUserIdAndIsUsed(userId, false);
        }
    }

    @Nested
    @DisplayName("전체 보유 쿠폰 조회 테스트")
    class GetAllMyCouponsTest {

        @Test
        @DisplayName("사용 여부와 관계없이 모든 쿠폰을 조회한다")
        void getAllMyCoupons_success() {
            // given
            Long userId = 1L;
            CouponUser unusedCoupon = CouponUser.issue(userId, 1L, LocalDateTime.now().plusDays(30));
            CouponUser usedCoupon = CouponUser.issue(userId, 2L, LocalDateTime.now().plusDays(30))
                    .use(100L);

            List<CouponUser> couponUsers = List.of(unusedCoupon, usedCoupon);

            Coupon coupon1 = Coupon.create("쿠폰1", BigDecimal.valueOf(3000), 50,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
            Coupon coupon2 = Coupon.create("쿠폰2", BigDecimal.valueOf(5000), 100,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(20));

            given(couponUserRepository.findByUserId(anyLong())).willReturn(couponUsers);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon1));
            given(couponRepository.findById(2L)).willReturn(Optional.of(coupon2));

            // when
            List<CouponService.CouponUserWithCoupon> result = couponService.getAllMyCoupons(userId);

            // then
            assertThat(result).hasSize(2);
            verify(couponUserRepository).findByUserId(userId);
        }
    }

    @Nested
    @DisplayName("쿠폰 사용 테스트")
    class UseCouponTest {

        @Test
        @DisplayName("쿠폰 사용에 성공한다")
        void useCoupon_success() {
            // given
            Long couponUserId = 1L;
            Long orderId = 100L;
            CouponUser usedCoupon = testCouponUser.use(orderId);

            given(couponUserRepository.findById(anyLong())).willReturn(Optional.of(testCouponUser));
            given(couponUserRepository.save(any(CouponUser.class))).willReturn(usedCoupon);

            // when
            CouponUser result = couponService.useCoupon(couponUserId, orderId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIsUsed()).isTrue();
            assertThat(result.getOrderId()).isEqualTo(orderId);

            verify(couponUserRepository).findById(couponUserId);
            verify(couponUserRepository).save(any(CouponUser.class));
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 사용 시 예외가 발생한다")
        void useCoupon_couponUserNotFound() {
            // given
            Long couponUserId = 999L;
            Long orderId = 100L;

            given(couponUserRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponService.useCoupon(couponUserId, orderId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_USER_NOT_FOUND);

            verify(couponUserRepository).findById(couponUserId);
        }

        @Test
        @DisplayName("이미 사용된 쿠폰을 재사용 시도하면 예외가 발생한다")
        void useCoupon_alreadyUsed() {
            // given
            Long couponUserId = 1L;
            Long orderId = 100L;
            CouponUser usedCoupon = testCouponUser.use(orderId);

            given(couponUserRepository.findById(anyLong())).willReturn(Optional.of(usedCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.useCoupon(couponUserId, 200L))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_ALREADY_USED);

            verify(couponUserRepository).findById(couponUserId);
        }

        @Test
        @DisplayName("만료된 쿠폰 사용 시 예외가 발생한다")
        void useCoupon_expired() {
            // given
            Long couponUserId = 1L;
            Long orderId = 100L;
            CouponUser expiredCoupon = CouponUser.issue(
                    1L,
                    1L,
                    LocalDateTime.now().minusDays(1)
            );

            given(couponUserRepository.findById(anyLong())).willReturn(Optional.of(expiredCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.useCoupon(couponUserId, orderId))
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_USER_EXPIRED);

            verify(couponUserRepository).findById(couponUserId);
        }
    }

    @Nested
    @DisplayName("쿠폰 조회 테스트")
    class GetCouponTest {

        @Test
        @DisplayName("ID로 쿠폰을 조회한다")
        void getCoupon_success() {
            // given
            Long couponId = 1L;
            given(couponRepository.findById(anyLong())).willReturn(Optional.of(testCoupon));

            // when
            Coupon result = couponService.getCoupon(couponId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("신규회원 할인 쿠폰");
            verify(couponRepository).findById(couponId);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 조회 시 예외가 발생한다")
        void getCoupon_notFound() {
            // given
            Long couponId = 999L;
            given(couponRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponService.getCoupon(couponId))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("쿠폰을 찾을 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);

            verify(couponRepository).findById(couponId);
        }
    }

    @Nested
    @DisplayName("CouponUserWithCoupon 테스트")
    class CouponUserWithCouponTest {

        @Test
        @DisplayName("CouponUserWithCoupon 객체를 생성한다")
        void of_success() {
            // given
            CouponUser couponUser = testCouponUser;
            Coupon coupon = testCoupon;

            // when
            CouponService.CouponUserWithCoupon result =
                    CouponService.CouponUserWithCoupon.of(couponUser, coupon);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCouponUser()).isEqualTo(couponUser);
            assertThat(result.getCoupon()).isEqualTo(coupon);
        }

        @Test
        @DisplayName("같은 쿠폰 ID를 가진 경우 true를 반환한다")
        void isSameCouponId_true() {
            // given
            Coupon coupon = Coupon.builder()
                    .id(1L)
                    .name("테스트 쿠폰")
                    .discountAmount(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .availableQuantity(100)
                    .status(CouponStatus.ACTIVE)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .version(0L)
                    .build();

            CouponService.CouponUserWithCoupon couponUserWithCoupon =
                    CouponService.CouponUserWithCoupon.of(testCouponUser, coupon);

            // when & then
            assertThat(couponUserWithCoupon.isSameCouponId(1L)).isTrue();
        }

        @Test
        @DisplayName("다른 쿠폰 ID를 가진 경우 false를 반환한다")
        void isSameCouponId_false() {
            // given
            Coupon coupon = Coupon.builder()
                    .id(1L)
                    .name("테스트 쿠폰")
                    .discountAmount(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .availableQuantity(100)
                    .status(CouponStatus.ACTIVE)
                    .startDate(LocalDateTime.now())
                    .endDate(LocalDateTime.now().plusDays(30))
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .version(0L)
                    .build();

            CouponService.CouponUserWithCoupon couponUserWithCoupon =
                    CouponService.CouponUserWithCoupon.of(testCouponUser, coupon);

            // when & then
            assertThat(couponUserWithCoupon.isSameCouponId(999L)).isFalse();
        }
    }
}
