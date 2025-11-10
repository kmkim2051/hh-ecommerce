package com.hh.ecom.cart.domain;

import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.product.domain.Product;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CartItemList {
    private List<CartItem> cartItemList;

    public static CartItemList from(List<CartItem> cartItemList) {
        return new CartItemList(cartItemList);
    }

    public void validateCartItemOwnership(Long userId) {
        if (isEmptyCart()) {
            return;
        }
        cartItemList.stream()
                .filter(item -> !item.belongsToUser(userId))
                .findAny()
                .ifPresent(item -> {
                    throw new OrderException(
                            OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS,
                            "장바구니 아이템이 다른 사용자의 것입니다. itemId=" + item.getId()
                    );
                });
    }

    // 공통적으로 productId → Product 매핑 및 존재여부 검증을 수행
    private Map<CartItem, Product> mapCartItemsToProducts(List<Product> products) {
        Map<Long, Product> productIdMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return cartItemList.stream()
                .collect(Collectors.toMap(Function.identity(), item -> {
                    Product product = productIdMap.get(item.getProductId());
                    if (product == null) {
                        throw new OrderException(OrderErrorCode.PRODUCT_IN_ORDER_NOT_FOUND, "상품을 찾을 수 없습니다. id=" + item.getProductId());
                    }
                    return product;
                }));
    }

    public void validateStockAvailability(List<Product> products) {
        if (isEmptyCart()) return;

        mapCartItemsToProducts(products).forEach((item, product) -> {
            if (!product.hasEnoughStock(item.getQuantity())) {
                throw new OrderException(
                        OrderErrorCode.INVALID_ORDER_STATUS,
                        String.format("상품 '%s'의 재고가 부족합니다. 요청: %d, 재고: %d",
                                product.getName(), item.getQuantity(), product.getStockQuantity())
                );
            }
        });
    }

    public BigDecimal calculateTotalPrice(List<Product> products) {
        if (isEmptyCart() || products == null || products.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return mapCartItemsToProducts(products).entrySet().stream()
                .map(entry -> {
                    CartItem item = entry.getKey();
                    Product product = entry.getValue();

                    BigDecimal price = Optional.ofNullable(product.getPrice())
                            .orElse(BigDecimal.ZERO);
                    int quantity = Optional.ofNullable(item.getQuantity())
                            .orElse(0);

                    if (quantity <= 0) {
                        return BigDecimal.ZERO;
                    }

                    return price.multiply(BigDecimal.valueOf(quantity));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Long> getProductIdList() {
        if (isEmptyCart()) {
            return List.of();
        }
        return cartItemList.stream().map(CartItem::getProductId).toList();
    }

    public boolean isEmptyCart() {
        return cartItemList == null || cartItemList.isEmpty();
    }
}
