package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.application.dto.OrderPreparationResult;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.CouponCommandService;
import com.hh.ecom.coupon.application.CouponQueryService;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.domain.*;
import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.outbox.domain.MessagePublisher;
import com.hh.ecom.outbox.domain.OutboxEventRepository;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.application.SalesRankingRepository;
import com.hh.ecom.product.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("OrderService Event 기반 로직 통합 테스트 (Service + Repository)")
class OrderServiceEventIntegrationTest extends TestContainersConfig {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private OrderCommandService orderCommandService;
    @Autowired
    private OrderQueryService orderQueryService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private SalesRankingRepository salesRankingRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    @Qualifier("customStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private MessagePublisher messagePublisher;
    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private CouponQueryService couponQueryService;
    @MockitoBean
    private CouponCommandService couponCommandService;
    @MockitoBean
    private PointService pointService;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        outboxEventRepository.deleteAll();
        cleanupRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanupRedisKeys();
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 시 이벤트를 통해 Redis 전체 기간 랭킹 기록")
    void integration_CreateOrder_RecordsAllTimeRanking() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;
        Integer quantity = 1;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        // when
        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        // then - 주문 생성 성공
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // 이벤트 리스너가 비동기로 실행되므로 약간의 대기
        Thread.sleep(200);

        // then - Redis 랭킹이 이벤트 리스너에 의해 기록됨
        String allTimeKey = "product:ranking:sales:all";
        Double allTimeScore = redisTemplate.opsForZSet().score(allTimeKey, productId.toString());
        assertThat(allTimeScore).isNotNull();
        assertThat(allTimeScore.intValue()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 시 이벤트를 통해 Redis 일별 랭킹 기록 및 TTL 설정")
    void integration_CreateOrder_RecordsDailyRankingWithTTL() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;
        Integer quantity = 1;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        // when
        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        orderCommandService.createOrder(userId, command);

        // 이벤트 리스너 실행 대기
        Thread.sleep(200);

        // then - 일별 랭킹 기록 확인
        String today = LocalDate.now().format(DAY_FORMAT);
        String dailyKey = "product:ranking:sales:daily:" + today;
        Double dailyScore = redisTemplate.opsForZSet().score(dailyKey, productId.toString());
        assertThat(dailyScore).isNotNull();
        assertThat(dailyScore.intValue()).isEqualTo(quantity);

        // then - TTL 설정 확인
        Long ttl = redisTemplate.getExpire(dailyKey, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L); // 30일 TTL
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 시 Kafka 이벤트가 트랜잭션 커밋 후 발행됨")
    void integration_CreateOrder_PublishesKafkaEventAfterCommit() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        // when
        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        // then - 주문이 성공적으로 생성됨
        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // @TransactionalEventListener(AFTER_COMMIT)는 트랜잭션 커밋 후 동기적으로 실행되지만 약간의 대기가 필요할 수 있음
        Thread.sleep(200);

        // then - MessagePublisher의 publish가 호출되었는지 확인
        verify(messagePublisher, times(1)).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(createdOrder.getId().toString()),
                any(OrderCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("통합 테스트 - 여러 주문 생성 시 각각 독립적으로 Kafka 이벤트 발행")
    void integration_CreateMultipleOrders_PublishesIndependentKafkaEvents() throws InterruptedException {
        // given
        Long userId1 = 1L;
        Long userId2 = 2L;
        Long cartItemId1 = 100L;
        Long cartItemId2 = 200L;
        Long productId1 = 1000L;
        Long productId2 = 2000L;

        setupMocksForUser(userId1, cartItemId1, productId1, "상품1", BigDecimal.valueOf(10000));
        setupMocksForUser(userId2, cartItemId2, productId2, "상품2", BigDecimal.valueOf(20000));

        // when - 두 개의 주문 생성
        CreateOrderCommand command1 = new CreateOrderCommand(List.of(cartItemId1), null);
        CreateOrderCommand command2 = new CreateOrderCommand(List.of(cartItemId2), null);

        Order order1 = orderCommandService.createOrder(userId1, command1);
        Order order2 = orderCommandService.createOrder(userId2, command2);

        Thread.sleep(200);

        // then - 각 주문마다 독립적으로 Kafka 이벤트 발행 확인
        verify(messagePublisher).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(order1.getId().toString()),
                any(OrderCompletedEvent.class)
        );
        verify(messagePublisher).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(order2.getId().toString()),
                any(OrderCompletedEvent.class)
        );
        verify(messagePublisher, times(2)).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                anyString(),
                any(OrderCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 시 Kafka 이벤트는 한 번만 발행됨 (중복 방지)")
    void integration_CreateOrder_KafkaEventPublishedOnlyOnce() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        // when - 주문 생성
        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        Thread.sleep(200);

        // then - Kafka 이벤트가 정확히 1번만 발행됨
        verify(messagePublisher, times(1)).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(createdOrder.getId().toString()),
                any(OrderCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 시 Kafka 이벤트 발행 확인")
    void integration_CreateOrder_PublishesKafkaEvent() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        // when
        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        Thread.sleep(200);

        // then - Kafka 이벤트가 발행되었는지 확인
        verify(messagePublisher).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(createdOrder.getId().toString()),
                any(OrderCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("통합 테스트 - 동일 사용자의 여러 주문 각각 Kafka 이벤트 발행")
    void integration_CreateMultipleOrdersBySameUser_EachPublishesKafkaEvent() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId1 = 100L;
        Long cartItemId2 = 200L;
        Long productId1 = 1000L;
        Long productId2 = 2000L;

        setupMocksForUser(userId, cartItemId1, productId1, "상품1", BigDecimal.valueOf(10000));
        setupMocksForUser(userId, cartItemId2, productId2, "상품2", BigDecimal.valueOf(20000));

        // when - 같은 사용자가 두 번 주문
        CreateOrderCommand command1 = new CreateOrderCommand(List.of(cartItemId1), null);
        CreateOrderCommand command2 = new CreateOrderCommand(List.of(cartItemId2), null);

        Order order1 = orderCommandService.createOrder(userId, command1);
        Order order2 = orderCommandService.createOrder(userId, command2);

        Thread.sleep(200);

        // then - 각 주문마다 Kafka 이벤트 발행
        verify(messagePublisher).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(order1.getId().toString()),
                any(OrderCompletedEvent.class)
        );
        verify(messagePublisher).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(order2.getId().toString()),
                any(OrderCompletedEvent.class)
        );
        verify(messagePublisher, times(2)).publish(
                eq(KafkaTopics.ORDER_COMPLETED),
                anyString(),
                any(OrderCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 시 이벤트로 판매 랭킹이 1회만 기록됨 (중복 방지)")
    void integration_UpdateOrderStatus_PreventsDuplicateRecording() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;
        Integer quantity = 1;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        // 이벤트 리스너 실행 대기
        Thread.sleep(200);

        String allTimeKey = "product:ranking:sales:all";
        String today = LocalDate.now().format(DAY_FORMAT);
        String dailyKey = "product:ranking:sales:daily:" + today;

        // when - COMPLETED로 상태 변경 (현재는 이벤트를 발행하지 않음)
        Order completedOrder = orderCommandService.updateOrderStatus(createdOrder.getId(), OrderStatus.COMPLETED);

        // then - 주문 상태 확인
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // then - 랭킹은 여전히 1회만 기록됨 (주문 생성 시 1회)
        Double allTimeScore = redisTemplate.opsForZSet().score(allTimeKey, productId.toString());
        Double dailyScore = redisTemplate.opsForZSet().score(dailyKey, productId.toString());
        assertThat(allTimeScore.intValue()).isEqualTo(quantity);
        assertThat(dailyScore.intValue()).isEqualTo(quantity);

        // then - DB 조회 확인
        Order retrievedOrder = orderQueryService.getOrderById(createdOrder.getId());
        assertThat(retrievedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("통합 테스트 - Redis에 존재하지 않는 상품 ID 조회")
    void integration_RedisRanking_NonExistentProduct() {
        // given
        String allTimeKey = "product:ranking:sales:all";
        Long nonExistentProductId = 99999L;

        // when
        Double score = redisTemplate.opsForZSet().score(allTimeKey, nonExistentProductId.toString());

        // then
        assertThat(score).isNull();
    }

    @Test
    @DisplayName("통합 테스트 - 한 주문에 여러 상품 포함 시 이벤트로 각 상품별 수량 기록")
    void integration_RedisRanking_MultipleProductsInOneOrder() throws InterruptedException {
        // given
        Long userId = 1L;
        Long productId1 = 2001L;
        Long productId2 = 2002L;

        CartItem cartItem1 = createCartItem(101L, userId, productId1, 3);
        CartItem cartItem2 = createCartItem(102L, userId, productId2, 5);
        Product product1 = createProduct(productId1, "상품1", BigDecimal.valueOf(10000), 100);
        Product product2 = createProduct(productId2, "상품2", BigDecimal.valueOf(20000), 100);

        lenient().when(cartService.getCartItemById(101L)).thenReturn(cartItem1);
        lenient().when(cartService.getCartItemById(102L)).thenReturn(cartItem2);
        lenient().when(cartService.prepareOrderFromCart(userId, List.of(101L, 102L)))
                .thenReturn(OrderPreparationResult.of(
                        List.of(cartItem1, cartItem2),
                        BigDecimal.valueOf(130000),
                        List.of(productId1, productId2),
                        Map.of(productId1, 3, productId2, 5)
                ));
        lenient().when(productService.getProductList(List.of(productId1, productId2)))
                .thenReturn(List.of(product1, product2));
        lenient().when(pointService.hasPointAccount(userId)).thenReturn(true);
        lenient().when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(10000000)));
        lenient().doNothing().when(cartService).completeOrderCheckout(anyLong(), anyList());

        // when
        CreateOrderCommand command = new CreateOrderCommand(List.of(101L, 102L), null);
        orderCommandService.createOrder(userId, command);

        // 이벤트 리스너 실행 대기
        Thread.sleep(200);

        // then - 각 상품별 수량이 정확히 기록됨
        String allTimeKey = "product:ranking:sales:all";
        Double product1Score = redisTemplate.opsForZSet().score(allTimeKey, productId1.toString());
        Double product2Score = redisTemplate.opsForZSet().score(allTimeKey, productId2.toString());

        assertThat(product1Score.intValue()).isEqualTo(3);
        assertThat(product2Score.intValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("통합 테스트 - 동일 상품 여러 주문 시 이벤트로 판매량 누적")
    void integration_RedisRanking_AccumulatesSameProduct() throws InterruptedException {
        // given
        Long userId = 1L;
        Long productId = 3000L;
        Product product = createProduct(productId, "상품", BigDecimal.valueOf(10000), 100);

        // 첫 번째 주문: 3개
        CartItem cartItem1 = createCartItem(201L, userId, productId, 3);
        lenient().when(cartService.getCartItemById(201L)).thenReturn(cartItem1);
        lenient().when(cartService.prepareOrderFromCart(userId, List.of(201L)))
                .thenReturn(OrderPreparationResult.of(
                        List.of(cartItem1),
                        BigDecimal.valueOf(30000),
                        List.of(productId),
                        Map.of(productId, 3)
                ));
        lenient().when(productService.getProductList(List.of(productId))).thenReturn(List.of(product));
        lenient().when(pointService.hasPointAccount(userId)).thenReturn(true);
        lenient().when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(10000000)));
        lenient().doNothing().when(cartService).completeOrderCheckout(anyLong(), anyList());

        orderCommandService.createOrder(userId, new CreateOrderCommand(List.of(201L), null));
        Thread.sleep(200); // 첫 번째 이벤트 처리 대기

        // 두 번째 주문: 2개
        CartItem cartItem2 = createCartItem(202L, userId, productId, 2);
        lenient().when(cartService.getCartItemById(202L)).thenReturn(cartItem2);
        lenient().when(cartService.prepareOrderFromCart(userId, List.of(202L)))
                .thenReturn(OrderPreparationResult.of(
                        List.of(cartItem2),
                        BigDecimal.valueOf(20000),
                        List.of(productId),
                        Map.of(productId, 2)
                ));

        // when
        orderCommandService.createOrder(userId, new CreateOrderCommand(List.of(202L), null));
        Thread.sleep(200); // 두 번째 이벤트 처리 대기

        // then - 누적 확인 (3 + 2 = 5)
        String allTimeKey = "product:ranking:sales:all";
        Double accumulatedScore = redisTemplate.opsForZSet().score(allTimeKey, productId.toString());
        assertThat(accumulatedScore.intValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("통합 테스트 - 주문 완료 이벤트로 판매 랭킹이 독립적으로 기록됨")
    void integration_SalesRankingEventListener_RecordsIndependently() throws InterruptedException {
        // given
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;
        Integer quantity = 1; // setupMocksForUser는 quantity 1로 모킹함

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        // when - 주문 생성 (이벤트 발행)
        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        // then - 주문은 즉시 성공
        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // 이벤트 리스너가 독립적으로 실행됨
        Thread.sleep(200);

        // then - 판매 랭킹이 이벤트 리스너에 의해 기록됨
        String allTimeKey = "product:ranking:sales:all";
        String today = LocalDate.now().format(DAY_FORMAT);
        String dailyKey = "product:ranking:sales:daily:" + today;

        Double allTimeScore = redisTemplate.opsForZSet().score(allTimeKey, productId.toString());
        Double dailyScore = redisTemplate.opsForZSet().score(dailyKey, productId.toString());

        assertThat(allTimeScore).isNotNull();
        assertThat(dailyScore).isNotNull();
        assertThat(allTimeScore.intValue()).isEqualTo(quantity);
        assertThat(dailyScore.intValue()).isEqualTo(quantity);

        // then - 중복 기록 방지를 위한 Redis Set 확인
        String recordedKey = "product:ranking:sales:recorded:" + createdOrder.getId();
        Boolean isRecorded = redisTemplate.hasKey(recordedKey);
        assertThat(isRecorded).isTrue();
    }

    @Test
    @DisplayName("통합 테스트 - 잘못된 Redis Key 조회 시 빈 결과 반환")
    void integration_RedisRanking_InvalidKey() {
        // given
        String invalidKey = "product:ranking:sales:invalid:key";

        // when
        Long size = redisTemplate.opsForZSet().size(invalidKey);

        // then
        assertThat(size).isEqualTo(0L);
    }

    private void setupMocksForUser(Long userId, Long cartItemId, Long productId, String productName, BigDecimal price) {
        CartItem cartItem = createCartItem(cartItemId, userId, productId, 1);
        Product product = createProduct(productId, productName, price, 100);

        lenient().when(cartService.getCartItemById(cartItemId)).thenReturn(cartItem);

        OrderPreparationResult preparationResult = OrderPreparationResult.of(
                List.of(cartItem),
                price,
                List.of(productId),
                Map.of(productId, 1)
        );
        lenient().when(cartService.prepareOrderFromCart(userId, List.of(cartItemId))).thenReturn(preparationResult);

        lenient().when(productService.getProductList(List.of(productId))).thenReturn(List.of(product));
        lenient().when(pointService.hasPointAccount(userId)).thenReturn(true);
        lenient().when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(10000000)));
        lenient().doNothing().when(cartService).completeOrderCheckout(anyLong(), anyList());
    }

    private CartItem createCartItem(Long id, Long userId, Long productId, Integer quantity) {
        return CartItem.builder()
                .id(id)
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Product createProduct(Long id, String name, BigDecimal price, Integer stock) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("상품 설명")
                .price(price)
                .stockQuantity(stock)
                .viewCount(0)
                .isActive(true)
                .deletedAt(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Point createPoint(Long userId, BigDecimal balance) {
        return Point.builder()
                .id(userId)
                .userId(userId)
                .balance(balance)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void cleanupRedisKeys() {
        try {
            // Clean up sales ranking keys
            Set<String> rankingKeys = redisTemplate.keys("product:ranking:sales:*");
            if (!rankingKeys.isEmpty()) {
                redisTemplate.delete(rankingKeys);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
