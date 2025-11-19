package com.hh.ecom.coupon.domain;

import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon 도메인 단위 테스트")
class CouponTest {

    @Nested
    @DisplayName("쿠폰 생성 테스트")
    class CreateCouponTest {

        @Test
        @DisplayName("유효한 파라미터로 쿠폰 생성에 성공한다")
        void create_success() {
            // given
            String name = "신규회원 할인 쿠폰";
            BigDecimal discountAmount = BigDecimal.valueOf(5000);
            Integer totalQuantity = 100;
            LocalDateTime startDate = LocalDateTime.now().plusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(30);

            // when
            Coupon coupon = Coupon.create(name, discountAmount, totalQuantity, startDate, endDate);

            // then
            assertThat(coupon.getName()).isEqualTo(name);
            assertThat(coupon.getDiscountAmount()).isEqualTo(discountAmount);
            assertThat(coupon.getTotalQuantity()).isEqualTo(totalQuantity);
            assertThat(coupon.getAvailableQuantity()).isEqualTo(totalQuantity);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
            assertThat(coupon.getStartDate()).isEqualTo(startDate);
            assertThat(coupon.getEndDate()).isEqualTo(endDate);
            assertThat(coupon.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("쿠폰명이 null이면 예외가 발생한다")
        void create_nullName() {
            // given
            String name = null;
            BigDecimal discountAmount = BigDecimal.valueOf(5000);
            Integer totalQuantity = 100;
            LocalDateTime startDate = LocalDateTime.now().plusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(30);

            // when & then
            assertThatThrownBy(() -> Coupon.create(name, discountAmount, totalQuantity, startDate, endDate))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("쿠폰명은 필수입니다")
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("쿠폰명이 빈 문자열이면 예외가 발생한다")
        void create_blankName() {
            // given
            String name = "   ";
            BigDecimal discountAmount = BigDecimal.valueOf(5000);
            Integer totalQuantity = 100;
            LocalDateTime startDate = LocalDateTime.now().plusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(30);

            // when & then
            assertThatThrownBy(() -> Coupon.create(name, discountAmount, totalQuantity, startDate, endDate))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("쿠폰명은 필수입니다");
        }

        @Test
        @DisplayName("할인 금액이 0 이하이면 예외가 발생한다")
        void create_invalidDiscountAmount() {
            // given
            String name = "테스트 쿠폰";
            BigDecimal discountAmount = BigDecimal.ZERO;
            Integer totalQuantity = 100;
            LocalDateTime startDate = LocalDateTime.now().plusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(30);

            // when & then
            assertThatThrownBy(() -> Coupon.create(name, discountAmount, totalQuantity, startDate, endDate))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("할인 금액은 0보다 커야 합니다");
        }

        @Test
        @DisplayName("총 수량이 0 이하이면 예외가 발생한다")
        void create_invalidTotalQuantity() {
            // given
            String name = "테스트 쿠폰";
            BigDecimal discountAmount = BigDecimal.valueOf(5000);
            Integer totalQuantity = 0;
            LocalDateTime startDate = LocalDateTime.now().plusDays(1);
            LocalDateTime endDate = LocalDateTime.now().plusDays(30);

            // when & then
            assertThatThrownBy(() -> Coupon.create(name, discountAmount, totalQuantity, startDate, endDate))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("총 수량은 0보다 커야 합니다");
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 예외가 발생한다")
        void create_invalidDateRange() {
            // given
            String name = "테스트 쿠폰";
            BigDecimal discountAmount = BigDecimal.valueOf(5000);
            Integer totalQuantity = 100;
            LocalDateTime startDate = LocalDateTime.now().plusDays(30);
            LocalDateTime endDate = LocalDateTime.now().plusDays(1);

            // when & then
            assertThatThrownBy(() -> Coupon.create(name, discountAmount, totalQuantity, startDate, endDate))
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("종료일은 시작일보다 늦어야 합니다");
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 가능 여부 검증 테스트")
    class ValidateIssuableTest {

        @Test
        @DisplayName("활성 상태이고 기간 내이며 수량이 있으면 발급 가능하다")
        void validateIssuable_success() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when & then - 예외가 발생하지 않음
            coupon.validateIssuable();
        }

        @Test
        @DisplayName("비활성 상태이면 예외가 발생한다")
        void validateIssuable_notActive() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon disabledCoupon = coupon.disable();

            // when & then
            assertThatThrownBy(() -> disabledCoupon.validateIssuable())
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_NOT_ACTIVE);
        }

        @Test
        @DisplayName("발급 기간 전이면 예외가 발생한다")
        void validateIssuable_beforeStartDate() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when & then
            assertThatThrownBy(() -> coupon.validateIssuable())
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_EXPIRED);
        }

        @Test
        @DisplayName("발급 기간 후이면 예외가 발생한다")
        void validateIssuable_afterEndDate() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now().minusDays(1)
            );

