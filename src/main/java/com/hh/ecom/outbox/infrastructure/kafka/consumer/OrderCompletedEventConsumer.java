package com.hh.ecom.outbox.infrastructure.kafka.consumer;

import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 완료 이벤트 Consumer
 * - Kafka에서 주문 완료 이벤트를 수신
 * - 현재는 로그만 출력하는 간단한 구현
 * - 향후 외부 시스템 연동, 알림 발송 등으로 확장 가능
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCompletedEventConsumer {

    @KafkaListener(
            topics = KafkaTopics.ORDER_COMPLETED,
            groupId = "ecom-order-group"
    )
    public void consumeOrderCompletedEvent(OrderCompletedEvent event) {
        log.info("[Kafka Consumer] 주문 완료 이벤트 수신: orderId={}, orderStatus={}, completedAt={}", event.orderId(), event.orderStatus(), event.completedAt());

        // TODO: 향후 확장 가능
        // - 외부 시스템에 알림 전송 (이메일, SMS, Push 등)
        // - 배송 시스템 연동
    }
}
