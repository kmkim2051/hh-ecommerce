package com.hh.ecom.cart.presentation;

import com.hh.ecom.cart.presentation.api.CartApi;
import com.hh.ecom.product.presentation.dto.request.CreateCartItemRequest;
import com.hh.ecom.product.presentation.dto.request.UpdateCartItemRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/cart-items")
public class CartController implements CartApi {

    @Override
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCartItems() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> cartItems = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1L);
        item1.put("userId", 1L);
        item1.put("productId", 1L);
        item1.put("productName", "노트북");
        item1.put("price", 1500000);
        item1.put("quantity", 1);
        cartItems.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2L);
        item2.put("userId", 1L);
        item2.put("productId", 2L);
        item2.put("productName", "무선 마우스");
        item2.put("price", 35000);
        item2.put("quantity", 2);
        cartItems.add(item2);

        int totalAmount = 1500000 + (35000 * 2);

        response.put("cartItems", cartItems);
        response.put("totalCount", cartItems.size());
        response.put("totalAmount", totalAmount);

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping
    public ResponseEntity<Map<String, Object>> addCartItem(@RequestBody CreateCartItemRequest request) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", System.currentTimeMillis() % 10000);
        response.put("userId", 1L);
        response.put("productId", request.productId());
        response.put("productName", "노트북");
        response.put("price", 1500000);
        response.put("quantity", request.quantity());
        response.put("message", "장바구니에 상품이 추가되었습니다.");

        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCartItemQuantity(
            @PathVariable Long id,
            @RequestBody UpdateCartItemRequest request) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", id);
        response.put("userId", 1L);
        response.put("productId", 1L);
        response.put("productName", "노트북");
        response.put("price", 1500000);
        response.put("quantity", request.quantity());
        response.put("message", "장바구니 수량이 변경되었습니다.");

        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCartItem(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", id);
        response.put("message", "장바구니에서 상품이 삭제되었습니다.");

        return ResponseEntity.ok(response);


    }
}