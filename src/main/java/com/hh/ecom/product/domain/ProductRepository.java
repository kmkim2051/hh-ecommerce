package com.hh.ecom.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Page<Product> findAll(Pageable pageable);
    Optional<Product> findById(Long id);
    List<Product> findByIdsIn(List<Long> ids);
    List<Product> findByIdsInForUpdate(List<Long> ids);
    List<Product> findTopByViewCount(Integer limit);

    /**
     * 판매량 기준 상위 상품 조회
     * COMPLETED 상태 주문의 판매 수량 기준으로 정렬
     *
     * @param limit 조회할 상위 상품 개수
     * @return 판매량 내림차순 정렬된 상품 리스트
     */
    List<Product> findTopBySalesCount(Integer limit);

    // for testing
    Product save(Product product);
    void deleteAll();
}
