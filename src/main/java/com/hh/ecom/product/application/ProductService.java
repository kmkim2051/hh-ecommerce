package com.hh.ecom.product.application;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.ViewCountRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ViewCountRepository viewCountRepository;

    public Page<Product> getProductList(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        Product product = findProductById(id);
        viewCountRepository.incrementViewCount(id);
        return product;
    }

    public List<Product> getProductList(List<Long> ids) {
        return productRepository.findByIdsIn(ids);
    }

    public Product getProductStock(Long id) {
        return findProductById(id);
    }

    public List<Product> getProductsByViewCount(Integer limit) {
        return productRepository.findTopByViewCount(limit);
    }

    public List<Product> getProductsByViewCountInRecentDays(Integer days, Integer limit) {
        return productRepository.findTopByViewCountInRecentDays(days, limit);
    }

    public List<Product> getProductsBySalesCount(Integer limit) {
        return productRepository.findTopBySalesCount(limit);
    }

    public List<Product> getProductsBySalesCountInRecentDays(Integer days, Integer limit) {
        return productRepository.findTopBySalesCountInRecentDays(days, limit);
    }

    @Transactional
    public void decreaseProductStock(Long productId, Integer quantity) {
        Product product = findProductById(productId);
        Product decreased = product.decreaseStock(quantity);
        productRepository.save(decreased);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, "ID: " + id));
    }
}
