package com.hh.ecom.product.infrastructure.persistence;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductInMemoryRepository implements ProductRepository {
    private final Map<Long, ProductEntity> products = new ConcurrentHashMap<>();

    @Override
    public Page<Product> findAll(Pageable pageable) {
        List<Product> allProducts = products.values().stream()
                .map(ProductEntity::toDomain)
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allProducts.size());

        List<Product> pageContent = (start <= allProducts.size())
                ? allProducts.subList(start, end)
                : List.of();

        return new PageImpl<>(pageContent, pageable, allProducts.size());
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
