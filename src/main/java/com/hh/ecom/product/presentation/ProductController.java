package com.hh.ecom.product.presentation;

import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.presentation.api.ProductApi;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<Product>> getProducts() {
        List<Product> products = productService.getProductList();
        return ResponseEntity.ok(products);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    @Override
    @GetMapping("/{id}/stock")
    public ResponseEntity<Product> getProductStock(@PathVariable Long id) {
        Product product = productService.getProductStock(id);
        return ResponseEntity.ok(product);
    }

    @Override
    @GetMapping("/ranking/views")
    public ResponseEntity<List<Product>> getProductsByViewCount(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getProductsByViewCount(limit);
        return ResponseEntity.ok(products);
    }

    @Override
    @GetMapping("/ranking/sales")
    public ResponseEntity<List<Product>> getProductsBySalesCount(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<Product> products = productService.getProductsBySalesCount(limit);
        return ResponseEntity.ok(products);
    }
}