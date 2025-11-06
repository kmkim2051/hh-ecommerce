package com.hh.ecom.cart.application;

import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemRepository;
import com.hh.ecom.cart.domain.exception.CartErrorCode;
import com.hh.ecom.cart.domain.exception.CartException;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {
    /**
     * #### FR-CA-001: 장바구니 상품 추가
     * - **설명**: 사용자가 상품을 장바구니에 추가할 수 있다
     * - **입력**: 사용자 ID, 상품 ID, 수량
     * - **출력**: 추가된 장바구니 아이템
     * - **비고**: 재고 수량 검증 필요
     *
     * #### FR-CA-002: 장바구니 수량 변경
     * - **설명**: 사용자가 장바구니 상품의 수량을 변경할 수 있다
     * - **입력**: 장바구니 아이템 ID, 변경할 수량
     * - **출력**: 변경된 장바구니 아이템
     * - **비고**: 재고 수량 검증 필요
     *
     * #### FR-CA-003: 장바구니 상품 삭제
     * - **설명**: 사용자가 장바구니에서 상품을 삭제할 수 있다
     * - **입력**: 장바구니 아이템 ID
     * - **출력**: 삭제 성공 여부
     *
     * #### FR-CA-004: 장바구니 조회
     * - **설명**: 사용자가 장바구니에 담긴 상품 목록을 조회할 수 있다
     * - **입력**: 사용자 ID
     * - **출력**: 장바구니 아이템 목록 (상품 정보, 수량)
     */

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * FR-CA-001: 장바구니 상품 추가
     * 동일 상품이 이미 장바구니에 있으면 수량을 증가시킵니다.
     */
    @Transactional
    public CartItem addToCart(Long userId, Long productId, Integer quantity) {
        // 1. 상품 조회 및 검증
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, "ID: " + productId));

        // 2. 상품 판매 가능 여부 확인
        if (!product.isAvailableForSale()) {
            throw new ProductException(ProductErrorCode.PRODUCT_NOT_AVAILABLE_FOR_SALE, "ID: " + productId);
        }

        // 3. 재고 확인
        if (!product.hasEnoughStock(quantity)) {
            throw new CartException(CartErrorCode.QUANTITY_EXCEEDS_STOCK,
                    "요청 수량: " + quantity + ", 현재 재고: " + product.getStockQuantity());
        }

        // 4. 기존 장바구니 아이템 확인
        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(userId, productId);

        if (existingItem.isPresent()) {
            // 기존 아이템이 있으면 수량 증가
            CartItem cartItem = existingItem.get();
            int newQuantity = cartItem.getQuantity() + quantity;

            // 증가된 수량도 재고 확인
            if (!product.hasEnoughStock(newQuantity)) {
                throw new CartException(CartErrorCode.QUANTITY_EXCEEDS_STOCK,
                        "요청 수량: " + newQuantity + ", 현재 재고: " + product.getStockQuantity());
            }

            CartItem updatedItem = cartItem.updateQuantity(newQuantity);
            return cartItemRepository.save(updatedItem);
        } else {
            // 새 아이템 추가
            CartItem newItem = CartItem.create(userId, productId, quantity);
            return cartItemRepository.save(newItem);
        }
    }

    /**
     * FR-CA-002: 장바구니 수량 변경
     */
    @Transactional
    public CartItem updateCartItemQuantity(Long cartItemId, Long userId, Integer newQuantity) {
        // 1. 장바구니 아이템 조회
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND, "ID: " + cartItemId));

        // 2. 권한 확인
        if (!cartItem.belongsToUser(userId)) {
            throw new CartException(CartErrorCode.UNAUTHORIZED_CART_ACCESS,
                    "User ID: " + userId + ", Cart Item User ID: " + cartItem.getUserId());
        }

        // 3. 상품 재고 확인
        Product product = productRepository.findById(cartItem.getProductId())
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND,
                        "ID: " + cartItem.getProductId()));

        if (!product.hasEnoughStock(newQuantity)) {
            throw new CartException(CartErrorCode.QUANTITY_EXCEEDS_STOCK,
                    "요청 수량: " + newQuantity + ", 현재 재고: " + product.getStockQuantity());
        }

        // 4. 수량 변경
        CartItem updatedItem = cartItem.updateQuantity(newQuantity);
        return cartItemRepository.save(updatedItem);
    }

    /**
     * FR-CA-003: 장바구니 상품 삭제
     */
    @Transactional
    public void removeCartItem(Long cartItemId, Long userId) {
        // 1. 장바구니 아이템 조회
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND, "ID: " + cartItemId));

        // 2. 권한 확인
        if (!cartItem.belongsToUser(userId)) {
            throw new CartException(CartErrorCode.UNAUTHORIZED_CART_ACCESS,
                    "User ID: " + userId + ", Cart Item User ID: " + cartItem.getUserId());
        }

        // 3. 삭제
        cartItemRepository.deleteById(cartItemId);
    }

    /**
     * FR-CA-004: 장바구니 조회
     */
    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findAllByUserId(userId);
    }

    /**
     * 장바구니 전체 비우기
     */
    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    /**
     * 특정 상품들을 장바구니에서 삭제 (주문 생성 시 사용)
     */
    @Transactional
    public void removeCartItems(Long userId, List<Long> productIds) {
        cartItemRepository.deleteAllByUserIdAndProductIdIn(userId, productIds);
    }

    /**
     * 특정 장바구니 아이템 조회 (주문 생성 시 사용)
     */
    @Transactional(readOnly = true)
    public CartItem getCartItem(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND, "ID: " + cartItemId));
    }
}
