package com.hh.ecom.product.application;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public Page<Product> getProductList(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, "ID: " + id));
    }

    public List<Product> getProductList(List<Long> ids) {
        return productRepository.findByIdsIn(ids);
    }

    public Product getProductStock(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, "ID: " + id));
    }

    public List<Product> getProductsByViewCount(Integer limit) {
        return productRepository.findTopByViewCount(limit);
    }

    public List<Product> getProductsBySalesCount(Integer limit) {
        return productRepository.findTopBySalesCount(limit);
    }
}
