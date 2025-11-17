package com.hh.ecom.product.presentation.dto.response;

import com.hh.ecom.product.domain.Product;

import java.time.LocalDateTime;

public record ProductStockResponse(
        Long productId,
        Integer stockQuantity,
        LocalDateTime updatedAt
) {
    public static ProductStockResponse from(Product product) {
        return new ProductStockResponse(
                product.getId(),
                product.getStockQuantity(),
                product.getUpdatedAt()
        );
    }
}
