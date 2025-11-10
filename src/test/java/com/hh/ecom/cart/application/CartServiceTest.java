package com.hh.ecom.cart.application;

import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemRepository;
import com.hh.ecom.cart.domain.exception.CartErrorCode;
import com.hh.ecom.cart.domain.exception.CartException;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private Product testProduct;
    private CartItem testCartItem;

    @BeforeEach
    void setUp() {
        testProduct = Product.create(
                "테스트 상품",
                "상품 설명",
                BigDecimal.valueOf(10000),
                100
        );
        testCartItem = CartItem.create(1L, 1L, 5);
    }

    @Nested
    @DisplayName("장바구니 상품 추가 테스트")
    class AddToCartTest {

        @Test
        @DisplayName("새로운 상품을 장바구니에 추가할 수 있다")
        void addToCart_newProduct_success() {
            // given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 5;

            given(productRepository.findById(productId)).willReturn(Optional.of(testProduct));
            given(cartItemRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
            given(cartItemRepository.save(any(CartItem.class))).willAnswer(invocation -> {
                CartItem item = invocation.getArgument(0);
                return CartItem.builder()
                        .id(1L)
                        .userId(item.getUserId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .createdAt(item.getCreatedAt())
                        .build();
            });

            // when
            CartItem result = cartService.addToCart(userId, productId, quantity);

            // then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getQuantity()).isEqualTo(quantity);
            verify(cartItemRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("기존에 있던 상품이면 수량을 증가시킨다")
        void addToCart_existingProduct_increaseQuantity() {
            // given
            Long userId = 1L;
            Long productId = 1L;
            Integer additionalQuantity = 3;
            CartItem existingItem = CartItem.builder()
                    .id(1L)
                    .userId(userId)
                    .productId(productId)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(productRepository.findById(productId)).willReturn(Optional.of(testProduct));
            given(cartItemRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.of(existingItem));
            given(cartItemRepository.save(any(CartItem.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            CartItem result = cartService.addToCart(userId, productId, additionalQuantity);

            // then
            assertThat(result.getQuantity()).isEqualTo(8);
            verify(cartItemRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 예외가 발생한다")
        void addToCart_productNotFound() {
            // given
            Long userId = 1L;
            Long productId = 999L;
            Integer quantity = 5;

            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(ProductException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("상품이 판매 불가능하면 예외가 발생한다")
        void addToCart_productNotAvailable() {
            // given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 5;
            Product inactiveProduct = testProduct.softDelete();

            given(productRepository.findById(productId)).willReturn(Optional.of(inactiveProduct));

            // when & then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(ProductException.class)
                    .hasMessageContaining("판매 불가능한 상품입니다")
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_AVAILABLE_FOR_SALE);
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 발생한다")
        void addToCart_insufficientStock() {
            // given
            Long userId = 1L;
            Long productId = 1L;
            Integer quantity = 200;

            given(productRepository.findById(productId)).willReturn(Optional.of(testProduct));

            // when & then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("재고 수량을 초과할 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }

        @Test
        @DisplayName("기존 수량과 합쳐서 재고를 초과하면 예외가 발생한다")
        void addToCart_existingQuantityExceedsStock() {
            // given
            Long userId = 1L;
            Long productId = 1L;
            Integer additionalQuantity = 50;
            Product limitedProduct = Product.create("테스트", "설명", BigDecimal.valueOf(10000), 60);
            CartItem existingItem = CartItem.builder()
                    .id(1L)
                    .userId(userId)
                    .productId(productId)
                    .quantity(50)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(productRepository.findById(productId)).willReturn(Optional.of(limitedProduct));
            given(cartItemRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.of(existingItem));

            // when & then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, additionalQuantity))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("재고 수량을 초과할 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }
    }

    @Nested
    @DisplayName("장바구니 수량 변경 테스트")
    class UpdateCartItemQuantityTest {

        @Test
        @DisplayName("장바구니 수량을 변경할 수 있다")
        void updateCartItemQuantity_success() {
            // given
            Long cartItemId = 1L;
            Long userId = 1L;
            Integer newQuantity = 10;
            CartItem existingItem = CartItem.builder()
                    .id(cartItemId)
                    .userId(userId)
                    .productId(1L)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existingItem));
            given(productRepository.findById(1L)).willReturn(Optional.of(testProduct));
            given(cartItemRepository.save(any(CartItem.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            CartItem result = cartService.updateCartItemQuantity(cartItemId, userId, newQuantity);

            // then
            assertThat(result.getQuantity()).isEqualTo(newQuantity);
            verify(cartItemRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("장바구니 아이템이 존재하지 않으면 예외가 발생한다")
        void updateCartItemQuantity_cartItemNotFound() {
            // given
            Long cartItemId = 999L;
            Long userId = 1L;
            Integer newQuantity = 10;

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, userId, newQuantity))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니 아이템을 찾을 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 장바구니 아이템이면 예외가 발생한다")
        void updateCartItemQuantity_unauthorized() {
            // given
            Long cartItemId = 1L;
            Long userId = 2L;  // 다른 사용자
            Integer newQuantity = 10;
            CartItem existingItem = CartItem.builder()
                    .id(cartItemId)
                    .userId(1L)  // 원래 소유자
                    .productId(1L)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existingItem));

            // when & then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, userId, newQuantity))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니에 접근할 권한이 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.UNAUTHORIZED_CART_ACCESS);
        }

        @Test
        @DisplayName("변경하려는 수량이 재고를 초과하면 예외가 발생한다")
        void updateCartItemQuantity_exceedsStock() {
            // given
            Long cartItemId = 1L;
            Long userId = 1L;
            Integer newQuantity = 200;
            CartItem existingItem = CartItem.builder()
                    .id(cartItemId)
                    .userId(userId)
                    .productId(1L)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existingItem));
            given(productRepository.findById(1L)).willReturn(Optional.of(testProduct));

            // when & then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItemId, userId, newQuantity))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("재고 수량을 초과할 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }
    }

    @Nested
    @DisplayName("장바구니 상품 삭제 테스트")
    class RemoveCartItemTest {

        @Test
        @DisplayName("장바구니에서 상품을 삭제할 수 있다")
        void removeCartItem_success() {
            // given
            Long cartItemId = 1L;
            Long userId = 1L;
            CartItem existingItem = CartItem.builder()
                    .id(cartItemId)
                    .userId(userId)
                    .productId(1L)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existingItem));
            doNothing().when(cartItemRepository).deleteById(cartItemId);

            // when
            cartService.removeCartItem(cartItemId, userId);

            // then
            verify(cartItemRepository).deleteById(cartItemId);
        }

        @Test
        @DisplayName("장바구니 아이템이 존재하지 않으면 예외가 발생한다")
        void removeCartItem_cartItemNotFound() {
            // given
            Long cartItemId = 999L;
            Long userId = 1L;

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> cartService.removeCartItem(cartItemId, userId))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니 아이템을 찾을 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 장바구니 아이템이면 예외가 발생한다")
        void removeCartItem_unauthorized() {
            // given
            Long cartItemId = 1L;
            Long userId = 2L;  // 다른 사용자
            CartItem existingItem = CartItem.builder()
                    .id(cartItemId)
                    .userId(1L)  // 원래 소유자
                    .productId(1L)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(existingItem));

            // when & then
            assertThatThrownBy(() -> cartService.removeCartItem(cartItemId, userId))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니에 접근할 권한이 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.UNAUTHORIZED_CART_ACCESS);
        }
    }

    @Nested
    @DisplayName("장바구니 조회 테스트")
    class GetCartItemsTest {

        @Test
        @DisplayName("사용자의 장바구니 목록을 조회할 수 있다")
        void getCartItems_success() {
            // given
            Long userId = 1L;
            List<CartItem> cartItems = List.of(
                    CartItem.builder().id(1L).userId(userId).productId(1L).quantity(5)
                            .createdAt(java.time.LocalDateTime.now()).build(),
                    CartItem.builder().id(2L).userId(userId).productId(2L).quantity(3)
                            .createdAt(java.time.LocalDateTime.now()).build()
            );

            given(cartItemRepository.findAllByUserId(userId)).willReturn(cartItems);

            // when
            List<CartItem> result = cartService.getCartItems(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(CartItem::getUserId).containsOnly(userId);
        }

        @Test
        @DisplayName("장바구니가 비어있으면 빈 리스트를 반환한다")
        void getCartItems_emptyCart() {
            // given
            Long userId = 1L;
            given(cartItemRepository.findAllByUserId(userId)).willReturn(List.of());

            // when
            List<CartItem> result = cartService.getCartItems(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("장바구니 비우기 테스트")
    class ClearCartTest {

        @Test
        @DisplayName("장바구니를 비울 수 있다")
        void clearCart_success() {
            // given
            Long userId = 1L;
            doNothing().when(cartItemRepository).deleteAllByUserId(userId);

            // when
            cartService.clearCart(userId);

            // then
            verify(cartItemRepository).deleteAllByUserId(userId);
        }
    }

    @Nested
    @DisplayName("특정 상품들 삭제 테스트")
    class RemoveCartItemsTest {

        @Test
        @DisplayName("특정 상품들을 장바구니에서 삭제할 수 있다")
        void removeCartItems_success() {
            // given
            Long userId = 1L;
            List<Long> productIds = List.of(1L, 2L, 3L);
            doNothing().when(cartItemRepository).deleteAllByUserIdAndProductIdIn(userId, productIds);

            // when
            cartService.removeCartItems(userId, productIds);

            // then
            verify(cartItemRepository).deleteAllByUserIdAndProductIdIn(userId, productIds);
        }
    }

    @Nested
    @DisplayName("특정 장바구니 아이템 조회 테스트")
    class GetCartItemTest {

        @Test
        @DisplayName("특정 장바구니 아이템을 조회할 수 있다")
        void getCartItem_success() {
            // given
            Long cartItemId = 1L;
            CartItem cartItem = CartItem.builder()
                    .id(cartItemId)
                    .userId(1L)
                    .productId(1L)
                    .quantity(5)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.of(cartItem));

            // when
            CartItem result = cartService.getCartItem(cartItemId);

            // then
            assertThat(result.getId()).isEqualTo(cartItemId);
        }

        @Test
        @DisplayName("장바구니 아이템이 존재하지 않으면 예외가 발생한다")
        void getCartItem_notFound() {
            // given
            Long cartItemId = 999L;
            given(cartItemRepository.findById(cartItemId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> cartService.getCartItem(cartItemId))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니 아이템을 찾을 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
    }
}
