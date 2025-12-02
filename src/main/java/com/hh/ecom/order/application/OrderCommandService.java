package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.application.dto.OrderPreparationResult;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.common.lock.OrderLockContext;
import com.hh.ecom.common.lock.RedisLockExecutor;
import com.hh.ecom.coupon.application.CouponCommandService;
import com.hh.ecom.coupon.application.CouponQueryService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.application.dto.DiscountInfo;
import com.hh.ecom.order.domain.*;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final CouponQueryService couponQueryService;
    private final CouponCommandService couponCommandService;
    private final PointService pointService;
    private final RedisLockExecutor redisLockExecutor;
    private final TransactionTemplate transactionTemplate;

    public Order createOrder(Long userId, CreateOrderCommand createOrderCommand) {
        createOrderCommand.validate();
        validateOrderPaymentAbility(userId);

        List<Long> cartItemIds = createOrderCommand.cartItemIds();
        log.info("주문 생성 시작합니다. (userId: {}, create command: {})", userId, createOrderCommand);

        List<Long> productIds = extractProductIdsFromCart(cartItemIds);
        Long couponUserId = extractCouponUserId(userId, createOrderCommand.couponId());

        // 주문 내부 분산락 필요 도메인: [Product, Point, Coupon]
        List<String> lockKeys = new OrderLockContext()
            .withUserPoint(userId)
            .withProducts(productIds)
            .withCoupon(couponUserId)
            .buildSortedLockKeys();
        log.debug("분산락 키 생성 완료: keys={}", lockKeys);

        return redisLockExecutor.executeWithLock(lockKeys, () ->
            transactionTemplate.execute(status ->
                executeOrderCreation(userId, createOrderCommand, couponUserId)
            )
        );
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        Order updatedOrder = order.updateStatus(newStatus);
        return orderRepository.save(updatedOrder);
    }

    // ------------------------------ Private Methods ------------------------------
    private Order executeOrderCreation(Long userId, CreateOrderCommand createOrderCommand, Long couponUserId) {
        List<Long> cartItemIds = createOrderCommand.cartItemIds();

        OrderPreparationResult preparationResult = cartService.prepareOrderFromCart(userId, cartItemIds);

        List<CartItem> validatedCartItems = preparationResult.validatedCartItems();
        BigDecimal totalAmount = preparationResult.totalAmount();
        List<Long> productIds = preparationResult.productIds();

        DiscountInfo discountInfo = couponUserId != null
            ? calculateDiscountInfo(userId, createOrderCommand.couponId())
            : DiscountInfo.NONE;
        BigDecimal discountAmount = discountInfo.discountAmount();

        final BigDecimal finalAmount = calculateValidFinalAmount(totalAmount, discountAmount);

        BigDecimal userBalance = Optional.ofNullable(pointService.getPoint(userId))
                .map(Point::getBalance)
                .orElse(BigDecimal.ZERO);

        validateSufficientUserBalance(userBalance, finalAmount);

        final String orderNumber = generateOrderNumber();
        Order order = Order.create(userId, orderNumber, totalAmount, discountAmount, couponUserId);
        Order savedOrder = orderRepository.save(order);

        List<Product> products = productService.getProductList(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItem> savedOrderItems = saveAndGetOrderItemList(validatedCartItems, productMap, savedOrder);

        decreaseProductStockInCart(validatedCartItems, productMap);

        usePoint(userId, finalAmount, savedOrder);

        useCoupon(couponUserId, savedOrder);

        Order paidOrder = savedOrder.processPayment();
        Order updatedOrder = orderRepository.save(paidOrder);

        cartService.completeOrderCheckout(userId, productIds);

        log.info("주문 생성 완료: orderId={}, orderNumber={}", updatedOrder.getId(), updatedOrder.getOrderNumber());
        return updatedOrder.setOrderItems(savedOrderItems);
    }

    private List<Long> extractProductIdsFromCart(List<Long> cartItemIds) {
        return cartItemIds.stream()
            .map(cartService::getCartItemById)
            .map(CartItem::getProductId)
            .distinct()
            .toList();
    }

    private Long extractCouponUserId(Long userId, Long couponId) {
        if (couponId == null) {
            return null;
        }

        try {
            DiscountInfo discountInfo = calculateDiscountInfo(userId, couponId);
            return discountInfo.couponUserId();
        } catch (Exception e) {
            log.warn("쿠폰 정보 조회 실패, 쿠폰 없이 진행: userId={}, couponId={}", userId, couponId);
            return null;
        }
    }

    private static BigDecimal calculateValidFinalAmount(BigDecimal totalAmount, BigDecimal discountAmount) {
        BigDecimal result = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_DISCOUNT_AMOUNT);
        }

        return result;
    }

    private String generateOrderNumber() {
        return "ORDER-" + System.currentTimeMillis();
    }

    private DiscountInfo calculateDiscountInfo(Long userId, Long couponId) {
        if (couponId == null) {
            return DiscountInfo.NONE;
        }

        Coupon coupon = couponQueryService.getCoupon(couponId);
        if (coupon == null) {
            throw new OrderException(OrderErrorCode.COUPON_IN_ORDER_NOT_FOUND, "쿠폰을 찾을 수 없습니다. id=" + couponId);
        }

        CouponUser validCouponUser = findValidCouponUser(userId, couponId);
        return DiscountInfo.of(coupon.getDiscountAmount(), validCouponUser.getId());
    }

    private CouponUser findValidCouponUser(Long userId, Long couponId) {
        List<CouponUserWithCoupon> userCoupons =
                Optional.ofNullable(couponQueryService.getAllMyCoupons(userId))
                        .orElse(Collections.emptyList());

        return userCoupons.stream()
                .filter(cwc -> cwc.isSameCouponId(couponId))
                .map(CouponUserWithCoupon::getCouponUser)
                .filter(Objects::nonNull)
                .filter(CouponUser::isUsable)
                .findFirst()
                .orElseThrow(() -> new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "사용 가능한 쿠폰이 없습니다. id=" + couponId));
    }

    private void useCoupon(Long couponUserId, Order savedOrder) {
        if (couponUserId != null) {
            // Reentrant Lock 덕분에 독립적인 useCoupon() 메서드 호출 가능
            // OrderService가 이미 락을 보유한 상태지만, 같은 스레드에서 재진입 허용됨
            couponCommandService.useCoupon(couponUserId, savedOrder.getId());
        }
    }

    private void usePoint(Long userId, BigDecimal finalAmount, Order savedOrder) {
        // Reentrant Lock 덕분에 독립적인 usePoint() 메서드 호출 가능
        // OrderService가 이미 락을 보유한 상태지만, 같은 스레드에서 재진입 허용됨
        pointService.usePoint(userId, finalAmount, savedOrder.getId());
    }

    private void decreaseProductStockInCart(List<CartItem> cartItems, Map<Long, Product> productMap) {
        cartItems.forEach(cartItem -> {
            Product product = productMap.get(cartItem.getProductId());
            if (product != null) {
                productService.decreaseProductStock(product.getId(), cartItem.getQuantity());
            }
        });
    }

    private List<OrderItem> saveAndGetOrderItemList(List<CartItem> cartItems, Map<Long, Product> productMap, Order savedOrder) {
        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> OrderItem.fromCartItem(
                        item,
                        productMap.get(item.getProductId()),
                        savedOrder.getId()))
                .toList();

        return orderItemRepository.saveAll(orderItems);
    }

    private static void validateSufficientUserBalance(BigDecimal userBalance, BigDecimal finalAmount) {
        if (userBalance.compareTo(finalAmount) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS,
                    "포인트 잔액이 부족합니다. 필요: %s, 보유: %s".formatted(finalAmount, userBalance));
        }
    }

    private void validateOrderPaymentAbility(Long userId) {
        if (!pointService.hasPointAccount(userId)) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "포인트 계정이 없습니다. 포인트를 충전해주세요.");
        }
    }
}
