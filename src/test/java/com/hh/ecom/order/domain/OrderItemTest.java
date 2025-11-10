package com.hh.ecom.order.domain;

import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderItemTest {

    @Test
    @DisplayName("주문 아이템 생성 - 성공")
    void createOrderItem_Success() {
        Long orderId = 1L;
        Long productId = 100L;
        String productName = "노트북";
        BigDecimal price = BigDecimal.valueOf(1500000);
        Integer quantity = 2;

        OrderItem orderItem = OrderItem.create(orderId, productId, productName, price, quantity);

        assertThat(orderItem).isNotNull();
        assertThat(orderItem.getOrderId()).isEqualTo(orderId);
        assertThat(orderItem.getProductId()).isEqualTo(productId);
        assertThat(orderItem.getProductName()).isEqualTo(productName);
        assertThat(orderItem.getPrice()).isEqualTo(price);
        assertThat(orderItem.getQuantity()).isEqualTo(quantity);
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.NORMAL);
    }

    @Test
    @DisplayName("주문 아이템 생성 실패 - 상품 ID 없음")
    void createOrderItem_Fail_NoProductId() {
        assertThatThrownBy(() ->
                OrderItem.create(1L, null, "노트북", BigDecimal.valueOf(1500000), 1))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("상품 ID는 필수입니다");
    }

    @Test
    @DisplayName("주문 아이템 생성 실패 - 상품명 없음")
    void createOrderItem_Fail_NoProductName() {
        assertThatThrownBy(() ->
                OrderItem.create(1L, 100L, null, BigDecimal.valueOf(1500000), 1))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("상품명은 필수입니다");
    }

    @Test
    @DisplayName("주문 아이템 생성 실패 - 음수 가격")
    void createOrderItem_Fail_NegativePrice() {
        assertThatThrownBy(() ->
                OrderItem.create(1L, 100L, "노트북", BigDecimal.valueOf(-1000), 1))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("상품 가격은 0 이상이어야 합니다");
    }

    @Test
    @DisplayName("주문 아이템 생성 실패 - 0 이하 수량")
    void createOrderItem_Fail_InvalidQuantity() {
        assertThatThrownBy(() ->
                OrderItem.create(1L, 100L, "노트북", BigDecimal.valueOf(1500000), 0))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");
    }

    @Test
    @DisplayName("주문 아이템 총액 계산")
    void getItemTotal() {
        BigDecimal price = BigDecimal.valueOf(1500000);
        Integer quantity = 2;

        OrderItem orderItem = OrderItem.create(1L, 100L, "노트북", price, quantity);

        BigDecimal expectedTotal = BigDecimal.valueOf(3000000);
        assertThat(orderItem.getItemTotal()).isEqualTo(expectedTotal);
    }

    @Test
    @DisplayName("주문 아이템 취소 - 성공")
    void cancelOrderItem_Success() {
        OrderItem orderItem = OrderItem.create(1L, 100L, "노트북", BigDecimal.valueOf(1500000), 1);

        OrderItem canceledItem = orderItem.cancel();

        assertThat(canceledItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(canceledItem.isCanceled()).isTrue();
        assertThat(canceledItem.isNormal()).isFalse();
    }

    @Test
    @DisplayName("주문 아이템 취소 실패 - 이미 취소됨")
    void cancelOrderItem_Fail_AlreadyCanceled() {
        OrderItem orderItem = OrderItem.create(1L, 100L, "노트북", BigDecimal.valueOf(1500000), 1);
        OrderItem canceledItem = orderItem.cancel();

        assertThatThrownBy(canceledItem::cancel)
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("이미 취소된 주문 아이템입니다");
    }

    @Test
    @DisplayName("주문 아이템 상태 확인")
    void statusCheckMethods() {
        OrderItem normalItem = OrderItem.create(1L, 100L, "노트북", BigDecimal.valueOf(1500000), 1);
        assertThat(normalItem.isNormal()).isTrue();
        assertThat(normalItem.isCanceled()).isFalse();

        OrderItem canceledItem = normalItem.cancel();
        assertThat(canceledItem.isNormal()).isFalse();
        assertThat(canceledItem.isCanceled()).isTrue();
    }

    @Test
    @DisplayName("withId로 ID 설정")
    void withId() {
        OrderItem orderItem = OrderItem.create(1L, 100L, "노트북", BigDecimal.valueOf(1500000), 1);

        OrderItem itemWithId = orderItem.withId(999L);

        assertThat(itemWithId.getId()).isEqualTo(999L);
        assertThat(itemWithId.getProductId()).isEqualTo(orderItem.getProductId());
    }
}
