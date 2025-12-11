package com.hh.ecom.product.application;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.ViewCountRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 서비스
 * - 상품 조회 및 재고 관리만 담당
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {
    @Value("${ranking.max-period-days:365")
    private Integer MAX_PERIOD_DAYS;

    private final ProductRepository productRepository;
    private final ViewCountRepository viewCountRepository;
    private final SalesRankingRepository salesRankingRepository;

    public Page<Product> getProductList(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

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

    @Transactional
    public void decreaseProductStock(Long productId, Integer quantity) {
        Product product = findProductById(productId);
        Product decreased = product.decreaseStock(quantity);
        productRepository.save(decreased);
    }

    public List<Product> getTopBySalesCount(int limit) {
        return salesRankingRepository.getTopBySalesCount(limit);
    }

    public List<Product> getTopBySalesCountInRecentDays(int days, int limit) {
        validateRankingViewPeriod(days);
        return salesRankingRepository.getTopBySalesCountInRecentDays(days, limit);
    }
    public List<Product> getTopByViewCount(Integer limit) {
        return productRepository.findTopByViewCount(limit);
    }

    public List<Product> getTopByViewCountInRecentDays(Integer days, Integer limit) {
        validateRankingViewPeriod(days);
        return productRepository.findTopByViewCountInRecentDays(days, limit);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND, "ID: " + id));
    }


    private void validateRankingViewPeriod(int days) {
        if (days <= 0 || days > MAX_PERIOD_DAYS) {
            throw new ProductException(ProductErrorCode.INVALID_RANKING_PERIOD);
        }
    }

}
