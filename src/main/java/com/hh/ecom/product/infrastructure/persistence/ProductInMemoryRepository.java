package com.hh.ecom.product.infrastructure.persistence;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.infrastructure.persistence.entity.ProductEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ProductInMemoryRepository implements ProductRepository {
    private final Map<Long, ProductEntity> products = new ConcurrentHashMap<>();
    private final OrderItemRepository orderItemRepository;

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
        if (limit == null || limit <= 0) {
            return List.of();
        }

        List<OrderItem> allOrderItems = Optional.ofNullable(orderItemRepository.findAll())
                .orElse(Collections.emptyList());

        if (allOrderItems.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> productSalesCount = allOrderItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductId() != null && item.getQuantity() != null)
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.summingLong(item -> item.getQuantity().longValue())
                ));

        if (productSalesCount.isEmpty()) {
            return List.of();
        }

        return productSalesCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> Optional.ofNullable(products.get(entry.getKey())))
                .flatMap(Optional::stream)
                .map(ProductEntity::toDomain)
                .toList();
    }
}
