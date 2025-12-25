package com.hh.ecom.order.infrastructure.kafka;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.outbox.domain.MessagePublisher;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문 완료 이벤트 Kafka Producer
 * - 주문 완료 시 Kafka로 직접 이벤트 발행
 * - @Async 방식의 신뢰성 문제 해결
 * - 외부 시스템 알림용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedKafkaProducer {
    private final MessagePublisher messagePublisher;

    /**
     * 주문 완료 이벤트를 Kafka로 발행
     *
     * @param order 완료된 주문
     */
    public void publishOrderCompletedEvent(Order order) {
        OrderCompletedEvent event = OrderCompletedEvent.from(order);

        messagePublisher.publish(
            KafkaTopics.ORDER_COMPLETED,
            order.getId().toString(),
            event
        );

        log.info("주문 완료 이벤트 Kafka 발행: orderId={}, orderStatus={}",
            order.getId(), order.getStatus());
    }
}
