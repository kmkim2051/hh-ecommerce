package com.hh.ecom.order.domain.event;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.OrderStatus;
import com.hh.ecom.order.domain.exception.OrderErrorCode;
import com.hh.ecom.order.domain.exception.OrderException;

import java.time.LocalDateTime;

/**
 * 주문 완료 이벤트
 * - 주문 트랜잭션 커밋 후 발행됨
 * - 외부 시스템 알림, Outbox 이벤트 발행 등에 사용
 */
public record OrderCompletedEvent(
        Long orderId,
        OrderStatus orderStatus,
        LocalDateTime completedAt
) {
    public static OrderCompletedEvent from(Order order) {
        validateOrder(order);
        return new OrderCompletedEvent(
                order.getId(),
                order.getStatus(),
                order.getUpdatedAt()
        );
    }

    private static void validateOrder(Order order) {
        if (order.getId() == null || order.getStatus() == null) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }
        // todo: completed 판정은 어떤 status 인가? (PAID or COMPLETED)
    }
}
