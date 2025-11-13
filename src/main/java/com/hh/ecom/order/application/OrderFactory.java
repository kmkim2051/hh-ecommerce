package com.hh.ecom.order.application;

import com.hh.ecom.cart.application.CartService;
import com.hh.ecom.cart.domain.CartItem;
import com.hh.ecom.cart.domain.CartItemList;
import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.order.application.dto.CreateOrderCommand;
import com.hh.ecom.order.application.dto.DiscountInfo;
import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.product.application.ProductService;
import com.hh.ecom.product.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// todo: order create refactor용 클래스 (구현 필요)
@Slf4j
@RequiredArgsConstructor
public class OrderFactory {
    private final CartService cartService;
    private final ProductService productService;
    private final PointService pointService;
    private final CouponService couponService;

    public Order assembleOrder(Long userId, CreateOrderCommand createOrderCommand) {
        log.info("order 생성 시작합니다. (userId: {}, create command: {})", userId, createOrderCommand );
        createOrderCommand.validate();
        validateEnoughAmount(userId);

        CartItemList cart = getCart(userId, createOrderCommand);
        List<Product> products = productService.getProductList(cart.getProductIdList());
        cart.validateEnoughStock(products);

        final Long couponId = createOrderCommand.couponId();
        DiscountInfo discountInfo = couponService.calculateDiscountInfo(userId, couponId);

        BigDecimal discountAmount = discountInfo.discountAmount();

        final BigDecimal totalAmount = cart.calculateTotalPrice(products);
        final BigDecimal finalAmount = calculateValidFinalAmount(totalAmount, discountAmount);

        BigDecimal userBalance = getUserBalance(userId);
        validateEnoughUserBalance(userBalance, finalAmount);

        final String orderNumber = generateOrderNumber();
        return Order.create(userId, orderNumber, totalAmount, discountAmount, discountInfo.couponUserId());
    }

    private BigDecimal getUserBalance(Long userId) {
        return Optional.ofNullable(pointService.getPoint(userId))
                .map(Point::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private CartItemList getCart(Long userId, CreateOrderCommand createOrderCommand) {
        List<Long> cartItemIds = createOrderCommand.cartItemIds();
        List<CartItem> findCartItems = cartItemIds.stream()
                .map(cartService::getCartItemById)
                .toList();

        validateCartItemsInOrderExists(findCartItems);
        CartItemList cart = CartItemList.from(findCartItems);
        cart.validateCartItemOwnership(userId);
        return cart;
    }

    private void validateEnoughAmount(Long userId) {
        if (!pointService.hasPointAccount(userId)) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS, "포인트 계정이 없습니다. 포인트를 충전해주세요.");
        }
    }

    private static void validateCartItemsInOrderExists(List<CartItem> findCartItems) {
        if (findCartItems.isEmpty()) {
            throw new OrderException(OrderErrorCode.EMPTY_ORDER_CART_ITEM);
        }
    }

    private String generateOrderNumber() {
        return "ORDER-" + System.currentTimeMillis();
    }

    private static BigDecimal calculateValidFinalAmount(BigDecimal totalAmount, BigDecimal discountAmount) {
        BigDecimal result = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_DISCOUNT_AMOUNT);
        }

        return result;
    }


    private static void validateEnoughUserBalance(BigDecimal userBalance, BigDecimal finalAmount) {
        if (userBalance.compareTo(finalAmount) < 0) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS,
                    String.format("포인트 잔액이 부족합니다. 필요: %s, 보유: %s", finalAmount, userBalance));
        }
    }

}
