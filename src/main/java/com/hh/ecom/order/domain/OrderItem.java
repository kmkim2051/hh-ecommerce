package com.hh.ecom.order.domain;

import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.product.domain.Product;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderItem {
    private final Long id;
    private final Long orderId;
    private final Long productId;
    private final String productName;  // 주문 시점의 상품명 (스냅샷)
    private final BigDecimal price;    // 주문 시점의 상품 가격 (스냅샷)
    private final Integer quantity;
    private final OrderItemStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static OrderItem create(
            Long orderId,
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity
    ) {
        validateCreateParams(productId, productName, price, quantity);

        LocalDateTime now = LocalDateTime.now();
        return OrderItem.builder()
                .orderId(orderId)
                .productId(productId)
                .productName(productName)
                .price(price)
                .quantity(quantity)
                .status(OrderItemStatus.NORMAL)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static OrderItem fromCartItem(CartItem cartItem, Product product, Long orderId) {
        if (cartItem == null) {
            throw new OrderException(OrderErrorCode.ORDER_ITEM_NOT_FOUND, "장바구니 항목이 비어있습니다.");
        }
        if (product == null) {
            throw new OrderException(OrderErrorCode.PRODUCT_IN_ORDER_NOT_FOUND, "상품을 찾을 수 없습니다. productId=" + cartItem.getProductId());
        }

        return OrderItem.create(
                orderId,
                product.getId(),
                product.getName(),
                product.getPrice(),
                cartItem.getQuantity()
        );
    }

    private static void validateCreateParams(
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity
    ) {
        if (productId == null) {
            throw new OrderException(OrderErrorCode.ORDER_ITEM_NOT_FOUND, "상품 ID는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new OrderException(OrderErrorCode.ORDER_ITEM_NOT_FOUND, "상품명은 필수입니다.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_AMOUNT, "상품 가격은 0 이상이어야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_AMOUNT, "수량은 1 이상이어야 합니다.");
        }
    }

    public BigDecimal getItemTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public OrderItem cancel() {
        if (status == OrderItemStatus.CANCELED) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "이미 취소된 주문 아이템입니다.");
        }

        return this.toBuilder()
                .status(OrderItemStatus.CANCELED)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public boolean isCanceled() {
        return status == OrderItemStatus.CANCELED;
    }

    public boolean isNormal() {
        return status == OrderItemStatus.NORMAL;
    }

    public OrderItem withId(Long id) {
        return this.toBuilder()
                .id(id)
                .build();
    }
}
