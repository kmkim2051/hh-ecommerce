package com.hh.ecom.product.infrastructure.persistence.jpa;

import com.hh.ecom.order.domain.ProductSalesCount;
import com.hh.ecom.order.infrastructure.persistence.jpa.OrderItemJpaRepository;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.ViewCountRepository;
import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;
    private final ViewCountRepository viewCountRepository;

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productJpaRepository.findAll(pageable)
                .map(ProductEntity::toDomain);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id)
                .map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findByIdsIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return productJpaRepository.findByIdIn(ids).stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByIdsInForUpdate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return productJpaRepository.findByIdInForUpdate(ids).stream()
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public List<Product> findTopByViewCount(Integer limit) {
        if (limit == null || limit <= 0) {
            return List.of();
        }

        return productJpaRepository.findTopByViewCount(limit).stream()
                .limit(limit)
                .map(ProductEntity::toDomain)
                .toList();
    }

    @Override
    public List<Product> findTopBySalesCount(Integer limit) {
        if (limit == null || limit <= 0) {
            return List.of();
        }

        List<ProductSalesCount> salesCounts = orderItemJpaRepository
                .findTopProductsBySalesCount(limit).stream()
                .map(projection -> ProductSalesCount.of(
                        projection.getProductId(),
                        projection.getSalesCount()
                ))
                .toList();
        return getTopProductsInSalesCount(salesCounts);
    }

    @Override
    public List<Product> findTopBySalesCountInRecentDays(Integer days, Integer limit) {
        if (days == null || days <= 0 || limit == null || limit <= 0) {
            return List.of();
        }

        List<ProductSalesCount> salesCounts = orderItemJpaRepository
                .findTopProductsBySalesCountInRecentDays(days, limit).stream()
                .map(projection -> ProductSalesCount.of(
                        projection.getProductId(),
                        projection.getSalesCount()
                ))
                .toList();

        return getTopProductsInSalesCount(salesCounts);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = ProductEntity.from(product);
        ProductEntity saved = productJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<Product> findTopByViewCountInRecentDays(Integer days, Integer limit) {
        if (days == null || days <= 0 || limit == null || limit <= 0) {
            return List.of();
        }

        List<Long> topProductIds = viewCountRepository.getTopViewedProductIds(days, limit);
        return getProductsInSequence(topProductIds);
    }

    @Override
    public void deleteAll() {
        productJpaRepository.deleteAll();
    }

    private List<Product> getTopProductsInSalesCount(List<ProductSalesCount> salesCounts) {
        List<Long> topProductIds = salesCounts.stream()
                .map(ProductSalesCount::getProductId)
                .toList();

        return getProductsInSequence(topProductIds);
    }

    private List<Product> getProductsInSequence(List<Long> topProductIds) {
        if (topProductIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = findByIdsIn(topProductIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return topProductIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
