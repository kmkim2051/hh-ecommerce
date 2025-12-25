package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemRepository;
import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.CouponCommandService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponRepository;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderRepository;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("OrderService 동시성 테스트 - Redis 분산락")
class OrderServiceConcurrencyTest extends TestContainersConfig {
//
//    @Autowired
//    private OrderCommandService orderCommandService;
//    @Autowired
//    private OrderRepository orderRepository;
//    @Autowired
//    private ProductService productService;
//    @Autowired
//    private ProductRepository productRepository;
//    @Autowired
//    private CartService cartService;
//    @Autowired
//    private CartItemRepository cartItemRepository;
//    @Autowired
//    private PointService pointService;
//    @Autowired
//    private PointRepository pointRepository;
//    @Autowired
//    private CouponCommandService couponCommandService;
//    @Autowired
//    private CouponRepository couponRepository;
//
//    @BeforeEach
//    void setUp() {
//        cartItemRepository.deleteAll();
//        orderRepository.deleteAll();
//        couponRepository.deleteAll();
//        pointRepository.deleteAll();
//        productRepository.deleteAll();
//    }
//
//    @Test
//    @DisplayName("여러 사용자가 동시에 같은 상품 주문 - 재고 정합성 검증")
//    void concurrentOrderCreation_SameProduct_StockConsistency() throws InterruptedException {
//        // given
//        int initialStock = 10;
//        int concurrentUsers = 20;
//        BigDecimal productPrice = BigDecimal.valueOf(10000);
//
//        // 상품 생성
//        Product product = Product.create("동시성 테스트 상품", "테스트용", productPrice, initialStock);
//        product = productRepository.save(product);
//        final Long productId = product.getId();
//
//        // 각 사용자별 포인트 충전 및 장바구니 생성
//        List<Long> cartItemIds = new ArrayList<>();
//        for (int i = 1; i <= concurrentUsers; i++) {
//            Long userId = (long) i;
//
//            // 포인트 충전
//            pointService.chargePoint(userId, BigDecimal.valueOf(50000));
//
//            // 장바구니에 상품 추가
//            CartItem cartItem = CartItem.create(userId, productId, 1);
//            cartItem = cartItemRepository.save(cartItem);
//            cartItemIds.add(cartItem.getId());
//        }
//
//        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
//        CountDownLatch latch = new CountDownLatch(concurrentUsers);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        // when - 20명이 동시에 주문 (재고는 10개)
//        IntStream.range(0, concurrentUsers).forEach(i -> {
//            executorService.submit(() -> {
//                try {
//                    Long userId = (long) (i + 1);
//                    Long cartItemId = cartItemIds.get(i);
//
//                    CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
//                    orderCommandService.createOrder(userId, command);
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//                } finally {
//                    latch.countDown();
//                }
//            });
//        });
//
//        latch.await(60, TimeUnit.SECONDS);
//        executorService.shutdown();
//        executorService.awaitTermination(60, TimeUnit.SECONDS);
//
//        // then
//        System.out.println("=== 주문 동시성 테스트 결과 ===");
//        System.out.println("성공: " + successCount.get());
//        System.out.println("실패: " + failCount.get());
//
//        // 정확히 10개만 주문 성공
//        assertThat(successCount.get()).isEqualTo(initialStock);
//        assertThat(failCount.get()).isEqualTo(concurrentUsers - initialStock);
//
//        // 재고 확인
//        Product finalProduct = productRepository.findById(productId).orElseThrow();
//        assertThat(finalProduct.getStockQuantity()).isEqualTo(0);
//    }
//
//    @Test
//    @DisplayName("한 사용자가 동시에 중복 주문 시도 - 포인트 잔액 정합성")
//    void concurrentOrderCreation_SameUser_PointConsistency() throws InterruptedException {
//        // given
//        Long userId = 1L;
//        BigDecimal initialBalance = BigDecimal.valueOf(15000);
//        BigDecimal productPrice = BigDecimal.valueOf(10000);
//
//        // 포인트 충전
//        pointService.chargePoint(userId, initialBalance);
//
//        // 상품 생성 (재고 충분)
//        Product product = Product.create("중복 주문 테스트 상품", "테스트용", productPrice, 100);
//        product = productRepository.save(product);
//        final Long productId = product.getId();
//
//        // 장바구니에 여러 상품 추가
//        int attemptCount = 3;
//        List<Long> cartItemIds = new ArrayList<>();
//        for (int i = 0; i < attemptCount; i++) {
//            CartItem cartItem = CartItem.create(userId, productId, 1);
//            cartItem = cartItemRepository.save(cartItem);
//            cartItemIds.add(cartItem.getId());
//        }
//
//        ExecutorService executorService = Executors.newFixedThreadPool(attemptCount);
//        CountDownLatch latch = new CountDownLatch(attemptCount);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        // when - 같은 사용자가 동시에 3번 주문 시도 (포인트는 15000원, 상품가격 10000원)
//        IntStream.range(0, attemptCount).forEach(i -> {
//            executorService.submit(() -> {
//                try {
//                    Long cartItemId = cartItemIds.get(i);
//                    CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
//                    orderCommandService.createOrder(userId, command);
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//                } finally {
//                    latch.countDown();
//                }
//            });
//        });
//
//        latch.await(30, TimeUnit.SECONDS);
//        executorService.shutdown();
//        executorService.awaitTermination(30, TimeUnit.SECONDS);
//
//        // then
//        System.out.println("=== 중복 주문 방지 테스트 결과 ===");
//        System.out.println("성공: " + successCount.get());
//        System.out.println("실패: " + failCount.get());
//
//        // 포인트 15000원으로 10000원 상품 1개만 주문 가능
//        assertThat(successCount.get()).isEqualTo(1);
//        assertThat(failCount.get()).isEqualTo(2);
//
//        // 포인트 잔액 확인
//        Point finalPoint = pointService.getPoint(userId);
//        assertThat(finalPoint.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000));
//    }
//
//    @Test
//    @DisplayName("여러 사용자가 쿠폰 사용하여 동시 주문 - 쿠폰 사용 정합성")
//    void concurrentOrderCreation_WithCoupon_CouponConsistency() throws InterruptedException {
//        // given
//        int userCount = 5;
//        BigDecimal productPrice = BigDecimal.valueOf(20000);
//        BigDecimal couponDiscount = BigDecimal.valueOf(5000);
//
//        // 쿠폰 생성
//        Coupon coupon = Coupon.create(
//            "동시성 테스트 쿠폰",
//            couponDiscount,
//            userCount,
//            LocalDateTime.now(),
//            LocalDateTime.now().plusDays(30)
//        );
//        coupon = couponRepository.save(coupon);
//        final Long couponId = coupon.getId();
//
//        // 상품 생성
//        Product product = Product.create("쿠폰 테스트 상품", "테스트용", productPrice, 100);
//        product = productRepository.save(product);
//        final Long productId = product.getId();
//
//        // 각 사용자별 쿠폰 발급, 포인트 충전, 장바구니 생성
//        List<Long> cartItemIds = new ArrayList<>();
//        for (int i = 1; i <= userCount; i++) {
//            Long userId = (long) i;
//
//            // 쿠폰 발급
//            couponCommandService.issueCoupon(userId, couponId);
//
//            // 포인트 충전 (할인 후 가격인 15000원 이상)
//            pointService.chargePoint(userId, BigDecimal.valueOf(20000));
//
//            // 장바구니에 상품 추가
//            CartItem cartItem = CartItem.create(userId, productId, 1);
//            cartItem = cartItemRepository.save(cartItem);
//            cartItemIds.add(cartItem.getId());
//        }
//
//        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
//        CountDownLatch latch = new CountDownLatch(userCount);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        // when - 5명이 동시에 쿠폰 사용하여 주문
//        IntStream.range(0, userCount).forEach(i -> {
//            executorService.submit(() -> {
//                try {
//                    Long userId = (long) (i + 1);
//                    Long cartItemId = cartItemIds.get(i);
//
//                    CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), couponId);
//                    Order order = orderCommandService.createOrder(userId, command);
//                    successCount.incrementAndGet();
//
//                    // 할인이 적용되었는지 확인
//                    assertThat(order.getDiscountAmount()).isEqualByComparingTo(couponDiscount);
//                    assertThat(order.getTotalAmount()).isEqualByComparingTo(productPrice);
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//                    System.err.println("주문 실패: " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            });
//        });
//
//        latch.await(60, TimeUnit.SECONDS);
//        executorService.shutdown();
//        executorService.awaitTermination(60, TimeUnit.SECONDS);
//
//        // then
//        System.out.println("=== 쿠폰 사용 동시 주문 테스트 결과 ===");
//        System.out.println("성공: " + successCount.get());
//        System.out.println("실패: " + failCount.get());
//
//        // 모두 성공해야 함 (각자 다른 쿠폰 사용)
//        assertThat(successCount.get()).isEqualTo(userCount);
//        assertThat(failCount.get()).isEqualTo(0);
//    }
//
//    @Test
//    @DisplayName("여러 상품 주문 시 다중 락 획득 - 데드락 방지")
//    void concurrentOrderCreation_MultipleProducts_DeadlockPrevention() throws InterruptedException {
//        // given
//        int userCount = 10;
//        BigDecimal productPrice = BigDecimal.valueOf(5000);
//
//        // 여러 상품 생성
//        Product product1 = Product.create("상품1", "테스트용", productPrice, 100);
//        product1 = productRepository.save(product1);
//        final Long productId1 = product1.getId();
//
//        Product product2 = Product.create("상품2", "테스트용", productPrice, 100);
//        product2 = productRepository.save(product2);
//        final Long productId2 = product2.getId();
//
//        Product product3 = Product.create("상품3", "테스트용", productPrice, 100);
//        product3 = productRepository.save(product3);
//        final Long productId3 = product3.getId();
//
//        // 각 사용자별 포인트 충전 및 장바구니 생성
//        List<List<Long>> userCartItems = new ArrayList<>();
//        for (int i = 1; i <= userCount; i++) {
//            Long userId = (long) i;
//
//            // 포인트 충전
//            pointService.chargePoint(userId, BigDecimal.valueOf(50000));
//
//            // 장바구니에 여러 상품 추가
//            List<Long> cartItemIds = new ArrayList<>();
//
//            // 사용자별로 다른 조합의 상품 추가 (락 순서 다름)
//            if (i % 3 == 0) {
//                cartItemIds.add(cartItemRepository.save(CartItem.create(userId, productId1, 1)).getId());
//                cartItemIds.add(cartItemRepository.save(CartItem.create(userId, productId2, 1)).getId());
//            } else if (i % 3 == 1) {
//                cartItemIds.add(cartItemRepository.save(CartItem.create(userId, productId2, 1)).getId());
//                cartItemIds.add(cartItemRepository.save(CartItem.create(userId, productId3, 1)).getId());
//            } else {
//                cartItemIds.add(cartItemRepository.save(CartItem.create(userId, productId3, 1)).getId());
//                cartItemIds.add(cartItemRepository.save(CartItem.create(userId, productId1, 1)).getId());
//            }
//
//            userCartItems.add(cartItemIds);
//        }
//
//        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
//        CountDownLatch latch = new CountDownLatch(userCount);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        // when - 10명이 동시에 여러 상품 주문 (락 순서가 다름)
//        IntStream.range(0, userCount).forEach(i -> {
//            executorService.submit(() -> {
//                try {
//                    Long userId = (long) (i + 1);
//                    List<Long> cartItemIds = userCartItems.get(i);
//
//                    CreateOrderCommand command = new CreateOrderCommand(cartItemIds, null);
//                    orderCommandService.createOrder(userId, command);
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//                    System.err.println("주문 실패: " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            });
//        });
//
//        latch.await(60, TimeUnit.SECONDS);
//        executorService.shutdown();
//        executorService.awaitTermination(60, TimeUnit.SECONDS);
//
//        // then - 데드락 없이 모두 성공
//        System.out.println("=== 다중 상품 주문 데드락 방지 테스트 결과 ===");
//        System.out.println("성공: " + successCount.get());
//        System.out.println("실패: " + failCount.get());
//
//        assertThat(successCount.get()).isEqualTo(userCount);
//        assertThat(failCount.get()).isEqualTo(0);
//    }
//
//    @Test
//    @DisplayName("한 사용자가 같은 쿠폰으로 여러 주문 동시 시도 - 쿠폰 중복 사용 방지")
//    void concurrentOrderCreation_SameCoupon_PreventDuplicateUsage() throws InterruptedException {
//        // given
//        Long userId = 1L;
//        BigDecimal productPrice = BigDecimal.valueOf(10000);
//        BigDecimal couponDiscount = BigDecimal.valueOf(3000);
//        int attemptCount = 5;
//
//        // 쿠폰 생성 (1개만)
//        Coupon coupon = Coupon.create(
//            "중복 사용 방지 테스트 쿠폰",
//            couponDiscount,
//            1,
//            LocalDateTime.now(),
//            LocalDateTime.now().plusDays(30)
//        );
//        coupon = couponRepository.save(coupon);
//        final Long couponId = coupon.getId();
//
//        // 사용자에게 쿠폰 발급
//        couponCommandService.issueCoupon(userId, couponId);
//
//        // 충분한 포인트 충전
//        pointService.chargePoint(userId, BigDecimal.valueOf(100000));
//
//        // 상품 생성 (재고 충분)
//        Product product = Product.create("중복 쿠폰 테스트 상품", "테스트용", productPrice, 100);
//        product = productRepository.save(product);
//        final Long productId = product.getId();
//
//        // 장바구니에 여러 상품 추가
//        List<Long> cartItemIds = new ArrayList<>();
//        for (int i = 0; i < attemptCount; i++) {
//            CartItem cartItem = CartItem.create(userId, productId, 1);
//            cartItem = cartItemRepository.save(cartItem);
//            cartItemIds.add(cartItem.getId());
//        }
//
//        ExecutorService executorService = Executors.newFixedThreadPool(attemptCount);
//        CountDownLatch latch = new CountDownLatch(attemptCount);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        // when - 같은 사용자가 같은 쿠폰으로 5번 주문 시도
//        IntStream.range(0, attemptCount).forEach(i -> {
//            executorService.submit(() -> {
//                try {
//                    Long cartItemId = cartItemIds.get(i);
//                    CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), couponId);
//                    Order order = orderCommandService.createOrder(userId, command);
//                    successCount.incrementAndGet();
//
//                    // 할인이 적용되었는지 확인
//                    assertThat(order.getDiscountAmount()).isEqualByComparingTo(couponDiscount);
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//                }  finally {
//                    latch.countDown();
//                }
//            });
//        });
//
//        latch.await(30, TimeUnit.SECONDS);
//        executorService.shutdown();
//        executorService.awaitTermination(30, TimeUnit.SECONDS);
//
//        // then
//        System.out.println("=== 쿠폰 중복 사용 방지 테스트 결과 ===");
//        System.out.println("성공: " + successCount.get());
//        System.out.println("실패: " + failCount.get());
//
//        // 쿠폰은 1번만 사용 가능해야 함
//        assertThat(successCount.get()).isEqualTo(1);
//        assertThat(failCount.get()).isEqualTo(attemptCount - 1);
//
//        // 성공한 주문 1개, 실패한 주문 4개
//        List<Order> orders = orderRepository.findAll();
//        assertThat(orders).hasSize(1);
//    }
//
//    @Test
//    @DisplayName("재고 부족 상황에서 수량이 다른 주문들의 동시 경쟁")
//    void concurrentOrderCreation_DifferentQuantities_StockCompetition() throws InterruptedException {
//        // given
//        int initialStock = 10;
//        BigDecimal productPrice = BigDecimal.valueOf(5000);
//
//        // 상품 생성
//        Product product = Product.create("수량 경쟁 테스트 상품", "테스트용", productPrice, initialStock);
//        product = productRepository.save(product);
//        final Long productId = product.getId();
//
//        // 사용자 A: 8개 주문 시도
//        Long userA = 1L;
//        pointService.chargePoint(userA, BigDecimal.valueOf(100000));
//        CartItem cartItemA = CartItem.create(userA, productId, 8);
//        cartItemA = cartItemRepository.save(cartItemA);
//        final Long cartItemIdA = cartItemA.getId();
//
//        // 사용자 B, C, D: 각각 3개씩 주문 시도
//        List<Long> otherUserIds = List.of(2L, 3L, 4L);
//        List<Long> otherCartItemIds = new ArrayList<>();
//        for (Long userId : otherUserIds) {
//            pointService.chargePoint(userId, BigDecimal.valueOf(100000));
//            CartItem cartItem = CartItem.create(userId, productId, 3);
//            cartItem = cartItemRepository.save(cartItem);
//            otherCartItemIds.add(cartItem.getId());
//        }
//
//        ExecutorService executorService = Executors.newFixedThreadPool(4);
//        CountDownLatch latch = new CountDownLatch(4);
//
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount = new AtomicInteger(0);
//
//        AtomicInteger totalOrderedQuantity = new AtomicInteger(0);
//
//        // when - 4명이 동시에 서로 다른 수량 주문 (총 요청: 8 + 3*3 = 17개, 실제 재고: 10개)
//        // 사용자 A의 8개 주문
//        executorService.submit(() -> {
//            final int orderAmount = 8;
//            try {
//                CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemIdA), null);
//                orderCommandService.createOrder(userA, command);
//                successCount.incrementAndGet();
//                totalOrderedQuantity.addAndGet(orderAmount);
//            } catch (Exception e) {
//                failCount.incrementAndGet();
//            } finally {
//                latch.countDown();
//            }
//        });
//
//        // 사용자 B, C, D의 소량(3개) 주문
//        for (int i = 0; i < otherUserIds.size(); i++) {
//            final int index = i;
//            executorService.submit(() -> {
//                final int orderAmount = 3;
//                try {
//                    Long userId = otherUserIds.get(index);
//                    Long cartItemId = otherCartItemIds.get(index);
//
//
//                    CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
//                    orderCommandService.createOrder(userId, command);
//                    successCount.incrementAndGet();
//                    totalOrderedQuantity.addAndGet(orderAmount);
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await(60, TimeUnit.SECONDS);
//        executorService.shutdown();
//        executorService.awaitTermination(60, TimeUnit.SECONDS);
//
//        // then
//        System.out.println("=== 수량 경쟁 테스트 결과 ===");
//        System.out.println("성공: " + successCount.get());
//        System.out.println("실패: " + failCount.get());
//        System.out.println("총 주문 수량: " + totalOrderedQuantity.get());
//
//        // 재고 정확히 소진되어야 함
//        Product finalProduct = productRepository.findById(productId).orElseThrow();
//        assertThat(finalProduct.getStockQuantity()).isEqualTo(initialStock - totalOrderedQuantity.get());
//
//        // 일부만 성공, 일부는 실패해야 함
//        assertThat(successCount.get()).isLessThan(4);
//        assertThat(failCount.get()).isGreaterThan(0);
//    }
}
