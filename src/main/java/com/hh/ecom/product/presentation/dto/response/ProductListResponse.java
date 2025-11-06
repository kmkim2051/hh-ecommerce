package com.hh.ecom.product.presentation.dto.response;

import com.hh.ecom.product.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListResponse {
    private List<ProductResponse> products;
    private Integer totalCount;
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalPages;

    public static ProductListResponse from(Page<Product> productPage) {
        List<ProductResponse> products = productPage.getContent().stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        return ProductListResponse.builder()
                .products(products)
                .totalCount((int) productPage.getTotalElements())
                .currentPage(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalPages(productPage.getTotalPages())
                .build();
    }

    public static ProductListResponse from(List<Product> productList) {
        List<ProductResponse> products = productList.stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());

        return ProductListResponse.builder()
                .products(products)
                .totalCount(products.size())
                .currentPage(0)
                .pageSize(products.size())
                .totalPages(1)
                .build();
    }
}
