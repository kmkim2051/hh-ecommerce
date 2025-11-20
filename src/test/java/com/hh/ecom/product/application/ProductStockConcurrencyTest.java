package com.hh.ecom.product.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("상품 재고 동시성 테스트")
class ProductStockConcurrencyTest extends TestContainersConfig {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("기본 재고 차감 테스트 - 단일 사용자")
    void simpleStockDecrease() {
        // given
        Product product = Product.create(
                "테스트 상품",
                "설명",
                BigDecimal.valueOf(10000),
                10
        );
        product = productRepository.save(product);
        System.out.println("Saved product ID: " + product.getId());
        final Long productId = product.getId();

        // when
        Product result = transactionTemplate.execute(status -> {
            List<Product> products = productRepository.findByIdsInForUpdate(List.of(productId));
            assertThat(products).isNotEmpty();

            Product lockedProduct = products.get(0);
            Product decreased = lockedProduct.decreaseStock(1);
            return productRepository.save(decreased);
        });

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStockQuantity()).isEqualTo(9);

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(9);
    }

    @Test
    @DisplayName("동시에 여러 사용자가 같은 상품 구매 시도 - 재고 정합성 검증")
    void concurrentStockDecrease_MultipleUsers() throws InterruptedException {
        // given
        int initialStock = 10;
        int concurrentUsers = 50;

        Product product = Product.create(
                "인기 상품",
                "한정판 상품",
                BigDecimal.valueOf(50000),
                initialStock
        );
        product = productRepository.save(product);
        final Long productId = product.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50명의 사용자가 동시에 재고 차감 시도
        IntStream.range(0, concurrentUsers).forEach(i -> {
            executorService.submit(() -> {
                try {
                    transactionTemplate.execute(status -> {
                        // 비관적 락으로 상품 조회
                        List<Product> products = productRepository.findByIdsInForUpdate(List.of(productId));
                        if (products.isEmpty()) {
                            throw new RuntimeException("Product not found");
                        }

                        Product lockedProduct = products.get(0);

                        // 재고 감소 시도
                        if (!lockedProduct.hasEnoughStock(1)) {
                            throw new RuntimeException("Insufficient stock");
                        }

                        Product decreased = lockedProduct.decreaseStock(1);
                        productRepository.save(decreased);
                        return null;
                    });

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    if (e.getCause() != null) {
                        System.out.println("Cause: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 정확히 10명만 성공, 재고는 0
        System.out.println("=== 동시 재고 차감 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failCount.get()).isEqualTo(concurrentUsers - initialStock);

        // 최종 재고 확인
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("동시 재고 차감 시 음수 재고 방지 검증")
    void concurrentStockDecrease_PreventNegativeStock() throws InterruptedException {
        // given
        int initialStock = 5;
        int concurrentUsers = 20;
        int quantityPerUser = 2;

        Product product = Product.create(
                "한정 수량 상품",
                "재고 소진 임박",
                BigDecimal.valueOf(100000),
                initialStock
        );
        product = productRepository.save(product);
        final Long productId = product.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        // when - 20명이 각각 2개씩(총 40개) 구매 시도, 하지만 재고는 5개
        IntStream.range(0, concurrentUsers).forEach(i -> {
            executorService.submit(() -> {
                try {
                    transactionTemplate.execute(status -> {
                        List<Product> products = productRepository.findByIdsInForUpdate(List.of(productId));
                        if (products.isEmpty()) {
                            throw new RuntimeException("Product not found");
                        }

                        Product lockedProduct = products.get(0);

                        if (!lockedProduct.hasEnoughStock(quantityPerUser)) {
                            throw new RuntimeException("Insufficient stock");
                        }

                        Product decreased = lockedProduct.decreaseStock(quantityPerUser);
                        productRepository.save(decreased);
                        return null;
                    });

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 2명만 성공 (5개 재고 / 2개씩 = 2명), 재고는 음수가 되지 않음
        System.out.println("=== 음수 재고 방지 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + exceptions.size());

        int expectedSuccess = initialStock / quantityPerUser; // 5 / 2 = 2
        assertThat(successCount.get()).isEqualTo(expectedSuccess);

        // 최종 재고는 1개 (5 - 2*2 = 1)
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        int expectedRemainingStock = initialStock - (expectedSuccess * quantityPerUser);
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(expectedRemainingStock);
    }

    @Test
    @DisplayName("여러 상품 동시 구매 - 서로 다른 상품은 독립적으로 처리")
    void concurrentStockDecrease_MultipleProducts() throws InterruptedException {
        // given
        int stockPerProduct = 10;
        int productsCount = 3;
        int usersPerProduct = 15;

        // 3개의 상품 생성
        Product product1 = productRepository.save(
                Product.create("상품1", "설명1", BigDecimal.valueOf(10000), stockPerProduct)
        );
        Product product2 = productRepository.save(
                Product.create("상품2", "설명2", BigDecimal.valueOf(20000), stockPerProduct)
        );
        Product product3 = productRepository.save(
                Product.create("상품3", "설명3", BigDecimal.valueOf(30000), stockPerProduct)
        );

        List<Long> productIds = List.of(product1.getId(), product2.getId(), product3.getId());

        ExecutorService executorService = Executors.newFixedThreadPool(productsCount * usersPerProduct);
        CountDownLatch latch = new CountDownLatch(productsCount * usersPerProduct);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 45명이 동시에 서로 다른 상품 구매 (각 상품당 15명)
        IntStream.range(0, productsCount * usersPerProduct).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long productId = productIds.get(i % productsCount);

                    transactionTemplate.execute(status -> {
                        List<Product> products = productRepository.findByIdsInForUpdate(List.of(productId));
                        if (products.isEmpty()) {
                            throw new RuntimeException("Product not found");
                        }

                        Product lockedProduct = products.get(0);

                        if (!lockedProduct.hasEnoughStock(1)) {
                            throw new RuntimeException("Insufficient stock");
                        }

                        Product decreased = lockedProduct.decreaseStock(1);
                        productRepository.save(decreased);
                        return null;
                    });

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 각 상품당 10명씩 총 30명 성공
        System.out.println("=== 다중 상품 동시 구매 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        int expectedTotalSuccess = productsCount * stockPerProduct; // 3 * 10 = 30
        assertThat(successCount.get()).isEqualTo(expectedTotalSuccess);
        assertThat(failCount.get()).isEqualTo((productsCount * usersPerProduct) - expectedTotalSuccess);

        // 각 상품의 최종 재고는 0개
        Product updated1 = productRepository.findById(product1.getId()).orElseThrow();
        Product updated2 = productRepository.findById(product2.getId()).orElseThrow();
        Product updated3 = productRepository.findById(product3.getId()).orElseThrow();

        assertThat(updated1.getStockQuantity()).isZero();
        assertThat(updated2.getStockQuantity()).isZero();
        assertThat(updated3.getStockQuantity()).isZero();
    }
}
