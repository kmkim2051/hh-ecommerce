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
    List<Product> findTopBySalesCount(Integer limit);
    List<Product> findTopByViewCountInRecentDays(Integer days, Integer limit);
    List<Product> findTopBySalesCountInRecentDays(Integer days, Integer limit);

    void saveProductView(Long productId);

    // for testing
    Product save(Product product);
    void deleteAll();
}
