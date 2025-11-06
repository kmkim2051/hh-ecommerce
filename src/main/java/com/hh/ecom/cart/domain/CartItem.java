package com.hh.ecom.cart.domain;

import com.hh.ecom.cart.domain.exception.CartErrorCode;
import com.hh.ecom.cart.domain.exception.CartException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CartItem {
    private final Long id;
    private final Long userId;
    private final Long productId;
    private final Integer quantity;
    private final LocalDateTime createdAt;

    public static CartItem create(Long userId, Long productId, Integer quantity) {
        validateUserId(userId);
        validateProductId(productId);
        validateQuantity(quantity);

        return CartItem.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public CartItem updateQuantity(Integer newQuantity) {
        validateQuantity(newQuantity);
        return this.toBuilder()
                .quantity(newQuantity)
                .build();
    }

    public CartItem increaseQuantity(Integer additionalQuantity) {
        if (additionalQuantity == null || additionalQuantity <= 0) {
            throw new CartException(CartErrorCode.INVALID_QUANTITY, "추가 수량: " + additionalQuantity);
        }
        return updateQuantity(this.quantity + additionalQuantity);
    }

    public CartItem decreaseQuantity(Integer decreaseAmount) {
        if (decreaseAmount == null || decreaseAmount <= 0) {
            throw new CartException(CartErrorCode.INVALID_QUANTITY, "감소 수량: " + decreaseAmount);
        }
        int newQuantity = this.quantity - decreaseAmount;
        if (newQuantity < 1) {
            throw new CartException(CartErrorCode.INVALID_QUANTITY,
                    "현재 수량: " + this.quantity + ", 감소 수량: " + decreaseAmount);
        }
        return updateQuantity(newQuantity);
    }

    public boolean belongsToUser(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    public boolean isSameProduct(Long productId) {
        return this.productId.equals(productId);
    }

    private static void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new CartException(CartErrorCode.INVALID_USER_ID, "User ID: " + userId);
        }
    }

    private static void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new CartException(CartErrorCode.INVALID_PRODUCT_ID, "Product ID: " + productId);
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new CartException(CartErrorCode.INVALID_QUANTITY, "수량: " + quantity);
        }
    }
}
