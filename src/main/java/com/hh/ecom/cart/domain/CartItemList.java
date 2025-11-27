package com.hh.ecom.cart.domain;

import com.hh.ecom.cart.domain.exception.CartErrorCode;
import com.hh.ecom.cart.domain.exception.CartException;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.product.domain.Product;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CartItemList {
    private List<CartItem> cartItemList;

    public static CartItemList from(List<CartItem> cartItemList) {
        validateCartItemsExist(cartItemList);
        return new CartItemList(cartItemList);
    }

    private static void validateCartItemsExist(List<CartItem> findCartItems) {
        if (findCartItems.isEmpty()) {
            throw new CartException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
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

    public void validateEnoughStock(List<Product> products) {
        if (isEmptyCart()) return;

        mapCartItemsToProducts(products).forEach((item, product) -> {
            if (!product.hasEnoughStock(item.getQuantity())) {
                throw new OrderException(
                        OrderErrorCode.INVALID_ORDER_STATUS,
                        "상품 '%s'의 재고가 부족합니다. 요청: %d, 재고: %d".formatted(
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
