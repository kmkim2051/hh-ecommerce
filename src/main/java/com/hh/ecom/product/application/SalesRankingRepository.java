package com.hh.ecom.product.application;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.product.domain.Product;

import java.util.List;

public interface SalesRankingRepository {

    List<Product> getTopBySalesCount(int limit);
    List<Product> getTopBySalesCountInRecentDays(int days, int limit);

    void recordSales(Long productId, Integer quantity);
    void recordBatchSales(Long orderId, List<OrderItem> orderItems);
}
