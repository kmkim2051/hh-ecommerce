package com.hh.ecom.product.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.ViewCountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ProductService 통합 테스트")
class ProductServiceIntegrationTest extends TestContainersConfig {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ViewCountRepository viewCountRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Nested
    @DisplayName("상품 조회 시 조회수 증가 통합 테스트")
    class ViewCountIncrementIntegrationTest {

        @Test
        @DisplayName("상품 조회 시 Redis에 조회수 델타가 기록된다")
        void getProduct_incrementsViewCountInRedis() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 100);
            Product savedProduct = productRepository.save(product);
            Long productId = savedProduct.getId();

            // when
            productService.getProduct(productId);
            productService.getProduct(productId);
            productService.getProduct(productId);

            // then
            Long delta = viewCountRepository.getDelta(productId);
            assertThat(delta).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("최근 n일간 조회수 기반 인기상품 조회 통합 테스트")
    class RecentDaysViewCountRankingIntegrationTest {

        @Test
        @DisplayName("최근 N일 조회수 랭킹은 Redis 기록 기준으로 조회한다")
        void getProductsByViewCountInRecentDays_usesRedisData() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 10));
            Product product2 = productRepository.save(Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 20));
            Product product3 = productRepository.save(Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 30));

            // 상품 조회 (Redis에 타임스탬프 기록됨)
            productService.getProduct(product1.getId());
            productService.getProduct(product1.getId());
            productService.getProduct(product1.getId()); // product1: 3회

            productService.getProduct(product2.getId());
            productService.getProduct(product2.getId()); // product2: 2회

            productService.getProduct(product3.getId()); // product3: 1회

            // when
            List<Product> result = productService.getProductsByViewCountInRecentDays(1, 10);

            // then - Redis 기록 기준으로 정렬됨
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId()); // 3회
            assertThat(result.get(1).getId()).isEqualTo(product2.getId()); // 2회
            assertThat(result.get(2).getId()).isEqualTo(product3.getId()); // 1회
        }

        @Test
        @DisplayName("최근 N일 조회 기록이 없으면 빈 리스트를 반환한다")
        void getProductsByViewCountInRecentDays_noData() {
            // given
            productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 10));

            // when - 조회 없이 바로 랭킹 조회
            List<Product> result = productService.getProductsByViewCountInRecentDays(7, 10);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("최근 n일간 판매량 기반 인기상품 조회 통합 테스트")
    class RecentDaysSalesCountRankingIntegrationTest {

        @Autowired
        private com.hh.ecom.order.infrastructure.persistence.jpa.OrderJpaRepository orderJpaRepository;

        @Autowired
        private com.hh.ecom.order.infrastructure.persistence.jpa.OrderItemJpaRepository orderItemJpaRepository;

        @Autowired
        private EntityManager entityManager;

        @BeforeEach
        void setUpOrders() {
            orderItemJpaRepository.deleteAll();
            orderJpaRepository.deleteAll();
        }

        @Test
        @DisplayName("최근 1일간 판매량이 높은 상품 순으로 조회한다")
        @Transactional
        void getProductsBySalesCountInRecentDays_1day() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 100));
            Product product2 = productRepository.save(Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 100));
            Product product3 = productRepository.save(Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 100));

            LocalDateTime now = LocalDateTime.now();

            // 최근 1일 이내 완료된 주문
            createCompletedOrder(1L, product1.getId(), 5, now.minusHours(1));
            createCompletedOrder(2L, product1.getId(), 3, now.minusHours(2));
            createCompletedOrder(3L, product2.getId(), 4, now.minusHours(3));
            createCompletedOrder(4L, product3.getId(), 2, now.minusHours(4));

            // 2일 전 주문은 제외되어야 함
            createCompletedOrder(5L, product3.getId(), 10, now.minusDays(2));

            // when
            List<Product> result = productService.getProductsBySalesCountInRecentDays(1, 10);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId()); // 8개
            assertThat(result.get(1).getId()).isEqualTo(product2.getId()); // 4개
            assertThat(result.get(2).getId()).isEqualTo(product3.getId()); // 2개
        }

        @Test
        @DisplayName("최근 3일간 판매량이 높은 상품 순으로 조회한다")
        @Transactional
        void getProductsBySalesCountInRecentDays_3days() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 100));
            Product product2 = productRepository.save(Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 100));
            Product product3 = productRepository.save(Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 100));

            LocalDateTime now = LocalDateTime.now();

            // product1: 2일 전 3개
            createCompletedOrder(1L, product1.getId(), 3, now.minusDays(2));

            // product2: 1일 전 5개
            createCompletedOrder(2L, product2.getId(), 5, now.minusDays(1));

            // product3: 오늘 7개
            createCompletedOrder(3L, product3.getId(), 7, now);

            // 4일 전 주문은 제외되어야 함
            createCompletedOrder(4L, product1.getId(), 20, now.minusDays(4));

            // when
            List<Product> result = productService.getProductsBySalesCountInRecentDays(3, 10);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(product3.getId()); // 7개
            assertThat(result.get(1).getId()).isEqualTo(product2.getId()); // 5개
            assertThat(result.get(2).getId()).isEqualTo(product1.getId()); // 3개
        }

        @Test
        @DisplayName("최근 7일간 판매량이 높은 상품 순으로 조회한다")
        @Transactional
        void getProductsBySalesCountInRecentDays_7days() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 100));
            Product product2 = productRepository.save(Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 100));
            Product product3 = productRepository.save(Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 100));
            Product product4 = productRepository.save(Product.create("상품4", "설명4", BigDecimal.valueOf(4000), 100));

            LocalDateTime now = LocalDateTime.now();

            // product1: 6일 전 2개
            createCompletedOrder(1L, product1.getId(), 2, now.minusDays(6));

            // product2: 5일 전 4개
            createCompletedOrder(2L, product2.getId(), 4, now.minusDays(5));

            // product3: 3일 전 6개
            createCompletedOrder(3L, product3.getId(), 6, now.minusDays(3));

            // product4: 오늘 8개
            createCompletedOrder(4L, product4.getId(), 8, now);

            // 8일 전 주문은 제외되어야 함
            createCompletedOrder(5L, product1.getId(), 50, now.minusDays(8));

            // when
            List<Product> result = productService.getProductsBySalesCountInRecentDays(7, 10);

            // then
            assertThat(result).hasSize(4);
            assertThat(result.get(0).getId()).isEqualTo(product4.getId()); // 8개
            assertThat(result.get(1).getId()).isEqualTo(product3.getId()); // 6개
            assertThat(result.get(2).getId()).isEqualTo(product2.getId()); // 4개
            assertThat(result.get(3).getId()).isEqualTo(product1.getId()); // 2개
        }

        @Test
        @DisplayName("COMPLETED 상태의 주문만 집계한다")
        @Transactional
        void getProductsBySalesCountInRecentDays_onlyCompletedOrders() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 100));
            Product product2 = productRepository.save(Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 100));

            LocalDateTime now = LocalDateTime.now();

            // COMPLETED 상태 주문
            createCompletedOrder(1L, product1.getId(), 5, now);

            // PENDING, PAID 상태 주문은 제외되어야 함
            createOrderWithStatus(2L, product2.getId(), 10, now, com.hh.ecom.order.domain.OrderStatus.PENDING);
            createOrderWithStatus(3L, product2.getId(), 10, now, com.hh.ecom.order.domain.OrderStatus.PAID);

            // when
            List<Product> result = productService.getProductsBySalesCountInRecentDays(1, 10);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
        }

        @Test
        @DisplayName("limit 만큼만 상품을 조회한다")
        @Transactional
        void getProductsBySalesCountInRecentDays_withLimit() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 100));
            Product product2 = productRepository.save(Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 100));
            Product product3 = productRepository.save(Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 100));

            LocalDateTime now = LocalDateTime.now();
            createCompletedOrder(1L, product1.getId(), 10, now);
            createCompletedOrder(2L, product2.getId(), 8, now);
            createCompletedOrder(3L, product3.getId(), 6, now);

            // when
            List<Product> result = productService.getProductsBySalesCountInRecentDays(1, 2);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(product1.getId());
            assertThat(result.get(1).getId()).isEqualTo(product2.getId());
        }

        @Test
        @DisplayName("최근 n일간 완료된 주문이 없으면 빈 리스트를 반환한다")
        @Transactional
        void getProductsBySalesCountInRecentDays_noCompletedOrders() {
            // given
            Product product1 = productRepository.save(Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 100));

            LocalDateTime now = LocalDateTime.now();
            // 8일 전 주문만 존재
            createCompletedOrder(1L, product1.getId(), 10, now.minusDays(8));

            // when
            List<Product> result = productService.getProductsBySalesCountInRecentDays(7, 10);

            // then
            assertThat(result).isEmpty();
        }

        private void createCompletedOrder(Long userId, Long productId, int quantity, LocalDateTime createdAt) {
            createOrderWithStatus(userId, productId, quantity, createdAt, com.hh.ecom.order.domain.OrderStatus.COMPLETED);
        }

        private void createOrderWithStatus(Long userId, Long productId, int quantity, LocalDateTime createdAt,
                                           com.hh.ecom.order.domain.OrderStatus status) {
            String orderNumber = "ORD-" + System.nanoTime();
            BigDecimal amount = BigDecimal.valueOf(1000);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String createdAtStr = createdAt.format(formatter);

            // Order 직접 INSERT
            entityManager.createNativeQuery(
                    "INSERT INTO orders (user_id, order_number, total_amount, discount_amount, final_amount, status, created_at, updated_at, version) " +
                    "VALUES (:userId, :orderNumber, :totalAmount, :discountAmount, :finalAmount, :status, :createdAt, :updatedAt, 0)")
                    .setParameter("userId", userId)
                    .setParameter("orderNumber", orderNumber)
                    .setParameter("totalAmount", amount)
                    .setParameter("discountAmount", BigDecimal.ZERO)
                    .setParameter("finalAmount", amount)
                    .setParameter("status", status.name())
                    .setParameter("createdAt", createdAtStr)
                    .setParameter("updatedAt", createdAtStr)
                    .executeUpdate();

            entityManager.flush();

            // 방금 생성한 order의 ID 조회
            Long orderId = ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();

            // OrderItem 직접 INSERT
            entityManager.createNativeQuery(
                    "INSERT INTO order_items (order_id, product_id, product_name, price, quantity, status, created_at, updated_at) " +
                    "VALUES (:orderId, :productId, :productName, :price, :quantity, :status, :createdAt, :updatedAt)")
                    .setParameter("orderId", orderId)
                    .setParameter("productId", productId)
                    .setParameter("productName", "상품")
                    .setParameter("price", BigDecimal.valueOf(1000))
                    .setParameter("quantity", quantity)
                    .setParameter("status", com.hh.ecom.order.domain.OrderItemStatus.NORMAL.name())
                    .setParameter("createdAt", createdAtStr)
                    .setParameter("updatedAt", createdAtStr)
                    .executeUpdate();

            entityManager.flush();
        }
    }
}
