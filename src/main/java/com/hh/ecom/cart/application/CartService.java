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

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CartItem addToCart(Long userId, Long productId, Integer quantity) {
        Product product = findProductById(productId);

        if (!product.isAvailableForSale()) {
            throw new ProductException(ProductErrorCode.PRODUCT_NOT_AVAILABLE_FOR_SALE, "ID: " + productId);
        }

        if (!product.hasEnoughStock(quantity)) {
            throw new CartException(CartErrorCode.QUANTITY_EXCEEDS_STOCK,
                    "요청 수량: " + quantity + ", 현재 재고: " + product.getStockQuantity());
        }

        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(existCartItem -> {
                    int newQuantity = existCartItem.getQuantity() + quantity;

                    if (!product.hasEnoughStock(newQuantity)) {
                        throw new CartException(CartErrorCode.QUANTITY_EXCEEDS_STOCK);
                    }

                    return cartItemRepository.save(existCartItem.updateQuantity(newQuantity));
                })
                .orElseGet(() -> {
                    CartItem newItem = CartItem.create(userId, productId, quantity);
                    return cartItemRepository.save(newItem);
                });
    }

    @Transactional
    public CartItem updateCartItemQuantity(Long cartItemId, Long userId, Integer newQuantity) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND, "ID: " + cartItemId));

        if (!cartItem.belongsToUser(userId)) {
            throw new CartException(CartErrorCode.UNAUTHORIZED_CART_ACCESS,
                    "User ID: " + userId + ", Cart Item User ID: " + cartItem.getUserId());
        }

        Product product = findProductById(cartItem.getProductId());

        if (!product.hasEnoughStock(newQuantity)) {
            throw new CartException(CartErrorCode.QUANTITY_EXCEEDS_STOCK,
                    "요청 수량: " + newQuantity + ", 현재 재고: " + product.getStockQuantity());
        }

        CartItem updatedItem = cartItem.updateQuantity(newQuantity);
        return cartItemRepository.save(updatedItem);
    }

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
        cartItemRepository.deleteById(cartItemId);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findAllByUserId(userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void removeCartItems(Long userId, List<Long> productIds) {
        cartItemRepository.deleteAllByUserIdAndProductIdIn(userId, productIds);
    }

    @Transactional(readOnly = true)
    public CartItem getCartItemById(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND, "ID: " + cartItemId));
    }

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, "ID: " + productId));
    }
}
