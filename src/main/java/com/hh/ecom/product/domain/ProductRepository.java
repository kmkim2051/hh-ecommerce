package com.hh.ecom.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findAll();
    Optional<Product> findById(Long id);
    List<Product> findTopByViewCount(Integer limit);
    List<Product> findTopBySalesCount(Integer limit);
}
