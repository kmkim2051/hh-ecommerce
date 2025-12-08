package com.hh.ecom.product.presentation;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.order.application.OrderCommandService;
import com.hh.ecom.order.domain.OrderRepository;
import com.hh.ecom.product.application.SalesRankingRepository;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis 기반 판매 랭킹 시스템 End-to-End 테스트
 * - TestContainers (MySQL + Redis) 사용
 * - MockMvc를 통한 실제 HTTP 요청 테스트
 * - HTTP → Controller → Service → Redis까지 전체 플로우 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ProductController E2E 테스트 (Redis 판매 랭킹)")
class ProductControllerE2ETest extends TestContainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private SalesRankingRepository salesRankingRepository;

    @Autowired
    @Qualifier("customStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        // Clean up
        orderRepository.deleteAll();
        productRepository.deleteAll();
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
    @DisplayName("E2E - 판매 랭킹 조회 (전체 기간) - Redis 데이터 없을 때 DB 폴백")
    void e2e_GetSalesRanking_FallbackToDatabase() throws Exception {
        // given - Redis에 데이터 없음 (초기 상태)

        // when & then - DB 폴백으로 조회 (빈 리스트 반환)
        mockMvc.perform(get("/products/ranking/sales")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    @DisplayName("E2E - 판매 랭킹 조회 (전체 기간) - Redis에서 조회")
    void e2e_GetSalesRanking_FromRedis() throws Exception {
        // given - Redis에 판매량 데이터 기록
        salesRankingRepository.recordSales(product1.getId(), 10);
        salesRankingRepository.recordSales(product2.getId(), 20);
        salesRankingRepository.recordSales(product3.getId(), 5);

        // when & then - Redis에서 판매 랭킹 조회
        mockMvc.perform(get("/products/ranking/sales")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(3))
                // 판매량 내림차순: product2(20) > product1(10) > product3(5)
                .andExpect(jsonPath("$.products[0].id").value(product2.getId()))
                .andExpect(jsonPath("$.products[1].id").value(product1.getId()))
                .andExpect(jsonPath("$.products[2].id").value(product3.getId()));
    }

    @Test
    @DisplayName("E2E - 판매 랭킹 조회 limit 적용")
    void e2e_GetSalesRanking_WithLimit() throws Exception {
        // given - Redis에 판매량 데이터 기록
        salesRankingRepository.recordSales(product1.getId(), 10);
        salesRankingRepository.recordSales(product2.getId(), 20);
        salesRankingRepository.recordSales(product3.getId(), 5);

        // when & then - limit=2로 상위 2개만 조회
        mockMvc.perform(get("/products/ranking/sales")
                        .param("limit", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].id").value(product2.getId()))
                .andExpect(jsonPath("$.products[1].id").value(product1.getId()));
    }

    @Test
    @DisplayName("E2E - 판매 랭킹 조회 (기본 limit=10)")
    void e2e_GetSalesRanking_DefaultLimit() throws Exception {
        // given - Redis에 판매량 데이터 기록
        salesRankingRepository.recordSales(product1.getId(), 10);

        // when & then - limit 파라미터 없이 조회 (기본값 10)
        mockMvc.perform(get("/products/ranking/sales")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    @DisplayName("E2E - 최근 n일 판매 랭킹 조회 - Redis에서 조회")
    void e2e_GetRecentSalesRanking_FromRedis() throws Exception {
        // given - Redis에 오늘 날짜로 판매량 데이터 기록
        salesRankingRepository.recordSales(product1.getId(), 15);
        salesRankingRepository.recordSales(product2.getId(), 25);
        salesRankingRepository.recordSales(product3.getId(), 8);

        // when & then - 최근 7일 판매 랭킹 조회
        mockMvc.perform(get("/products/ranking/sales/recent")
                        .param("days", "7")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(3))
                // 판매량 내림차순: product2(25) > product1(15) > product3(8)
                .andExpect(jsonPath("$.products[0].id").value(product2.getId()))
                .andExpect(jsonPath("$.products[1].id").value(product1.getId()))
                .andExpect(jsonPath("$.products[2].id").value(product3.getId()));
    }

    @Test
    @DisplayName("E2E - 판매 랭킹 누적 조회")
    void e2e_GetSalesRanking_Accumulated() throws Exception {
        // given - 동일 상품에 여러 번 판매량 기록 (누적)
        salesRankingRepository.recordSales(product1.getId(), 5);
        salesRankingRepository.recordSales(product1.getId(), 3);
        salesRankingRepository.recordSales(product1.getId(), 2);

        salesRankingRepository.recordSales(product2.getId(), 7);

        // when & then - 누적된 판매량으로 랭킹 조회
        mockMvc.perform(get("/products/ranking/sales")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(2))
                // product1 누적: 10, product2: 7
                .andExpect(jsonPath("$.products[0].id").value(product1.getId()))
                .andExpect(jsonPath("$.products[1].id").value(product2.getId()));
    }

    @Test
    @DisplayName("E2E - 조회수 랭킹과 판매 랭킹은 독립적")
    void e2e_ViewRankingAndSalesRankingIndependent() throws Exception {
        // given - 판매 랭킹만 기록 (조회수는 기록하지 않음)
        salesRankingRepository.recordSales(product1.getId(), 10);
        salesRankingRepository.recordSales(product2.getId(), 20);

        // when & then - 판매 랭킹 조회 성공
        mockMvc.perform(get("/products/ranking/sales")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(2));

        // when & then - 조회수 랭킹 조회 (별도 API, 판매와 무관)
        mockMvc.perform(get("/products/ranking/views")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    @DisplayName("E2E - 상품 상세 조회 API는 정상 동작")
    void e2e_GetProductDetail() throws Exception {
        // when & then - 상품 상세 조회
        mockMvc.perform(get("/products/{id}", product1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product1.getId()))
                .andExpect(jsonPath("$.name").value("노트북"))
                .andExpect(jsonPath("$.price").value(1500000));
    }

    @Test
    @DisplayName("E2E - 상품 목록 조회 API는 정상 동작")
    void e2e_GetProductList() throws Exception {
        // when & then - 상품 목록 조회 (페이지네이션)
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(3));
    }

    @Test
    @DisplayName("E2E - Redis 데이터 초기화 후 조회")
    void e2e_GetSalesRanking_AfterInitialization() throws Exception {
        // given - DB에 COMPLETED 주문 생성 (실제 환경 시뮬레이션)
        // (실제로는 주문 생성 플로우가 복잡하므로, 직접 Redis에 데이터 기록)
        salesRankingRepository.recordSales(product1.getId(), 100);
        salesRankingRepository.recordSales(product2.getId(), 50);

        // when & then - 판매 랭킹 조회
        mockMvc.perform(get("/products/ranking/sales")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].id").value(product1.getId()))
                .andExpect(jsonPath("$.products[0].name").value("노트북"));
    }

    // ===== Helper Methods =====

    private Product createAndSaveProduct(String name, BigDecimal price, Integer stock) {
        Product product = Product.create(name, "Test Description", price, stock);
        return productRepository.save(product);
    }

    private void cleanupRedisKeys() {
        try {
            Set<String> rankingKeys = redisTemplate.keys("product:ranking:sales:*");
            if (rankingKeys != null && !rankingKeys.isEmpty()) {
                redisTemplate.delete(rankingKeys);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