            // when & then
            assertThatThrownBy(() -> coupon.validateIssuable())
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_EXPIRED);
        }

        @Test
        @DisplayName("수량이 소진되면 예외가 발생한다")
        void validateIssuable_soldOut() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    1,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon soldOutCoupon = coupon.decreaseQuantity();

            // when & then
            assertThatThrownBy(() -> soldOutCoupon.validateIssuable())
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_SOLD_OUT);
        }
    }

    @Nested
    @DisplayName("쿠폰 수량 감소 테스트")
    class DecreaseQuantityTest {

        @Test
        @DisplayName("수량 감소에 성공한다")
        void decreaseQuantity_success() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when
            Coupon result = coupon.decreaseQuantity();

            // then
            assertThat(result.getAvailableQuantity()).isEqualTo(99);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        }

        @Test
        @DisplayName("마지막 수량을 감소하면 상태가 SOLD_OUT으로 변경된다")
        void decreaseQuantity_lastOne() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    1,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when
            Coupon result = coupon.decreaseQuantity();

            // then
            assertThat(result.getAvailableQuantity()).isEqualTo(0);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("수량이 0이면 예외가 발생한다")
        void decreaseQuantity_noStock() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    1,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon soldOutCoupon = coupon.decreaseQuantity();

            // when & then
            assertThatThrownBy(() -> soldOutCoupon.decreaseQuantity())
                    .isInstanceOf(CouponException.class)
                    .extracting("errorCode")
                    .isEqualTo(CouponErrorCode.COUPON_SOLD_OUT);
        }

        @Test
        @DisplayName("불변성 검증: 원본 객체는 변경되지 않는다")
        void decreaseQuantity_immutability() {
            // given
            Coupon original = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Integer originalQuantity = original.getAvailableQuantity();

            // when
            Coupon decreased = original.decreaseQuantity();

            // then
            assertThat(original.getAvailableQuantity()).isEqualTo(originalQuantity);
            assertThat(decreased.getAvailableQuantity()).isEqualTo(originalQuantity - 1);
        }
    }

    @Nested
    @DisplayName("쿠폰 수량 증가 테스트")
    class IncreaseQuantityTest {

        @Test
        @DisplayName("수량 증가에 성공한다")
        void increaseQuantity_success() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon decreased = coupon.decreaseQuantity();

            // when
            Coupon result = decreased.increaseQuantity();

            // then
            assertThat(result.getAvailableQuantity()).isEqualTo(100);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        }

        @Test
        @DisplayName("SOLD_OUT 상태에서 수량 증가 시 ACTIVE로 변경된다")
        void increaseQuantity_fromSoldOut() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    1,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon soldOut = coupon.decreaseQuantity();

            // when
            Coupon result = soldOut.increaseQuantity();

            // then
            assertThat(result.getAvailableQuantity()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        }

        @Test
        @DisplayName("총 수량을 초과하면 예외가 발생한다")
        void increaseQuantity_exceedTotal() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when & then
            assertThatThrownBy(() -> coupon.increaseQuantity())
                    .isInstanceOf(CouponException.class)
                    .hasMessageContaining("복원할 수량이 초과되었습니다");
        }
    }

    @Nested
    @DisplayName("쿠폰 비활성화 테스트")
    class DisableTest {

        @Test
        @DisplayName("쿠폰 비활성화에 성공한다")
        void disable_success() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when
            Coupon result = coupon.disable();

            // then
            assertThat(result.getStatus()).isEqualTo(CouponStatus.DISABLED);
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 가능 여부 확인 테스트")
    class IsIssuableTest {

        @Test
        @DisplayName("모든 조건을 만족하면 발급 가능하다")
        void isIssuable_true() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when & then
            assertThat(coupon.isIssuable()).isTrue();
        }

        @Test
        @DisplayName("비활성 상태이면 발급 불가능하다")
        void isIssuable_inactive() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon disabled = coupon.disable();

            // when & then
            assertThat(disabled.isIssuable()).isFalse();
        }

        @Test
        @DisplayName("수량이 0이면 발급 불가능하다")
        void isIssuable_noQuantity() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    1,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            Coupon soldOut = coupon.decreaseQuantity();

            // when & then
            assertThat(soldOut.isIssuable()).isFalse();
        }

        @Test
        @DisplayName("발급 기간 전이면 발급 불가능하다")
        void isIssuable_beforeStartDate() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when & then
            assertThat(coupon.isIssuable()).isFalse();
        }

        @Test
        @DisplayName("발급 기간 후이면 발급 불가능하다")
        void isIssuable_afterEndDate() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now().minusDays(1)
            );

            // when & then
            assertThat(coupon.isIssuable()).isFalse();
        }
    }

    @Nested
    @DisplayName("쿠폰 만료 여부 확인 테스트")
    class IsExpiredTest {

        @Test
        @DisplayName("종료일이 지나면 만료 상태이다")
        void isExpired_true() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now().minusDays(1)
            );

            // when & then
            assertThat(coupon.isExpired()).isTrue();
        }

        @Test
        @DisplayName("종료일이 지나지 않았으면 만료 상태가 아니다")
        void isExpired_false() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when & then
            assertThat(coupon.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("복합 시나리오 테스트")
    class ComplexScenarioTest {

        @Test
        @DisplayName("쿠폰 수량이 0이 될 때까지 감소시킬 수 있다")
        void multipleDecrease_untilSoldOut() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    3,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when
            Coupon after1 = coupon.decreaseQuantity();
            Coupon after2 = after1.decreaseQuantity();
            Coupon after3 = after2.decreaseQuantity();

            // then
            assertThat(after1.getAvailableQuantity()).isEqualTo(2);
            assertThat(after2.getAvailableQuantity()).isEqualTo(1);
            assertThat(after3.getAvailableQuantity()).isEqualTo(0);
            assertThat(after3.getStatus()).isEqualTo(CouponStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("수량 감소 후 증가를 반복할 수 있다")
        void decreaseAndIncrease_multiple() {
            // given
            Coupon coupon = Coupon.create(
                    "테스트 쿠폰",
                    BigDecimal.valueOf(5000),
                    100,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );

            // when
            Coupon decreased1 = coupon.decreaseQuantity();
            Coupon decreased2 = decreased1.decreaseQuantity();
            Coupon increased1 = decreased2.increaseQuantity();
            Coupon decreased3 = increased1.decreaseQuantity();

            // then
            assertThat(decreased1.getAvailableQuantity()).isEqualTo(99);
            assertThat(decreased2.getAvailableQuantity()).isEqualTo(98);
            assertThat(increased1.getAvailableQuantity()).isEqualTo(99);
            assertThat(decreased3.getAvailableQuantity()).isEqualTo(98);
        }
    }
}
