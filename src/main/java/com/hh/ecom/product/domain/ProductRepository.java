package com.hh.ecom.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Page<Product> findAll(Pageable pageable);
    Optional<Product> findById(Long id);
    List<Product> findTopByViewCount(Integer limit);
    List<Product> findTopBySalesCount(Integer limit);
}
