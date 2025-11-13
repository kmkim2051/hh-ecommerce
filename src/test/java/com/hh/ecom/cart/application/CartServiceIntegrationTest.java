package com.hh.ecom.cart.application;

import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemRepository;
import com.hh.ecom.cart.domain.exception.CartErrorCode;
import com.hh.ecom.cart.domain.exception.CartException;
import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("CartService-Repository 통합 테스트")
class CartServiceIntegrationTest extends TestContainersConfig {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long product1Id;
    private Long product2Id;
    private Long product3Id;

    @BeforeEach
    void setUp() {
        // 데이터 정리
        cartItemRepository.deleteAll();
        productRepository.deleteAll();

        // 테스트 상품 생성 (JPA를 통해 정상적으로 저장)
        Product product1 = Product.create("테스트 상품 1", "설명", BigDecimal.valueOf(10000), 100);
        product1 = productRepository.save(product1);
        product1Id = product1.getId();

        Product product2 = Product.create("테스트 상품 2", "설명", BigDecimal.valueOf(20000), 50);
        product2 = productRepository.save(product2);
        product2Id = product2.getId();

        Product product3 = Product.create("품절 상품", "설명", BigDecimal.valueOf(15000), 0);
        product3 = productRepository.save(product3);
        product3Id = product3.getId();
    }

    @Nested
    @DisplayName("장바구니 상품 추가 통합 테스트")
    class AddToCartIntegrationTest {

        @Test
        @DisplayName("새로운 상품을 장바구니에 추가하면 Repository에 저장된다")
        void addToCart_newProduct_savedInRepository() {
            // given
            Long userId = 1L;
            Long productId = product1Id;
            Integer quantity = 5;

            // when
            CartItem result = cartService.addToCart(userId, productId, quantity);

            // then
            assertThat(result.getId()).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getQuantity()).isEqualTo(quantity);

