package com.hh.ecom.product.infrastructure.db;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.product.application.SalesRankingRepository;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DB 기반 판매 랭킹 서비스 구현체
 * 활성화 조건: ranking.sales.strategy=db
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "ranking.sales.strategy",
    havingValue = "db"
)
public class DbSalesRankingRepository implements SalesRankingRepository {

    private final ProductRepository productRepository;

    @Override
    public List<Product> getTopBySalesCount(int limit) {
        log.debug("DB 기반 판매 랭킹 조회: limit={}", limit);
        return productRepository.findTopBySalesCount(limit);
    }

    @Override
    public List<Product> getTopBySalesCountInRecentDays(int days, int limit) {
        log.debug("DB 기반 최근 {}일 판매 랭킹 조회: limit={}", days, limit);
        return productRepository.findTopBySalesCountInRecentDays(days, limit);
    }

    @Override
    public void recordSales(Long productId, Integer quantity) {
        // DB 기반은 주문 테이블에 이미 기록되므로 별도 처리 불필요
        log.debug("DB 기반 판매 기록 (no-op): productId={}, quantity={}", productId, quantity);
    }

    @Override
    public void recordBatchSales(Long orderId, List<OrderItem> orderItems) {
        // DB 기반은 주문 테이블에 이미 기록되므로 별도 처리 불필요
        log.debug("DB 기반 배치 판매 기록 (no-op): orderId={}", orderId);
    }
}
