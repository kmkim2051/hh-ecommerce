package com.hh.ecom.outbox.domain;

import com.hh.ecom.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OutboxEventTest {

    @Test
    @DisplayName("OutboxEvent 생성 - 성공")
    void createOutboxEvent_Success() {
        // given
        Long orderId = 1L;
        OrderStatus orderStatus = OrderStatus.PAID;

        // when
        OutboxEvent outboxEvent = OutboxEvent.create(orderId, orderStatus);

        // then
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.getOrderId()).isEqualTo(orderId);
        assertThat(outboxEvent.getOrderStatus()).isEqualTo(orderStatus);
        assertThat(outboxEvent.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - orderId null")
    void createOutboxEvent_Fail_OrderIdNull() {
        // given
        Long orderId = null;
        OrderStatus orderStatus = OrderStatus.PAID;

        // when & then
        assertThatThrownBy(() -> OutboxEvent.create(orderId, orderStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId는 양수여야 합니다");
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - orderStatus null")
    void createOutboxEvent_Fail_OrderStatusNull() {
        // given
        Long orderId = 1L;
        OrderStatus orderStatus = null;

        // when & then
        assertThatThrownBy(() -> OutboxEvent.create(orderId, orderStatus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderStatus는 null일 수 없습니다");
    }
}