            // Repository에서 조회 가능한지 확인
            CartItem saved = cartItemRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getQuantity()).isEqualTo(quantity);
        }

        @Test
        @DisplayName("동일 상품을 다시 추가하면 수량이 증가한다")
        void addToCart_sameProduct_quantityIncreased() {
            // given
            Long userId = 1L;
            Long productId = product1Id;
            Integer firstQuantity = 5;
            Integer secondQuantity = 3;

            // when
            CartItem first = cartService.addToCart(userId, productId, firstQuantity);
            CartItem second = cartService.addToCart(userId, productId, secondQuantity);

            // then
            assertThat(second.getQuantity()).isEqualTo(8);
            assertThat(second.getId()).isEqualTo(first.getId()); // 같은 아이템

            // Repository에 하나만 존재하는지 확인
            List<CartItem> cartItems = cartItemRepository.findAllByUserId(userId);
            assertThat(cartItems).hasSize(1);
            assertThat(cartItems.get(0).getQuantity()).isEqualTo(8);
        }

        @Test
        @DisplayName("여러 상품을 장바구니에 추가할 수 있다")
        void addToCart_multipleProducts() {
            // given
            Long userId = 1L;

            // when
            cartService.addToCart(userId, product1Id, 5);
            cartService.addToCart(userId, product2Id, 3);

            // then
            List<CartItem> cartItems = cartItemRepository.findAllByUserId(userId);
            assertThat(cartItems).hasSize(2);
            assertThat(cartItems).extracting(CartItem::getProductId).containsExactlyInAnyOrder(product1Id, product2Id);
        }

        @Test
        @DisplayName("품절 상품은 장바구니에 추가할 수 없다")
        void addToCart_outOfStock_throwsException() {
            // given
            Long userId = 1L;
            Long productId = product3Id; // 품절 상품 (재고 0)

            // when & then
            // 재고가 0인 상품은 isAvailableForSale() 체크에서 실패하여 ProductException 발생
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, 1))
                    .isInstanceOf(ProductException.class)
                    .hasMessageContaining("판매 불가능한 상품입니다")
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_AVAILABLE_FOR_SALE);
        }

        @Test
        @DisplayName("재고를 초과하는 수량은 장바구니에 추가할 수 없다")
        void addToCart_exceedsStock_throwsException() {
            // given
            Long userId = 1L;
            Long productId = product2Id; // 재고 50개
            Integer quantity = 100;

            // when & then
            assertThatThrownBy(() -> cartService.addToCart(userId, productId, quantity))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("재고 수량을 초과할 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }
    }

    @Nested
    @DisplayName("장바구니 수량 변경 통합 테스트")
    class UpdateCartItemQuantityIntegrationTest {

        @Test
        @DisplayName("장바구니 수량을 변경하면 Repository에 반영된다")
        void updateCartItemQuantity_updatedInRepository() {
            // given
            Long userId = 1L;
            Long productId = product1Id;
            CartItem cartItem = cartService.addToCart(userId, productId, 5);

            // when
            CartItem updated = cartService.updateCartItemQuantity(cartItem.getId(), userId, 10);

            // then
            assertThat(updated.getQuantity()).isEqualTo(10);

            // Repository에서 조회하여 확인
            CartItem saved = cartItemRepository.findById(cartItem.getId()).orElseThrow();
            assertThat(saved.getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("다른 사용자의 장바구니 아이템은 수정할 수 없다")
        void updateCartItemQuantity_unauthorized_throwsException() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long productId = product1Id;
            CartItem cartItem = cartService.addToCart(userId, productId, 5);

            // when & then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItem.getId(), otherUserId, 10))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니에 접근할 권한이 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.UNAUTHORIZED_CART_ACCESS);
        }

        @Test
        @DisplayName("재고를 초과하는 수량으로 변경할 수 없다")
        void updateCartItemQuantity_exceedsStock_throwsException() {
            // given
            Long userId = 1L;
            Long productId = product2Id; // 재고 50개
            CartItem cartItem = cartService.addToCart(userId, productId, 5);

            // when & then
            assertThatThrownBy(() -> cartService.updateCartItemQuantity(cartItem.getId(), userId, 100))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("재고 수량을 초과할 수 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
        }
    }

    @Nested
    @DisplayName("장바구니 상품 삭제 통합 테스트")
    class RemoveCartItemIntegrationTest {

        @Test
        @DisplayName("장바구니 상품을 삭제하면 Repository에서도 제거된다")
        void removeCartItem_removedFromRepository() {
            // given
            Long userId = 1L;
            Long productId = product1Id;
            CartItem cartItem = cartService.addToCart(userId, productId, 5);

            // when
            cartService.removeCartItem(cartItem.getId(), userId);

            // then
            assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
            assertThat(cartItemRepository.findAllByUserId(userId)).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 장바구니 아이템은 삭제할 수 없다")
        void removeCartItem_unauthorized_throwsException() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long productId = product1Id;
            CartItem cartItem = cartService.addToCart(userId, productId, 5);

            // when & then
            assertThatThrownBy(() -> cartService.removeCartItem(cartItem.getId(), otherUserId))
                    .isInstanceOf(CartException.class)
                    .hasMessageContaining("장바구니에 접근할 권한이 없습니다")
                    .extracting("errorCode")
                    .isEqualTo(CartErrorCode.UNAUTHORIZED_CART_ACCESS);

            // 삭제되지 않았는지 확인
            assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("장바구니 조회 통합 테스트")
    class GetCartItemsIntegrationTest {

        @Test
        @DisplayName("사용자의 모든 장바구니 아이템을 조회할 수 있다")
        void getCartItems_returnsAllUserCartItems() {
            // given
            Long userId = 1L;
            cartService.addToCart(userId, product1Id, 5);
            cartService.addToCart(userId, product2Id, 3);

            // when
            List<CartItem> cartItems = cartService.getCartItems(userId);

            // then
            assertThat(cartItems).hasSize(2);
            assertThat(cartItems).extracting(CartItem::getProductId).containsExactlyInAnyOrder(product1Id, product2Id);
        }

        @Test
        @DisplayName("다른 사용자의 장바구니 아이템은 조회되지 않는다")
        void getCartItems_onlyReturnsOwnCartItems() {
            // given
            Long userId1 = 1L;
            Long userId2 = 2L;
            cartService.addToCart(userId1, product1Id, 5);
            cartService.addToCart(userId2, product2Id, 3);

            // when
            List<CartItem> user1CartItems = cartService.getCartItems(userId1);
            List<CartItem> user2CartItems = cartService.getCartItems(userId2);

            // then
            assertThat(user1CartItems).hasSize(1);
            assertThat(user1CartItems.get(0).getProductId()).isEqualTo(product1Id);

            assertThat(user2CartItems).hasSize(1);
            assertThat(user2CartItems.get(0).getProductId()).isEqualTo(product2Id);
        }

        @Test
        @DisplayName("장바구니가 비어있으면 빈 리스트를 반환한다")
        void getCartItems_emptyCart_returnsEmptyList() {
            // given
            Long userId = 1L;

            // when
            List<CartItem> cartItems = cartService.getCartItems(userId);

            // then
            assertThat(cartItems).isEmpty();
        }
    }

    @Nested
    @DisplayName("장바구니 비우기 통합 테스트")
    class ClearCartIntegrationTest {

        @Test
        @DisplayName("장바구니를 비우면 모든 아이템이 삭제된다")
        void clearCart_removesAllItems() {
            // given
            Long userId = 1L;
            cartService.addToCart(userId, product1Id, 5);
            cartService.addToCart(userId, product2Id, 3);
            assertThat(cartItemRepository.findAllByUserId(userId)).hasSize(2);

            // when
            cartService.clearCart(userId);

            // then
            assertThat(cartItemRepository.findAllByUserId(userId)).isEmpty();
        }

        @Test
        @DisplayName("다른 사용자의 장바구니는 영향받지 않는다")
        void clearCart_doesNotAffectOtherUsers() {
            // given
            Long userId1 = 1L;
            Long userId2 = 2L;
            cartService.addToCart(userId1, product1Id, 5);
            cartService.addToCart(userId2, product2Id, 3);

            // when
            cartService.clearCart(userId1);

            // then
            assertThat(cartItemRepository.findAllByUserId(userId1)).isEmpty();
            assertThat(cartItemRepository.findAllByUserId(userId2)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("특정 상품들 삭제 통합 테스트")
    class RemoveCartItemsIntegrationTest {

        @Test
        @DisplayName("특정 상품들만 장바구니에서 삭제된다")
        void removeCartItems_removesSpecificProducts() {
            // given
            Long userId = 1L;
            cartService.addToCart(userId, product1Id, 5);
            cartService.addToCart(userId, product2Id, 3);
            List<Long> productIdsToRemove = List.of(product1Id);

            // when
            cartService.removeCartItems(userId, productIdsToRemove);

            // then
            List<CartItem> remainingItems = cartItemRepository.findAllByUserId(userId);
            assertThat(remainingItems).hasSize(1);
            assertThat(remainingItems.get(0).getProductId()).isEqualTo(product2Id);
        }

        @Test
        @DisplayName("여러 상품을 한 번에 삭제할 수 있다")
        void removeCartItems_removesMultipleProducts() {
            // given
            Long userId = 1L;
            cartService.addToCart(userId, product1Id, 5);
            cartService.addToCart(userId, product2Id, 3);
            List<Long> productIdsToRemove = List.of(product1Id, product2Id);

            // when
            cartService.removeCartItems(userId, productIdsToRemove);

            // then
            assertThat(cartItemRepository.findAllByUserId(userId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("복합 시나리오 통합 테스트")
    class ComplexScenarioIntegrationTest {

        @Test
        @DisplayName("장바구니 추가 -> 수량 변경 -> 삭제 시나리오")
        void fullLifecycleScenario() {
            // given
            Long userId = 1L;
            Long productId = product1Id;

            // when - 추가
            CartItem added = cartService.addToCart(userId, productId, 5);
            assertThat(added.getQuantity()).isEqualTo(5);

            // when - 수량 변경
            CartItem updated = cartService.updateCartItemQuantity(added.getId(), userId, 10);
            assertThat(updated.getQuantity()).isEqualTo(10);

            // when - 삭제
            cartService.removeCartItem(added.getId(), userId);
            assertThat(cartItemRepository.findById(added.getId())).isEmpty();
        }

        @Test
        @DisplayName("여러 사용자가 동시에 장바구니를 사용할 수 있다")
        void multiUserScenario() {
            // given
            Long user1 = 1L;
            Long user2 = 2L;

            // when
            cartService.addToCart(user1, product1Id, 5);
            cartService.addToCart(user2, product1Id, 3);
            cartService.addToCart(user1, product2Id, 2);

            // then
            List<CartItem> user1Cart = cartService.getCartItems(user1);
            List<CartItem> user2Cart = cartService.getCartItems(user2);

            assertThat(user1Cart).hasSize(2);
            assertThat(user2Cart).hasSize(1);

            assertThat(user1Cart).extracting(CartItem::getUserId).containsOnly(user1);
            assertThat(user2Cart).extracting(CartItem::getUserId).containsOnly(user2);
        }
    }
}
