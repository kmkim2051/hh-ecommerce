package com.hh.ecom.product.infrastructure.redis;

import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.order.domain.ProductSalesCount;
import com.hh.ecom.product.application.SalesRankingRepository;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.SalesRanking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Redis 기반 판매 랭킹 서비스 구현체
 * - 고성능 실시간 랭킹 조회 (O(log N))
 * - DB 폴백 포함
 *
 * 활성화 조건: application.yml에서 ranking.sales.strategy=redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "ranking.sales.strategy",
    havingValue = "redis",
    matchIfMissing = true  // 기본값: Redis
)
public class RedisSalesRankingRepository implements SalesRankingRepository {

    private final SalesRankingRedisRepository redisRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    public List<Product> getTopBySalesCount(int limit) {
        try {
            String key = "product:ranking:sales:all";
            List<SalesRanking> rankings = redisRepository.getTopProducts(key, limit);

            if (rankings.isEmpty()) {
                log.warn("Redis 랭킹 데이터 없음, DB 폴백 실행");
                return productRepository.findTopBySalesCount(limit);
            }

            List<Long> productIds = rankings.stream()
                    .map(SalesRanking::getProductId)
                    .toList();

            return getProductsInSequence(productIds);

        } catch (Exception e) {
            log.error("Redis 랭킹 조회 실패, DB 폴백: {}", e.getMessage());
            return productRepository.findTopBySalesCount(limit);
        }
    }

    @Override
    public List<Product> getTopBySalesCountInRecentDays(int days, int limit) {
        try {
            List<SalesRanking> rankings =
                    redisRepository.getTopProductsInRecentDays(days, limit);

            if (rankings.isEmpty()) {
                log.warn("Redis 최근 {}일 랭킹 데이터 없음, DB 폴백", days);
                return productRepository.findTopBySalesCountInRecentDays(days, limit);
            }

            List<Long> productIds = rankings.stream()
                    .map(SalesRanking::getProductId)
                    .toList();

            return getProductsInSequence(productIds);

        } catch (Exception e) {
            log.error("Redis 최근 랭킹 조회 실패, DB 폴백: {}", e.getMessage());
            return productRepository.findTopBySalesCountInRecentDays(days, limit);
        }
    }

    @Override
    public void recordSales(Long productId, Integer quantity) {
        try {
            LocalDate today = LocalDate.now();
            redisRepository.incrementSalesCount(productId, quantity, today);
            log.info("판매량 기록 완료: productId={}, quantity={}", productId, quantity);
        } catch (Exception e) {
            log.warn("판매량 기록 실패 (Redis): productId={}, error={}", productId, e.getMessage());
        }
    }

    @Override
    public void recordBatchSales(Long orderId, List<OrderItem> orderItems) {
        if (orderId == null || orderId <= 0) {
            log.warn("유효하지 않은 주문 ID: orderId={}", orderId);
            return;
        }

        if (orderItems == null || orderItems.isEmpty()) {
            log.warn("주문 아이템이 비어있습니다: orderId={}", orderId);
            return;
        }

        try {
            // 중복 기록 방지
            if (redisRepository.isOrderRecorded(orderId)) {
                log.warn("이미 기록된 주문입니다: orderId={}", orderId);
                return;
            }

            // 판매량 기록
            orderItems.forEach(item ->
                    recordSales(item.getProductId(), item.getQuantity())
            );

            // 기록 완료 마킹
            redisRepository.markOrderRecorded(orderId);

            log.info("배치 판매량 기록 완료: orderId={}, itemCount={}", orderId, orderItems.size());
        } catch (Exception e) {
            log.error("배치 판매량 기록 실패: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    /**
     * DB 데이터를 Redis로 초기화
     * - SalesRankingInitializer에서 호출
     */
    @Transactional(readOnly = true)
    public void initializeFromDatabase() {
        log.info("Redis 판매 랭킹 초기화 시작");

        try {
            initializeAllTimeSales();
            initializeRecentDailySales(30);
            log.info("Redis 판매 랭킹 초기화 완료");
        } catch (Exception e) {
            log.error("Redis 판매 랭킹 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Redis 판매 랭킹 초기화 실패", e);
        }
    }

    private void initializeAllTimeSales() {
        log.info("전체 기간 판매량 초기화 시작");

        List<ProductSalesCount> allTimeSales = orderItemRepository.findAllProductSalesCount();

        if (allTimeSales.isEmpty()) {
            log.warn("전체 기간 판매 데이터 없음");
            return;
        }

        allTimeSales.forEach(sales -> {
            try {
                // 전체 기간은 오늘 날짜로 기록 (날짜는 allTimeKey에서는 무관)
                redisRepository.incrementSalesCount(
                        sales.getProductId(),
                        sales.getSalesCount().intValue(),
                        LocalDate.now()
                );
            } catch (Exception e) {
                log.error("전체 기간 판매량 초기화 실패: productId={}, error={}",
                        sales.getProductId(), e.getMessage());
            }
        });

        log.info("전체 기간 판매량 초기화 완료: productCount={}", allTimeSales.size());
    }

    private void initializeRecentDailySales(int days) {
        log.info("최근 {}일 일별 판매량 초기화 시작", days);

        LocalDate today = LocalDate.now();
        int initializedDays = 0;

        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            List<ProductSalesCount> dailySales = orderItemRepository.findProductSalesCountByDate(date);

            if (dailySales.isEmpty()) {
                log.debug("날짜 {} 판매 데이터 없음", date);
                continue;
            }

            dailySales.forEach(sales -> {
                try {
                    redisRepository.incrementSalesCount(
                            sales.getProductId(),
                            sales.getSalesCount().intValue(),
                            date
                    );
                } catch (Exception e) {
                    log.error("일별 판매량 초기화 실패: date={}, productId={}, error={}",
                            date, sales.getProductId(), e.getMessage());
                }
            });

            initializedDays++;
            log.debug("날짜 {} 판매량 초기화 완료: productCount={}", date, dailySales.size());
        }

        log.info("최근 {}일 일별 판매량 초기화 완료: initializedDays={}", days, initializedDays);
    }

    /**
     * 상품 ID 리스트를 순서대로 Product 객체로 변환
     * - 랭킹 순서 유지
     *
     * @param productIds 상품 ID 리스트 (정렬된 상태)
     * @return 상품 리스트 (순서 유지)
     */
    private List<Product> getProductsInSequence(List<Long> productIds) {


        Map<Long, Product> productMap = productRepository.findByIdsIn(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return productIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
