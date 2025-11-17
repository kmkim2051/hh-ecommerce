package com.hh.ecom.point.domain;

import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Point 도메인 단위 테스트")
class PointTest {

    @Nested
    @DisplayName("포인트 계좌 생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("새로운 포인트 계좌를 생성하면 잔액이 0으로 초기화된다")
        void create_newPoint() {
            // given
            Long userId = 1L;

            // when
            Point point = Point.createWithUserId(userId);

            // then
            assertThat(point.getUserId()).isEqualTo(userId);
            assertThat(point.getBalance()).isEqualTo(BigDecimal.ZERO);
            assertThat(point.getUpdatedAt()).isNotNull();
            assertThat(point.isZeroBalance()).isTrue();
        }
    }

    @Nested
    @DisplayName("포인트 충전 테스트")
    class ChargeTest {

        @Test
        @DisplayName("포인트를 충전하면 잔액이 증가한다")
        void charge_success() {
            // given
            Point point = Point.createWithUserId(1L);
            BigDecimal chargeAmount = BigDecimal.valueOf(10000);

            // when
            Point charged = point.charge(chargeAmount);

            // then
            assertThat(charged.getBalance()).isEqualTo(BigDecimal.valueOf(10000));
            assertThat(charged.getUpdatedAt()).isAfter(point.getUpdatedAt());
            assertThat(charged.isPositiveBalance()).isTrue();
        }

        @Test
        @DisplayName("여러 번 충전하면 누적된다")
        void charge_multiple() {
            // given
            Point point = Point.createWithUserId(1L);

            // when
            Point charged1 = point.charge(BigDecimal.valueOf(5000));
            Point charged2 = charged1.charge(BigDecimal.valueOf(3000));

            // then
            assertThat(charged2.getBalance()).isEqualTo(BigDecimal.valueOf(8000));
        }

        @Test
        @DisplayName("0 이하의 금액으로 충전 시 예외가 발생한다")
        void charge_invalidAmount() {
            // given
            Point point = Point.createWithUserId(1L);

            // when & then
            assertThatThrownBy(() -> point.charge(BigDecimal.ZERO))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);

            assertThatThrownBy(() -> point.charge(BigDecimal.valueOf(-1000)))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
        }

