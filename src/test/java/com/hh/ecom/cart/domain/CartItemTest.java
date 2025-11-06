package com.hh.ecom.cart.domain;

import com.hh.ecom.cart.domain.exception.CartErrorCode;
import com.hh.ecom.cart.domain.exception.CartException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CartItem 도메인 단위 테스트")
class CartItemTest {

    @Nested
    @DisplayName("장바구니 아이템 생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 값으로 장바구니 아이템 생성에 성공한다")
        void create_success() {
            // given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 5;

            // when
            CartItem cartItem = CartItem.create(userId, productId, quantity);

            // then
            assertThat(cartItem.getUserId()).isEqualTo(userId);
            assertThat(cartItem.getProductId()).isEqualTo(productId);
            assertThat(cartItem.getQuantity()).isEqualTo(quantity);
            assertThat(cartItem.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다")
        void create_nullUserId() {
            // when & then
            assertThatThrownBy(() -> CartItem.create(null, 1L, 1))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 사용자 ID")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_USER_ID);
        }

        @Test
        @DisplayName("userId가 0 이하면 예외가 발생한다")
        void create_invalidUserId() {
            // when & then
            assertThatThrownBy(() -> CartItem.create(0L, 1L, 1))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 사용자 ID")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_USER_ID);
        }

        @Test
        @DisplayName("productId가 null이면 예외가 발생한다")
        void create_nullProductId() {
            // when & then
            assertThatThrownBy(() -> CartItem.create(1L, null, 1))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 상품 ID")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_PRODUCT_ID);
        }

        @Test
        @DisplayName("productId가 0 이하면 예외가 발생한다")
        void create_invalidProductId() {
            // when & then
            assertThatThrownBy(() -> CartItem.create(1L, -1L, 1))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 상품 ID")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_PRODUCT_ID);
        }

        @Test
        @DisplayName("quantity가 null이면 예외가 발생한다")
        void create_nullQuantity() {
            // when & then
            assertThatThrownBy(() -> CartItem.create(1L, 1L, null))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("quantity가 0 이하면 예외가 발생한다")
        void create_invalidQuantity() {
            // when & then
            assertThatThrownBy(() -> CartItem.create(1L, 1L, 0))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("수량 변경 테스트")
    class UpdateQuantityTest {

        @Test
        @DisplayName("유효한 수량으로 변경에 성공한다")
        void updateQuantity_success() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when
            CartItem updated = cartItem.updateQuantity(10);

            // then
            assertThat(updated.getQuantity()).isEqualTo(10);
            assertThat(updated.getUserId()).isEqualTo(cartItem.getUserId());
            assertThat(updated.getProductId()).isEqualTo(cartItem.getProductId());
        }

        @Test
        @DisplayName("수량을 1로 변경할 수 있다")
        void updateQuantity_toOne() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when
            CartItem updated = cartItem.updateQuantity(1);

            // then
            assertThat(updated.getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("수량을 0으로 변경하면 예외가 발생한다")
        void updateQuantity_toZero() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.updateQuantity(0))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("수량을 음수로 변경하면 예외가 발생한다")
        void updateQuantity_toNegative() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.updateQuantity(-5))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("수량을 null로 변경하면 예외가 발생한다")
        void updateQuantity_toNull() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.updateQuantity(null))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("수량 증가 테스트")
    class IncreaseQuantityTest {

        @Test
        @DisplayName("수량 증가에 성공한다")
        void increaseQuantity_success() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when
            CartItem increased = cartItem.increaseQuantity(3);

            // then
            assertThat(increased.getQuantity()).isEqualTo(8);
        }

        @Test
        @DisplayName("추가 수량이 0이면 예외가 발생한다")
        void increaseQuantity_byZero() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.increaseQuantity(0))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("추가 수량이 음수면 예외가 발생한다")
        void increaseQuantity_byNegative() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.increaseQuantity(-1))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("추가 수량이 null이면 예외가 발생한다")
        void increaseQuantity_byNull() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.increaseQuantity(null))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("수량 감소 테스트")
    class DecreaseQuantityTest {

        @Test
        @DisplayName("수량 감소에 성공한다")
        void decreaseQuantity_success() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when
            CartItem decreased = cartItem.decreaseQuantity(2);

            // then
            assertThat(decreased.getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("수량을 1까지 감소할 수 있다")
        void decreaseQuantity_toOne() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when
            CartItem decreased = cartItem.decreaseQuantity(4);

            // then
            assertThat(decreased.getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("수량이 0 이하가 되면 예외가 발생한다")
        void decreaseQuantity_toZeroOrLess() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.decreaseQuantity(5))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("감소 수량이 현재 수량보다 크면 예외가 발생한다")
        void decreaseQuantity_exceedsCurrentQuantity() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.decreaseQuantity(10))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("감소 수량이 0이면 예외가 발생한다")
        void decreaseQuantity_byZero() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.decreaseQuantity(0))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("감소 수량이 음수면 예외가 발생한다")
        void decreaseQuantity_byNegative() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThatThrownBy(() -> cartItem.decreaseQuantity(-1))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("유효하지 않은 수량")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("사용자 소유 확인 테스트")
    class BelongsToUserTest {

        @Test
        @DisplayName("동일한 사용자 ID이면 true를 반환한다")
        void belongsToUser_sameUserId() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThat(cartItem.belongsToUser(1L)).isTrue();
        }

        @Test
        @DisplayName("다른 사용자 ID이면 false를 반환한다")
        void belongsToUser_differentUserId() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThat(cartItem.belongsToUser(2L)).isFalse();
        }
    }

    @Nested
    @DisplayName("동일 상품 확인 테스트")
    class IsSameProductTest {

        @Test
        @DisplayName("동일한 상품 ID이면 true를 반환한다")
        void isSameProduct_sameProductId() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThat(cartItem.isSameProduct(1L)).isTrue();
        }

        @Test
        @DisplayName("다른 상품 ID이면 false를 반환한다")
        void isSameProduct_differentProductId() {
            // given
            CartItem cartItem = CartItem.create(1L, 1L, 5);

            // when & then
            assertThat(cartItem.isSameProduct(2L)).isFalse();
        }
    }

    @Nested
    @DisplayName("불변성 검증 테스트")
    class ImmutabilityTest {

        @Test
        @DisplayName("수량 변경 시 원본 객체는 변경되지 않는다")
        void immutability_updateQuantity() {
            // given
            CartItem original = CartItem.create(1L, 1L, 5);
            Integer originalQuantity = original.getQuantity();

            // when
            CartItem updated = original.updateQuantity(10);

            // then
            assertThat(original.getQuantity()).isEqualTo(originalQuantity);
            assertThat(updated.getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("수량 증가 시 원본 객체는 변경되지 않는다")
        void immutability_increaseQuantity() {
            // given
            CartItem original = CartItem.create(1L, 1L, 5);
            Integer originalQuantity = original.getQuantity();

            // when
            CartItem increased = original.increaseQuantity(3);

            // then
            assertThat(original.getQuantity()).isEqualTo(originalQuantity);
            assertThat(increased.getQuantity()).isEqualTo(8);
        }

        @Test
        @DisplayName("수량 감소 시 원본 객체는 변경되지 않는다")
        void immutability_decreaseQuantity() {
            // given
            CartItem original = CartItem.create(1L, 1L, 5);
            Integer originalQuantity = original.getQuantity();

            // when
            CartItem decreased = original.decreaseQuantity(2);

            // then
            assertThat(original.getQuantity()).isEqualTo(originalQuantity);
            assertThat(decreased.getQuantity()).isEqualTo(3);
        }
    }
}
