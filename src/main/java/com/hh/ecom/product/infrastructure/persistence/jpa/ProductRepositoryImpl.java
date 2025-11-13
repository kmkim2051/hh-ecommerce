package com.hh.ecom.product.infrastructure.persistence.jpa;

import com.hh.ecom.order.domain.ProductSalesCount;
import com.hh.ecom.order.infrastructure.persistence.jpa.OrderItemJpaRepository;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Primary
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

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

        // 1. OrderItem에서 판매량 집계 (DB 레벨)
        List<ProductSalesCount> salesCounts = orderItemJpaRepository
                .findTopProductsBySalesCount(limit).stream()
                .map(projection -> ProductSalesCount.of(
                        projection.getProductId(),
                        projection.getSalesCount()
                ))
                .toList();

        if (salesCounts.isEmpty()) {
            return List.of();
        }

        // 2. 상위 판매 상품 ID 추출
        List<Long> topProductIds = salesCounts.stream()
                .map(ProductSalesCount::getProductId)
                .toList();

        // 3. 상품 정보 일괄 조회
        Map<Long, Product> productMap = findByIdsIn(topProductIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 4. 판매량 순서 유지하여 반환
        return topProductIds.stream()
                .map(productMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = ProductEntity.from(product);
        ProductEntity saved = productJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void deleteAll() {
        productJpaRepository.deleteAll();
    }
}
