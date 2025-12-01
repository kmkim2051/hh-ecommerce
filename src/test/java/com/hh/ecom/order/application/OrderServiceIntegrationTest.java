package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.application.dto.OrderPreparationResult;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.coupon.application.CouponCommandService;
import com.hh.ecom.coupon.application.CouponQueryService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponStatus;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.domain.*;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("OrderService 통합 테스트 (Service + Repository)")
class OrderServiceIntegrationTest extends TestContainersConfig {

    @Autowired
    private OrderCommandService orderCommandService;
    @Autowired
    private OrderQueryService orderQueryService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

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
    }

    @Test
    @DisplayName("통합 테스트 - 주문 생성 후 조회")
    void integration_CreateAndGetOrder() {
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        CartItem cartItem = createCartItem(cartItemId, userId, productId, 2);
        Product product = createProduct(productId, "노트북", BigDecimal.valueOf(1500000), 10);

        when(cartService.getCartItemById(cartItemId)).thenReturn(cartItem);

        // CartService.prepareOrderFromCart 모킹
        OrderPreparationResult preparationResult = OrderPreparationResult.of(
                List.of(cartItem),
                BigDecimal.valueOf(3000000),
                List.of(productId),
                Map.of(productId, 2)
        );
        when(cartService.prepareOrderFromCart(userId, List.of(cartItemId))).thenReturn(preparationResult);

        when(productService.getProductList(List.of(productId))).thenReturn(List.of(product));
        when(pointService.hasPointAccount(userId)).thenReturn(true);
        when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(5000000)));
        when(pointService.usePointWithinTransaction(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(null);
        doNothing().when(cartService).completeOrderCheckout(anyLong(), anyList());

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);

        Order createdOrder = orderCommandService.createOrder(userId, command);

        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getId()).isNotNull();
        assertThat(createdOrder.getUserId()).isEqualTo(userId);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(createdOrder.getTotalAmount()).isEqualTo(BigDecimal.valueOf(3000000));
        assertThat(createdOrder.getDiscountAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(createdOrder.getFinalAmount()).isEqualTo(BigDecimal.valueOf(3000000));
        assertThat(createdOrder.getOrderItems()).hasSize(1);

        Order retrievedOrder = orderQueryService.getOrder(createdOrder.getId(), userId);

        assertThat(retrievedOrder.getId()).isEqualTo(createdOrder.getId());
        assertThat(retrievedOrder.getOrderNumber()).isEqualTo(createdOrder.getOrderNumber());
        assertThat(retrievedOrder.getOrderItems()).hasSize(1);
        assertThat(retrievedOrder.getOrderItems().get(0).getProductName()).isEqualTo("노트북");
    }

    @Test
    @DisplayName("통합 테스트 - 쿠폰 적용 주문 생성")
    void integration_CreateOrderWithCoupon() {
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;
        Long couponId = 10L;
        Long couponUserId = 50L;

        CartItem cartItem = createCartItem(cartItemId, userId, productId, 1);
        Product product = createProduct(productId, "마우스", BigDecimal.valueOf(50000), 20);
        Coupon coupon = createCoupon(couponId, "할인쿠폰", BigDecimal.valueOf(5000));
        CouponUser couponUser = createCouponUser(couponUserId, userId, couponId, false);

        when(cartService.getCartItemById(cartItemId)).thenReturn(cartItem);

        OrderPreparationResult preparationResult = OrderPreparationResult.of(
                List.of(cartItem),
                BigDecimal.valueOf(50000),
                List.of(productId),
                Map.of(productId, 1)
        );
        when(cartService.prepareOrderFromCart(userId, List.of(cartItemId))).thenReturn(preparationResult);

        when(productService.getProductList(List.of(productId))).thenReturn(List.of(product));
        when(couponQueryService.getCoupon(couponId)).thenReturn(coupon);
        when(couponQueryService.getAllMyCoupons(userId)).thenReturn(
                List.of(CouponUserWithCoupon.of(couponUser, coupon))
        );
        when(pointService.hasPointAccount(userId)).thenReturn(true);
        when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(100000)));
        when(pointService.usePointWithinTransaction(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(null);
        when(couponCommandService.useCouponWithinTransaction(anyLong(), anyLong())).thenReturn(null);
        doNothing().when(cartService).completeOrderCheckout(anyLong(), anyList());

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), couponId);

        Order createdOrder = orderCommandService.createOrder(userId, command);

        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getTotalAmount()).isEqualTo(BigDecimal.valueOf(50000));
        assertThat(createdOrder.getDiscountAmount()).isEqualTo(BigDecimal.valueOf(5000));
        assertThat(createdOrder.getFinalAmount()).isEqualTo(BigDecimal.valueOf(45000));
        assertThat(createdOrder.getCouponUserId()).isEqualTo(couponUserId);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        verify(couponCommandService).useCouponWithinTransaction(couponUserId, createdOrder.getId());
        verify(pointService).usePointWithinTransaction(userId, BigDecimal.valueOf(45000), createdOrder.getId());
    }

    @Test
    @DisplayName("통합 테스트 - 여러 사용자의 주문 조회")
    void integration_GetOrdersByMultipleUsers() {
        Long user1 = 1L;
        Long user2 = 2L;
        Long cartItemId1 = 100L;
        Long cartItemId2 = 101L;
        Long productId1 = 1000L;
        Long productId2 = 1001L;

        setupMocksForUser(user1, cartItemId1, productId1, "상품1", BigDecimal.valueOf(10000));
        setupMocksForUser(user2, cartItemId2, productId2, "상품2", BigDecimal.valueOf(20000));

        CreateOrderCommand command1 = new CreateOrderCommand(List.of(cartItemId1), null);
        CreateOrderCommand command2 = new CreateOrderCommand(List.of(cartItemId2), null);

        orderCommandService.createOrder(user1, command1);
        orderCommandService.createOrder(user1, command1);
        orderCommandService.createOrder(user2, command2);

        List<Order> user1Orders = orderQueryService.getOrders(user1);
        List<Order> user2Orders = orderQueryService.getOrders(user2);

        assertThat(user1Orders).hasSize(2);
        assertThat(user2Orders).hasSize(1);
        assertThat(user1Orders).allMatch(order -> order.getUserId().equals(user1));
        assertThat(user2Orders).allMatch(order -> order.getUserId().equals(user2));
    }

    @Test
    @DisplayName("통합 테스트 - 주문 상태 변경")
    void integration_UpdateOrderStatus() {
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Order completedOrder = orderCommandService.updateOrderStatus(createdOrder.getId(), OrderStatus.COMPLETED);

        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        Order retrievedOrder = orderQueryService.getOrderById(createdOrder.getId());
        assertThat(retrievedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("통합 테스트 - 주문 조회 시 권한 검증")
    void integration_UnauthorizedAccess() {
        Long userId = 1L;
        Long otherUserId = 2L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);
        Order createdOrder = orderCommandService.createOrder(userId, command);

        assertThatThrownBy(() -> orderQueryService.getOrder(createdOrder.getId(), otherUserId))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.UNAUTHORIZED_ORDER_ACCESS);
    }

    @Test
    @DisplayName("통합 테스트 - 빈 장바구니로 주문 생성 실패")
    void integration_CreateOrderWithEmptyCart() {
        Long userId = 1L;
        CreateOrderCommand command = new CreateOrderCommand(List.of(), null);

        assertThatThrownBy(() -> orderCommandService.createOrder(userId, command))
                .isInstanceOf(OrderException.class)
                .extracting(ex -> ((OrderException) ex).getErrorCode())
                .isEqualTo(OrderErrorCode.EMPTY_ORDER_CART_ITEM);

        List<Order> orders = orderQueryService.getOrders(userId);
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("통합 테스트 - 포인트 부족으로 주문 생성 실패")
    void integration_CreateOrderWithInsufficientBalance() {
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        CartItem cartItem = createCartItem(cartItemId, userId, productId, 1);
        Product product = createProduct(productId, "고가상품", BigDecimal.valueOf(1000000), 10);

        when(cartService.getCartItemById(cartItemId)).thenReturn(cartItem);

        OrderPreparationResult preparationResult = OrderPreparationResult.of(
                List.of(cartItem),
                BigDecimal.valueOf(1000000),
                List.of(productId),
                Map.of(productId, 1)
        );
        when(cartService.prepareOrderFromCart(userId, List.of(cartItemId))).thenReturn(preparationResult);

        when(productService.getProductList(List.of(productId))).thenReturn(List.of(product));
        when(pointService.hasPointAccount(userId)).thenReturn(true);
        when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(50000)));

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);

        assertThatThrownBy(() -> orderCommandService.createOrder(userId, command))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("포인트 잔액이 부족합니다");

        List<Order> orders = orderQueryService.getOrders(userId);
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("통합 테스트 - 재고 부족으로 주문 생성 실패")
    void integration_CreateOrderWithInsufficientStock() {
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        // CartService.getCartItemById 모킹 (extractProductIdsFromCart에서 사용)
        CartItem cartItem = createCartItem(cartItemId, userId, productId, 1);
        when(cartService.getCartItemById(cartItemId)).thenReturn(cartItem);

        // prepareOrderFromCart에서 재고 부족 예외 발생
        when(cartService.prepareOrderFromCart(userId, List.of(cartItemId)))
                .thenThrow(new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "재고가 부족합니다"));
        when(pointService.hasPointAccount(userId)).thenReturn(true);

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);

        assertThatThrownBy(() -> orderCommandService.createOrder(userId, command))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("재고가 부족합니다");

        List<Order> orders = orderQueryService.getOrders(userId);
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("통합 테스트 - 주문 번호 고유성")
    void integration_OrderNumberUniqueness() throws InterruptedException {
        Long userId = 1L;
        Long cartItemId = 100L;
        Long productId = 1000L;

        setupMocksForUser(userId, cartItemId, productId, "상품", BigDecimal.valueOf(10000));

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId), null);

        Order order1 = orderCommandService.createOrder(userId, command);
        Thread.sleep(2);
        Order order2 = orderCommandService.createOrder(userId, command);

        assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
        assertThat(order1.getId()).isNotEqualTo(order2.getId());
    }

    @Test
    @DisplayName("통합 테스트 - 여러 상품 주문")
    void integration_CreateOrderWithMultipleItems() {
        Long userId = 1L;
        Long cartItemId1 = 100L;
        Long cartItemId2 = 101L;
        Long productId1 = 1000L;
        Long productId2 = 1001L;

        CartItem cartItem1 = createCartItem(cartItemId1, userId, productId1, 2);
        CartItem cartItem2 = createCartItem(cartItemId2, userId, productId2, 1);
        Product product1 = createProduct(productId1, "노트북", BigDecimal.valueOf(1500000), 10);
        Product product2 = createProduct(productId2, "마우스", BigDecimal.valueOf(50000), 20);

        // CartService.getCartItemById 모킹 (extractProductIdsFromCart에서 사용)
        when(cartService.getCartItemById(cartItemId1)).thenReturn(cartItem1);
        when(cartService.getCartItemById(cartItemId2)).thenReturn(cartItem2);

        // CartService.prepareOrderFromCart 모킹
        OrderPreparationResult preparationResult = OrderPreparationResult.of(
                List.of(cartItem1, cartItem2),
                BigDecimal.valueOf(3050000),
                List.of(productId1, productId2),
                Map.of(productId1, 2, productId2, 1)
        );
        when(cartService.prepareOrderFromCart(userId, List.of(cartItemId1, cartItemId2))).thenReturn(preparationResult);

        when(productService.getProductList(List.of(productId1, productId2)))
                .thenReturn(List.of(product1, product2));
        when(pointService.hasPointAccount(userId)).thenReturn(true);
        when(pointService.getPoint(userId)).thenReturn(createPoint(userId, BigDecimal.valueOf(5000000)));
        when(pointService.usePointWithinTransaction(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(null);
        doNothing().when(cartService).completeOrderCheckout(anyLong(), anyList());

        CreateOrderCommand command = new CreateOrderCommand(List.of(cartItemId1, cartItemId2), null);

        Order createdOrder = orderCommandService.createOrder(userId, command);

        assertThat(createdOrder.getOrderItems()).hasSize(2);
        assertThat(createdOrder.getTotalAmount()).isEqualTo(BigDecimal.valueOf(3050000));
        assertThat(createdOrder.getFinalAmount()).isEqualTo(BigDecimal.valueOf(3050000));

        OrderItem item1 = createdOrder.getOrderItems().get(0);
        OrderItem item2 = createdOrder.getOrderItems().get(1);

        assertThat(item1.getProductName()).isEqualTo("노트북");
        assertThat(item1.getQuantity()).isEqualTo(2);
        assertThat(item2.getProductName()).isEqualTo("마우스");
        assertThat(item2.getQuantity()).isEqualTo(1);
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
        lenient().when(pointService.usePointWithinTransaction(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(null);
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

    private Coupon createCoupon(Long id, String name, BigDecimal discountAmount) {
        return Coupon.builder()
                .id(id)
                .name(name)
                .discountAmount(discountAmount)
                .totalQuantity(100)
                .availableQuantity(50)
                .status(CouponStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CouponUser createCouponUser(Long id, Long userId, Long couponId, boolean isUsed) {
        return CouponUser.builder()
                .id(id)
                .userId(userId)
                .couponId(couponId)
                .orderId(null)
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .expireDate(LocalDateTime.now().plusDays(30))
                .isUsed(isUsed)
                .build();
    }
}
