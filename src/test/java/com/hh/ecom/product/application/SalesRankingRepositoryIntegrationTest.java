package com.hh.ecom.product.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.order.domain.*;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.infrastructure.redis.RedisSalesRankingRepository;
import com.hh.ecom.product.infrastructure.redis.SalesRankingRedisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RedisSalesRankingService 통합 테스트")
class SalesRankingRepositoryIntegrationTest extends TestContainersConfig {

    @Autowired
    private RedisSalesRankingRepository salesRankingService;  // Redis 구현체 직접 주입

    @Autowired
    private SalesRankingRedisRepository salesRankingRedisRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("customStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        // Clean up DB
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        // Clean up Redis
        cleanupRedisKeys();

        // Create test products
        product1 = createAndSaveProduct("노트북", BigDecimal.valueOf(1500000), 100);
        product2 = createAndSaveProduct("마우스", BigDecimal.valueOf(50000), 200);
        product3 = createAndSaveProduct("키보드", BigDecimal.valueOf(80000), 150);
    }

    @AfterEach
    void tearDown() {
        cleanupRedisKeys();
    }

    @Test
    @DisplayName("판매량 기록 - 단일 상품")
    void recordSales_SingleProduct() {
        // given
        Long productId = product1.getId();
        Integer quantity = 5;

        // when
        salesRankingService.recordSales(productId, quantity);

        // then
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);
        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).getId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("배치 판매량 기록 - 여러 상품")
    void recordBatchSales_MultipleProducts() {
        // given
        Long orderId = 1L;
        List<OrderItem> orderItems = List.of(
                createOrderItem(orderId, product1.getId(), 3),
                createOrderItem(orderId, product2.getId(), 5),
                createOrderItem(orderId, product3.getId(), 2)
        );

        // when
        salesRankingService.recordBatchSales(orderId, orderItems);

        // then
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);
        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).getId()).isEqualTo(product2.getId()); // 5개
        assertThat(ranking.get(1).getId()).isEqualTo(product1.getId()); // 3개
        assertThat(ranking.get(2).getId()).isEqualTo(product3.getId()); // 2개
    }

    @Test
    @DisplayName("배치 판매량 기록 - 중복 방지")
    void recordBatchSales_PreventDuplicates() {
        // given
        Long orderId = 1L;
        List<OrderItem> orderItems = List.of(
                createOrderItem(orderId, product1.getId(), 5)
        );

        // when - 동일 주문을 두 번 기록
        salesRankingService.recordBatchSales(orderId, orderItems);
        salesRankingService.recordBatchSales(orderId, orderItems); // 중복

        // then - 중복 기록 방지로 5개만 기록됨
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);
        assertThat(ranking).hasSize(1);
        // Redis에서는 중복 방지가 되므로 5개만 기록됨
    }

    @Test
    @DisplayName("전체 기간 판매 랭킹 조회")
    void getTopBySalesCount() {
        // given - 여러 상품의 판매량 기록
        salesRankingService.recordSales(product1.getId(), 10);
        salesRankingService.recordSales(product2.getId(), 20);
        salesRankingService.recordSales(product3.getId(), 5);

        // when
        List<Product> ranking = salesRankingService.getTopBySalesCount(3);

        // then - 판매량 내림차순
        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).getId()).isEqualTo(product2.getId()); // 20개
        assertThat(ranking.get(1).getId()).isEqualTo(product1.getId()); // 10개
        assertThat(ranking.get(2).getId()).isEqualTo(product3.getId()); // 5개
    }

    @Test
    @DisplayName("최근 n일 판매 랭킹 조회")
    void getTopBySalesCountInRecentDays() {
        // given - 오늘과 어제 판매 기록
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        salesRankingRedisRepository.incrementSalesCount(product1.getId(), 10, today);
        salesRankingRedisRepository.incrementSalesCount(product2.getId(), 5, today);
        salesRankingRedisRepository.incrementSalesCount(product1.getId(), 3, yesterday);

        // when - 최근 2일 조회
        List<Product> ranking = salesRankingService.getTopBySalesCountInRecentDays(2, 10);

        // then - 최근 2일 합산: product1=13, product2=5
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getId()).isEqualTo(product1.getId()); // 13개
        assertThat(ranking.get(1).getId()).isEqualTo(product2.getId()); // 5개
    }

    @Test
    @DisplayName("DB 초기화 - 전체 기간 판매량")
    void initializeFromDatabase_AllTimeSales() {
        // given - DB에 COMPLETED 주문 데이터 생성
        Order order1 = createAndSaveCompletedOrder(1L, "ORDER-001");
        OrderItem item1 = createAndSaveOrderItem(order1.getId(), product1.getId(), 10);
        OrderItem item2 = createAndSaveOrderItem(order1.getId(), product2.getId(), 5);

        Order order2 = createAndSaveCompletedOrder(2L, "ORDER-002");
        OrderItem item3 = createAndSaveOrderItem(order2.getId(), product1.getId(), 7);

        // when - Redis 초기화
        salesRankingService.initializeFromDatabase();

        // then - Redis에 데이터 적재 확인
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getId()).isEqualTo(product1.getId()); // 17개
        assertThat(ranking.get(1).getId()).isEqualTo(product2.getId()); // 5개
    }

    @Test
    @DisplayName("DB 초기화 - PENDING 주문은 제외")
    void initializeFromDatabase_OnlyCompletedOrders() {
        // given - PENDING 주문과 COMPLETED 주문 생성
        Order pendingOrder = createAndSavePendingOrder(1L, "ORDER-001");
        OrderItem pendingItem = createAndSaveOrderItem(pendingOrder.getId(), product1.getId(), 100);

        Order completedOrder = createAndSaveCompletedOrder(2L, "ORDER-002");
        OrderItem completedItem = createAndSaveOrderItem(completedOrder.getId(), product2.getId(), 5);

        // when - Redis 초기화
        salesRankingService.initializeFromDatabase();

        // then - COMPLETED 주문만 집계됨
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);
        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).getId()).isEqualTo(product2.getId()); // 5개만
    }

    @Test
    @DisplayName("Redis 데이터 없을 때 DB 폴백")
    void getTopBySalesCount_FallbackToDatabase() {
        // given - DB에만 데이터 존재 (Redis는 비어있음)
        Order order = createAndSaveCompletedOrder(1L, "ORDER-001");
        createAndSaveOrderItem(order.getId(), product1.getId(), 10);
        createAndSaveOrderItem(order.getId(), product2.getId(), 5);

        // when - Redis 조회 (비어있음 -> DB 폴백)
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);

        // then - DB에서 조회된 결과 반환
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getId()).isEqualTo(product1.getId()); // 10개
        assertThat(ranking.get(1).getId()).isEqualTo(product2.getId()); // 5개
    }

    @Test
    @DisplayName("여러 주문의 누적 판매량 집계")
    void accumulateSalesFromMultipleOrders() {
        // given - 동일 상품에 대한 여러 주문
        salesRankingService.recordSales(product1.getId(), 10);
        salesRankingService.recordSales(product1.getId(), 5);
        salesRankingService.recordSales(product1.getId(), 3);

        salesRankingService.recordSales(product2.getId(), 7);

        // when
        List<Product> ranking = salesRankingService.getTopBySalesCount(10);

        // then - 누적 판매량 확인
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getId()).isEqualTo(product1.getId()); // 18개 (10+5+3)
        assertThat(ranking.get(1).getId()).isEqualTo(product2.getId()); // 7개
    }

    // ===== Helper Methods =====

    private Product createAndSaveProduct(String name, BigDecimal price, Integer stock) {
        Product product = Product.create(name, "Test Description", price, stock);
        return productRepository.save(product);
    }

    private OrderItem createOrderItem(Long orderId, Long productId, Integer quantity) {
        return OrderItem.create(
                orderId,
                productId,
                "Test Product",
                BigDecimal.valueOf(10000),
                quantity
        );
    }

    private Order createAndSaveCompletedOrder(Long userId, String orderNumber) {
        Order order = Order.create(userId, orderNumber, BigDecimal.valueOf(100000), BigDecimal.ZERO, null);
        Order savedOrder = orderRepository.save(order);
        // Update to COMPLETED status
        Order completedOrder = savedOrder.updateStatus(OrderStatus.COMPLETED);
        return orderRepository.save(completedOrder);
    }

    private Order createAndSavePendingOrder(Long userId, String orderNumber) {
        Order order = Order.create(userId, orderNumber, BigDecimal.valueOf(100000), BigDecimal.ZERO, null);
        return orderRepository.save(order);
    }

    private OrderItem createAndSaveOrderItem(Long orderId, Long productId, Integer quantity) {
        OrderItem item = OrderItem.create(
                orderId,
                productId,
                "Test Product",
                BigDecimal.valueOf(10000),
                quantity
        );
        return orderItemRepository.save(item);
    }

    private void cleanupRedisKeys() {
        try {
            // Clean up sales ranking keys
            Set<String> rankingKeys = redisTemplate.keys("product:ranking:sales:*");
            if (rankingKeys != null && !rankingKeys.isEmpty()) {
                redisTemplate.delete(rankingKeys);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
