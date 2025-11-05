package com.hh.ecom.product.infrastructure.persistence;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductInMemoryRepository implements ProductRepository {
    private final Map<Long, ProductEntity> products = new ConcurrentHashMap<>();

    @Override
    public List<Product> findAll() {
        return products.values().stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id))
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findTopByViewCount(Integer limit) {
        // todo: step 6
        return null;
    }

    @Override
    public List<Product> findTopBySalesCount(Integer limit) {
        // todo: step 6
        return null;
    }
}
