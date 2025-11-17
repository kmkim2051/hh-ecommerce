package com.hh.ecom.product.presentation.dto.response;

import com.hh.ecom.product.domain.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProductListResponse(
        List<ProductResponse> products,
        Integer totalCount,
        Integer currentPage,
        Integer pageSize,
        Integer totalPages
) {
    public static ProductListResponse from(Page<Product> productPage) {
        List<ProductResponse> products = productPage.getContent().stream()
                .map(ProductResponse::from)
                .toList();

        return new ProductListResponse(
                products,
                (int) productPage.getTotalElements(),
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalPages()
        );
    }

    public static ProductListResponse from(List<Product> productList) {
        List<ProductResponse> products = productList.stream()
                .map(ProductResponse::from)
                .toList();

        return new ProductListResponse(
                products,
                products.size(),
                0,
                products.size(),
                1
        );
    }
}
