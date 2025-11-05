package com.hh.ecom.product.application;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    /**
     * #### FR-PR-001: 상품 목록 조회
     * - **설명**: 사용자가 판매 중인 상품 목록을 조회할 수 있다
     * - **입력**: 페이지 정보 (선택)
     * - **출력**: 상품 목록
     *
     * #### FR-PR-002: 상품 상세 조회
     * - **설명**: 사용자가 특정 상품의 상세 정보를 조회할 수 있다
     * - **입력**: 상품 ID
     * - **출력**: 상품 상세 정보
     *
     * #### FR-PR-003: 실시간 재고 조회
     * - **설명**: 사용자가 특정 상품의 현재 재고를 실시간으로 조회할 수 있다
     * - **입력**: 상품 ID
     * - **출력**: 현재 재고 수량
     * */

    private final ProductRepository productRepository;

    public Page<Product> getProductList(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Product getProduct(Long id) {
        // todo: custom exception
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    public Product getProductStock(Long id) {
        // todo: custom exception
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    public List<Product> getProductsByViewCount(Integer limit) {
        return productRepository.findTopByViewCount(limit);
    }

    public List<Product> getProductsBySalesCount(Integer limit) {
        return productRepository.findTopBySalesCount(limit);
    }
}
