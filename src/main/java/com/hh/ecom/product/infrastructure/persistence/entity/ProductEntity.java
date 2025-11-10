package com.hh.ecom.product.infrastructure.persistence.entity;

import com.hh.ecom.product.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 * */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private Integer viewCount;

    // Soft Delete
    private Boolean isActive;
    private LocalDateTime deletedAt;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product toDomain() {
        return Product.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .price(this.price)
                .stockQuantity(this.stockQuantity)
                .viewCount(this.viewCount)
                .isActive(this.isActive)
                .deletedAt(this.deletedAt)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static ProductEntity from(Product product) {
        return ProductEntity.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .viewCount(product.getViewCount())
                .isActive(product.getIsActive())
                .deletedAt(product.getDeletedAt())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
