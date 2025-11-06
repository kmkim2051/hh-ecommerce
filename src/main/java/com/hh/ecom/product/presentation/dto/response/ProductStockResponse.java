package com.hh.ecom.product.presentation.dto.response;

import com.hh.ecom.product.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockResponse {
    private Long productId;
    private Integer stockQuantity;
    private LocalDateTime updatedAt;

    public static ProductStockResponse from(Product product) {
        return ProductStockResponse.builder()
                .productId(product.getId())
                .stockQuantity(product.getStockQuantity())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
