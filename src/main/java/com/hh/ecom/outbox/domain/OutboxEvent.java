package com.hh.ecom.outbox.domain;

import com.hh.ecom.order.domain.OrderStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Outbox Event Domain Model
 * - 주문 관련 이벤트를 외부 시스템에 발행하기 위한 도메인 모델
 * - Transactional Outbox Pattern 구현
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEvent {
    private final Long id;
    private final Long orderId;
    private final OrderStatus orderStatus;
    private final LocalDateTime createdAt;

    public static OutboxEvent create(Long orderId, OrderStatus orderStatus) {
        validateParams(orderId, orderStatus);

        return OutboxEvent.builder()
                .orderId(orderId)
                .orderStatus(orderStatus)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static void validateParams(Long orderId, OrderStatus orderStatus) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId는 양수여야 합니다. orderId=" + orderId);
        }
        if (orderStatus == null) {
            throw new IllegalArgumentException("orderStatus는 null일 수 없습니다.");
        }
    }
}