        @Test
        @DisplayName("null 금액으로 충전 시 예외가 발생한다")
        void charge_nullAmount() {
            // given
            Point point = Point.createWithUserId(1L);

            // when & then
            assertThatThrownBy(() -> point.charge(null))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
        }
    }

    @Nested
    @DisplayName("포인트 사용 테스트")
    class UseTest {

        @Test
        @DisplayName("잔액이 충분하면 포인트를 사용할 수 있다")
        void use_success() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(10000));

            // when
            Point used = charged.use(BigDecimal.valueOf(3000));

            // then
            assertThat(used.getBalance()).isEqualTo(BigDecimal.valueOf(7000));
            assertThat(used.getUpdatedAt()).isAfterOrEqualTo(charged.getUpdatedAt());
        }

        @Test
        @DisplayName("잔액 전체를 사용할 수 있다")
        void use_allBalance() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(10000));

            // when
            Point used = charged.use(BigDecimal.valueOf(10000));

            // then
            assertThat(used.getBalance()).isEqualTo(BigDecimal.ZERO);
            assertThat(used.isZeroBalance()).isTrue();
        }

        @Test
        @DisplayName("잔액이 부족하면 예외가 발생한다")
        void use_insufficientBalance() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(5000));

            // when & then
            assertThatThrownBy(() -> charged.use(BigDecimal.valueOf(10000)))
                    .isInstanceOf(PointException.class)
                    .hasMessageContaining("포인트 잔액이 부족합니다")
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("잔액이 0일 때 사용 시도 시 예외가 발생한다")
        void use_zeroBalance() {
            // given
            Point point = Point.createWithUserId(1L);

            // when & then
            assertThatThrownBy(() -> point.use(BigDecimal.valueOf(1000)))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("0 이하의 금액으로 사용 시 예외가 발생한다")
        void use_invalidAmount() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(10000));

            // when & then
            assertThatThrownBy(() -> charged.use(BigDecimal.ZERO))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
        }
    }

    @Nested
    @DisplayName("포인트 환불 테스트")
    class RefundTest {

        @Test
        @DisplayName("포인트를 환불하면 잔액이 증가한다")
        void refund_success() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(10000));
            Point used = charged.use(BigDecimal.valueOf(3000));

            // when
            Point refunded = used.refund(BigDecimal.valueOf(3000));

            // then
            assertThat(refunded.getBalance()).isEqualTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("잔액이 0인 상태에서도 환불할 수 있다")
        void refund_zeroBalance() {
            // given
            Point point = Point.createWithUserId(1L);

            // when
            Point refunded = point.refund(BigDecimal.valueOf(5000));

            // then
            assertThat(refunded.getBalance()).isEqualTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("0 이하의 금액으로 환불 시 예외가 발생한다")
        void refund_invalidAmount() {
            // given
            Point point = Point.createWithUserId(1L);

            // when & then
            assertThatThrownBy(() -> point.refund(BigDecimal.ZERO))
                    .isInstanceOf(PointException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
        }
    }

    @Nested
    @DisplayName("잔액 확인 테스트")
    class BalanceCheckTest {

        @Test
        @DisplayName("충분한 잔액이 있으면 true를 반환한다")
        void hasEnoughBalance_sufficient() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(10000));

            // when & then
            assertThat(charged.hasEnoughBalance(BigDecimal.valueOf(5000))).isTrue();
            assertThat(charged.hasEnoughBalance(BigDecimal.valueOf(10000))).isTrue();
        }

        @Test
        @DisplayName("잔액이 부족하면 false를 반환한다")
        void hasEnoughBalance_insufficient() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(5000));

            // when & then
            assertThat(charged.hasEnoughBalance(BigDecimal.valueOf(10000))).isFalse();
        }

        @Test
        @DisplayName("null이나 0 이하의 금액 확인 시 false를 반환한다")
        void hasEnoughBalance_invalidAmount() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(10000));

            // when & then
            assertThat(charged.hasEnoughBalance(null)).isFalse();
            assertThat(charged.hasEnoughBalance(BigDecimal.ZERO)).isFalse();
            assertThat(charged.hasEnoughBalance(BigDecimal.valueOf(-1000))).isFalse();
        }

        @Test
        @DisplayName("잔액이 0인지 확인할 수 있다")
        void isZeroBalance() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(5000));
            Point used = charged.use(BigDecimal.valueOf(5000));

            // when & then
            assertThat(point.isZeroBalance()).isTrue();
            assertThat(charged.isZeroBalance()).isFalse();
            assertThat(used.isZeroBalance()).isTrue();
        }

        @Test
        @DisplayName("잔액이 양수인지 확인할 수 있다")
        void isPositiveBalance() {
            // given
            Point point = Point.createWithUserId(1L);
            Point charged = point.charge(BigDecimal.valueOf(5000));

            // when & then
            assertThat(point.isPositiveBalance()).isFalse();
            assertThat(charged.isPositiveBalance()).isTrue();
        }
    }

    @Nested
    @DisplayName("불변성 테스트")
    class ImmutabilityTest {

        @Test
        @DisplayName("충전/사용/환불 시 원본 객체는 변경되지 않는다")
        void immutability() {
            // given
            Point original = Point.createWithUserId(1L);
            Point charged = original.charge(BigDecimal.valueOf(10000));
            BigDecimal originalBalance = original.getBalance();
            BigDecimal chargedBalance = charged.getBalance();

            // when
            Point used = charged.use(BigDecimal.valueOf(3000));
            Point refunded = used.refund(BigDecimal.valueOf(1000));

            // then - 원본들은 변경되지 않음
            assertThat(original.getBalance()).isEqualTo(originalBalance);
            assertThat(charged.getBalance()).isEqualTo(chargedBalance);

            // 새로운 객체들만 변경됨
            assertThat(used.getBalance()).isEqualTo(BigDecimal.valueOf(7000));
            assertThat(refunded.getBalance()).isEqualTo(BigDecimal.valueOf(8000));
        }
    }
}
