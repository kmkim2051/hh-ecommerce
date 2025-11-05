package com.hh.ecom.product.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Product {
    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final Integer stockQuantity;
    private final Integer viewCount;

    private final Boolean isActive;
    private final LocalDateTime deletedAt;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static Product create(String name, String description, BigDecimal price, Integer stockQuantity) {
        LocalDateTime now = LocalDateTime.now();
        return Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .stockQuantity(stockQuantity)
                .viewCount(0)
                .isActive(true)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Product withUpdate(ProductBuilder builder) {
        return builder.updatedAt(LocalDateTime.now()).build();
    }

    public boolean isAvailableForSale() {
        return Boolean.TRUE.equals(this.isActive) && this.stockQuantity > 0;
    }

    public boolean hasEnoughStock(int requestedQuantity) {
        return this.stockQuantity >= requestedQuantity;
    }

    public Product decreaseStock(int quantity) {
        if (!hasEnoughStock(quantity)) {
            throw new IllegalArgumentException("재고가 부족합니다. 현재 재고: " + this.stockQuantity);
        }
        return withUpdate(this.toBuilder()
                .stockQuantity(this.stockQuantity - quantity)
        );
    }

    public Product increaseViewCount() {
        return this.toBuilder()
                .viewCount(this.viewCount + 1)
                .build();
    }

    public Product softDelete() {
        return withUpdate(this.toBuilder()
                .isActive(false)
                .deletedAt(LocalDateTime.now())
        );
    }

    public Product restore() {
        return withUpdate(this.toBuilder()
                .isActive(true)
                .deletedAt(null)
        );
    }
}
