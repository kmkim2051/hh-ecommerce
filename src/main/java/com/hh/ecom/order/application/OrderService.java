package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemList;
import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.domain.*;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final CouponService couponService;
    private final PointService pointService;

    @Transactional
    public Order createOrder(Long userId, CreateOrderCommand createOrderCommand) {
        List<Long> cartItemIds = createOrderCommand.cartItemIds();
        Long couponId = createOrderCommand.couponId();

        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new OrderException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }

        List<CartItem> cartItems = cartItemIds.stream()
                .map(cartService::getCartItem)
                .toList();

        CartItemList cartItemList = CartItemList.from(cartItems);
        cartItemList.validateCartItemOwnership(userId);

        List<Product> products = productService.getProductList(cartItemList.getProductIdList());
        cartItemList.validateStockAvailability(products);

        DiscountInfo discountInfo = calculateDiscountInfo(userId, couponId);
        BigDecimal discountAmount = discountInfo.discountAmount();
        Long couponUserId = discountInfo.couponUserId();

        BigDecimal totalAmount = cartItemList.calculateTotalPrice(products);
        BigDecimal finalAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_DISCOUNT_AMOUNT);
        }

        validatePointAccountExists(userId);

        BigDecimal userBalance = Optional.ofNullable(pointService.getPoint(userId))
                .map(Point::getBalance)
                .orElse(BigDecimal.ZERO);

        validateSufficientUserBalance(userBalance, finalAmount);

        final String orderNumber = generateOrderNumber();
        Order order = Order.create(userId, orderNumber, totalAmount, discountAmount, couponUserId);
        Order savedOrder = orderRepository.save(order);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItem> savedOrderItems = saveAndGetOrderItemList(cartItems, productMap, savedOrder);

        decreaseProductStockInCart(cartItems, productMap);
        usePoint(userId, finalAmount, savedOrder);
        useCoupon(couponUserId, savedOrder);

        Order paidOrder = savedOrder.updateStatus(OrderStatus.PAID);
        Order updatedOrder = orderRepository.save(paidOrder);

        removeCartItemsOfUser(userId, cartItemIds, cartItems);

        return updatedOrder.setOrderItems(savedOrderItems);
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        Order updatedOrder = order.updateStatus(newStatus);
        return orderRepository.save(updatedOrder);
    }

    public List<Order> getOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public Order getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        order.validateOwner(userId);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return order.setOrderItems(orderItems);
    }

    public Order getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return order.setOrderItems(orderItems);
    }

    // ------------------------------ private method ------------------------------
    private String generateOrderNumber() {
        return "ORDER-" + System.currentTimeMillis();
    }

    private DiscountInfo calculateDiscountInfo(Long userId, Long couponId) {
        if (couponId == null) {
            return DiscountInfo.NONE;
        }

        Coupon coupon = couponService.getCoupon(couponId);
        if (coupon == null) {
            throw new OrderException(OrderErrorCode.COUPON_IN_ORDER_NOT_FOUND, "쿠폰을 찾을 수 없습니다. id=" + couponId);
        }

        CouponUser validCouponUser = findValidCouponUser(userId, couponId);
        return new DiscountInfo(coupon.getDiscountAmount(), validCouponUser.getId());
    }

    private CouponUser findValidCouponUser(Long userId, Long couponId) {
        List<CouponService.CouponUserWithCoupon> userCoupons =
                Optional.ofNullable(couponService.getAllMyCoupons(userId))
                        .orElse(Collections.emptyList());

        return userCoupons.stream()
                .filter(cwc -> cwc.isSameCouponId(couponId))
                .map(CouponService.CouponUserWithCoupon::getCouponUser)
                .filter(Objects::nonNull)
                .filter(cu -> Boolean.FALSE.equals(cu.getIsUsed()))
                .findFirst()
                .orElseThrow(() -> new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "사용 가능한 쿠폰이 없습니다. id=" + couponId));
    }

    private void useCoupon(Long couponUserId, Order savedOrder) {
        if (couponUserId != null) {
            couponService.useCoupon(couponUserId, savedOrder.getId());
        }
    }

    private void usePoint(Long userId, BigDecimal finalAmount, Order savedOrder) {
        pointService.usePoint(userId, finalAmount, savedOrder.getId());
    }

    private static void decreaseProductStockInCart(List<CartItem> cartItems, Map<Long, Product> productMap) {
        cartItems.forEach(cartItem -> {
            Product product = productMap.get(cartItem.getProductId());
            if (product != null) {
                product.decreaseStock(cartItem.getQuantity());
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
                    String.format("포인트 잔액이 부족합니다. 필요: %s, 보유: %s", finalAmount, userBalance));
        }
    }

    private void validatePointAccountExists(Long userId) {
        if (!pointService.hasPointAccount(userId)) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "포인트 계정이 없습니다. 포인트를 충전해주세요.");
        }
    }

    private void removeCartItemsOfUser(Long userId, List<Long> cartItemIds, List<CartItem> cartItems) {
        cartService.removeCartItems(userId, cartItemIds.stream()
                .map(id -> cartItems.stream()
                        .filter(ci -> Objects.equals(ci.getId(), id))
                        .findFirst()
                        .map(CartItem::getProductId)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList());
    }

    private record DiscountInfo(BigDecimal discountAmount, Long couponUserId) {
        public static final DiscountInfo NONE = new DiscountInfo(BigDecimal.ZERO, null);
    }
}

