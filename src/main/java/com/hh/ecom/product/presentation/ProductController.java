package com.hh.ecom.product.presentation;

import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.presentation.api.ProductApi;
import com.hh.ecom.product.presentation.dto.response.ProductListResponse;
import com.hh.ecom.product.presentation.dto.response.ProductResponse;
import com.hh.ecom.product.presentation.dto.response.ProductStockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController implements ProductApi {

    private final ProductService productService;

    @Override
    @GetMapping
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductList(pageable);
        return ResponseEntity.ok(ProductListResponse.from(products));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    @Override
    @GetMapping("/{id}/stock")
    public ResponseEntity<ProductStockResponse> getProductStock(@PathVariable Long id) {
        Product product = productService.getProductStock(id);
        return ResponseEntity.ok(ProductStockResponse.from(product));
    }

    @Override
    @GetMapping("/ranking/views")
    public ResponseEntity<ProductListResponse> getProductsByViewCount(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getProductsByViewCount(limit);
        return ResponseEntity.ok(ProductListResponse.from(products));
    }

    @Override
    @GetMapping("/ranking/sales")
    public ResponseEntity<ProductListResponse> getProductsBySalesCount(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getProductsBySalesCount(limit);
        return ResponseEntity.ok(ProductListResponse.from(products));
    }
}