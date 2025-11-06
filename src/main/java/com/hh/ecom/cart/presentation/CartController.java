package com.hh.ecom.cart.presentation;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.presentation.api.CartApi;
import com.hh.ecom.cart.presentation.dto.CartItemListResponse;
import com.hh.ecom.cart.presentation.dto.CartItemMessageResponse;
import com.hh.ecom.product.presentation.dto.request.CreateCartItemRequest;
import com.hh.ecom.product.presentation.dto.request.UpdateCartItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart-items")
@RequiredArgsConstructor
public class CartController implements CartApi {

    private final CartService cartService;

    /**
     * FR-CA-004: 장바구니 조회
     */
    @Override
    @GetMapping
    public ResponseEntity<CartItemListResponse> getCartItems(
            @RequestHeader("userId") Long userId
    ) {
        List<CartItem> cartItems = cartService.getCartItems(userId);
        return ResponseEntity.ok(CartItemListResponse.from(cartItems));
    }

    /**
     * FR-CA-001: 장바구니 상품 추가
     */
    @Override
    @PostMapping
    public ResponseEntity<CartItemMessageResponse> addCartItem(
            @RequestHeader("userId") Long userId,
            @RequestBody CreateCartItemRequest request
    ) {
        CartItem cartItem = cartService.addToCart(userId, request.productId(), request.quantity());
        return ResponseEntity.ok(
                CartItemMessageResponse.of(cartItem, "장바구니에 상품이 추가되었습니다.")
        );
    }

    /**
     * FR-CA-002: 장바구니 수량 변경
     */
    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<CartItemMessageResponse> updateCartItemQuantity(
            @RequestHeader("userId") Long userId,
            @PathVariable Long id,
            @RequestBody UpdateCartItemRequest request
    ) {
        CartItem cartItem = cartService.updateCartItemQuantity(id, userId, request.quantity());
        return ResponseEntity.ok(
                CartItemMessageResponse.of(cartItem, "장바구니 수량이 변경되었습니다.")
        );
    }

    /**
     * FR-CA-003: 장바구니 상품 삭제
     */
    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<CartItemMessageResponse> deleteCartItem(
            @RequestHeader("userId") Long userId,
            @PathVariable Long id
    ) {
        cartService.removeCartItem(id, userId);
        return ResponseEntity.ok(
                CartItemMessageResponse.ofDeleted(id, "장바구니에서 상품이 삭제되었습니다.")
        );
    }
}