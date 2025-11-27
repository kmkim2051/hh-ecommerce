package com.hh.ecom.product.infrastructure.persistence.inmemory;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
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
    public List<Product> findByIdsIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .map(products::get)
                .filter(Objects::nonNull)
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByIdsInForUpdate(List<Long> ids) {
        return findByIdsIn(ids);
    }

    @Override
    public List<Product> findTopByViewCount(Integer limit) {
        if (limit == null || limit <= 0) {
            return List.of();
        }

        return products.values().stream()
                .filter(Objects::nonNull)
                .map(ProductEntity::toDomain)
                .filter(product -> product.getViewCount() != null)
                .sorted(Comparator.comparingInt((Product p) -> p.getViewCount())
                        .reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<Product> findTopBySalesCount(Integer limit) {
        return List.of();
    }

    @Override
    public List<Product> findTopByViewCountInRecentDays(Integer days, Integer limit) {
        return findTopByViewCount(limit);
    }

    @Override
    public List<Product> findTopBySalesCountInRecentDays(Integer days, Integer limit) {
        return List.of();
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = ProductEntity.from(product);
        products.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public void deleteAll() {
        products.clear();
    }

}
