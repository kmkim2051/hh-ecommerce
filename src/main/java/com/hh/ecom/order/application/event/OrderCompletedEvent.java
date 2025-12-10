package com.hh.ecom.order.application.event;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 주문 완료 이벤트
 * - 주문 트랜잭션 커밋 후 발행됨
 * - 외부 시스템 알림, Outbox 이벤트 발행 등에 사용
 */
@Getter
@RequiredArgsConstructor
public class OrderCompletedEvent {
    private final Long orderId;
    private final OrderStatus orderStatus;
    private final LocalDateTime completedAt;

    public static OrderCompletedEvent from(Order order) {
        return new OrderCompletedEvent(
            order.getId(),
            order.getStatus(),
            LocalDateTime.now()
        );
    }
}
