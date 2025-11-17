package com.hh.ecom.product.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//@SpringBootTest
//@DisplayName("ProductService 대용량 데이터 성능 테스트")
//class ProductServicePerformanceTest extends TestContainersConfig {
//
//    @Autowired
//    private ProductService productService;
//
//    @Autowired
//    private ProductRepository productRepository;
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    @Autowired
//    private EntityManager entityManager;
//
//    @BeforeEach
//    void setUp() {
//        // 모든 데이터 삭제
//        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
//        jdbcTemplate.execute("TRUNCATE TABLE order_items");
//        jdbcTemplate.execute("TRUNCATE TABLE orders");
//        jdbcTemplate.execute("TRUNCATE TABLE products");
//        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
//
//        // 인덱스 존재 확인
//        verifyIndexes();
//    }
//
//    private void verifyIndexes() {
//        String checkIndexSql = """
//            SELECT COUNT(*) as cnt
//            FROM information_schema.statistics
//            WHERE table_schema = DATABASE()
//            AND table_name = 'order_items'
//            AND index_name = 'idx_order_items_join_group_covering'
//            """;
//
//        Integer count = jdbcTemplate.queryForObject(checkIndexSql, Integer.class);
//        if (count != null && count > 0) {
//            System.out.println("✓ Covering Index 적용 확인: idx_order_items_join_group_covering");
//        } else {
//            System.out.println("⚠️  경고: Covering Index가 없습니다!");
//        }
//    }
//
//    @Test
//    @DisplayName("판매량 기준 상품 조회 - 1천 건 성능 테스트 (인덱스 적용)")
//    void getProductsBySalesCount_1k_withIndex() {
//        // Given: 1천 건 데이터 생성
//        System.out.println("\n========== 1천 건 데이터 테스트 (인덱스 적용) ==========");
//        long startSetup = System.currentTimeMillis();
//        createTestDataWithJdbc(100, 1_000);
//        long setupTime = System.currentTimeMillis() - startSetup;
//        System.out.println("✓ 데이터 생성 시간: " + setupTime + "ms");
//
//        // When: 판매량 기준 조회
//        long startQuery = System.currentTimeMillis();
//        List<Product> result = productService.getProductsBySalesCount(10);
//        long queryTime = System.currentTimeMillis() - startQuery;
//
//        // Then: 성능 측정
//        System.out.println("✓ 조회 시간: " + queryTime + "ms");
//        System.out.println("✓ 결과 건수: " + result.size());
//        assertThat(result).hasSize(10);
//        assertThat(queryTime).isLessThan(500); // 500ms 이내
//    }
//
//    @Test
//    @DisplayName("판매량 기준 상품 조회 - 1만 건 성능 테스트 (인덱스 적용)")
//    void getProductsBySalesCount_10k_withIndex() {
//        // Given: 1만 건 데이터 생성
//        System.out.println("\n========== 1만 건 데이터 테스트 (인덱스 적용) ==========");
//        long startSetup = System.currentTimeMillis();
//        createTestDataWithJdbc(1000, 10_000);
//        long setupTime = System.currentTimeMillis() - startSetup;
//        System.out.println("✓ 데이터 생성 시간: " + setupTime + "ms");
//
//        // When: 판매량 기준 조회
//        long startQuery = System.currentTimeMillis();
//        List<Product> result = productService.getProductsBySalesCount(10);
//        long queryTime = System.currentTimeMillis() - startQuery;
//
//        // Then: 성능 측정
//        System.out.println("✓ 조회 시간: " + queryTime + "ms");
//        System.out.println("✓ 결과 건수: " + result.size());
//        assertThat(result).hasSize(10);
//        assertThat(queryTime).isLessThan(1000); // 1초 이내
//    }
//
//    @Test
//    @DisplayName("판매량 기준 상품 조회 - 10만 건 성능 테스트 (인덱스 적용)")
//    void getProductsBySalesCount_100k_withIndex() {
//        // Given: 10만 건 데이터 생성
//        System.out.println("\n========== 10만 건 데이터 테스트 (인덱스 적용) ==========");
//        long startSetup = System.currentTimeMillis();
//        createTestDataWithJdbc(1000, 100_000);
//        long setupTime = System.currentTimeMillis() - startSetup;
//        System.out.println("✓ 데이터 생성 시간: " + setupTime + "ms");
//
//        // When: 판매량 기준 조회
//        long startQuery = System.currentTimeMillis();
//        List<Product> result = productService.getProductsBySalesCount(10);
//        long queryTime = System.currentTimeMillis() - startQuery;
//
//        // Then: 성능 측정
//        System.out.println("✓ 조회 시간: " + queryTime + "ms");
//        System.out.println("✓ 결과 건수: " + result.size());
//        assertThat(result).hasSize(10);
//        assertThat(queryTime).isLessThan(2000); // 2초 이내
//    }
//
//    private void createTestDataWithJdbc(int productCount, int orderItemCount) {
//        int orderCount = orderItemCount / 10; // 주문당 평균 10개 아이템
//
//        // 1. 상품 데이터 생성
//        String productSql = """
//            INSERT INTO products (name, description, price, stock_quantity, view_count, is_active, created_at, updated_at)
//            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
//            """;
//
//        jdbcTemplate.batchUpdate(productSql, new BatchPreparedStatementSetter() {
//            @Override
//            public void setValues(PreparedStatement ps, int i) throws SQLException {
//                ps.setString(1, "상품 " + (i + 1));
//                ps.setString(2, "성능 테스트용 상품");
//                ps.setBigDecimal(3, BigDecimal.valueOf(10000 + i * 100));
//                ps.setInt(4, 1000000); // 충분한 재고
//                ps.setInt(5, 0);
//                ps.setBoolean(6, true);
//            }
//
//            @Override
//            public int getBatchSize() {
//                return productCount;
//            }
//        });
//
//        // 2. 주문 데이터 생성
//        String orderSql = """
//            INSERT INTO orders (user_id, order_number, status, total_amount, discount_amount, final_amount, created_at, updated_at, version)
//            VALUES (1, ?, 'COMPLETED', 100000, 0, 100000, NOW(), NOW(), 0)
//            """;
//
//        jdbcTemplate.batchUpdate(orderSql, new BatchPreparedStatementSetter() {
//            @Override
//            public void setValues(PreparedStatement ps, int i) throws SQLException {
//                ps.setString(1, "ORD" + String.format("%010d", i + 1));
//            }
//
//            @Override
//            public int getBatchSize() {
//                return orderCount;
//            }
//        });
//
//        // 3. 주문 아이템 생성
//        String orderItemSql = """
//            INSERT INTO order_items (order_id, product_id, product_name, price, quantity, status, created_at, updated_at)
//            VALUES (?, ?, ?, ?, ?, 'NORMAL', NOW(), NOW())
//            """;
//
//        jdbcTemplate.batchUpdate(orderItemSql, new BatchPreparedStatementSetter() {
//            @Override
//            public void setValues(PreparedStatement ps, int i) throws SQLException {
//                long orderId = (i / 10) + 1; // 주문당 10개 아이템
//                long productId = (long) (Math.random() * productCount) + 1;
//                int quantity = (int) (Math.random() * 10) + 1;
//
//                ps.setLong(1, orderId);
//                ps.setLong(2, productId);
//                ps.setString(3, "상품 " + productId);
//                ps.setBigDecimal(4, BigDecimal.valueOf(10000));
//                ps.setInt(5, quantity);
//            }
//
//            @Override
//            public int getBatchSize() {
//                return orderItemCount;
//            }
//        });
//    }
//
//}
