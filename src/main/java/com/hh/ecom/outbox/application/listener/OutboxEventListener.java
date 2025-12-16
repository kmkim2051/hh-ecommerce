package com.hh.ecom.outbox.application.listener;

import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.outbox.domain.MessagePublisher;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 완료 이벤트 리스너
 * - 주문 완료 이벤트를 받아 외부 시스템에 메시지 발행
 * - 주문 트랜잭션 커밋 후 독립적으로 실행
 * - 메시지 발행 실패가 주문 성공에 영향을 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {
    private final MessagePublisher messagePublisher;

    /**
     * 주문 완료 이벤트 처리
     * - 주문 트랜잭션 커밋 후 비동기로 실행 (별도 스레드)
     * - 메시지 발행 실패해도 주문은 성공
     * - @Async로 주문 API 응답 속도 향상 (백그라운드 처리)
     *
     * @param event 주문 완료 이벤트
     */
    @Async("outboxEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        try {
            log.info("주문 완료 이벤트 수신: orderId={}, orderStatus={}",
                    event.orderId(), event.orderStatus());

            // Kafka로 메시지 발행 (외부 시스템 알림용)
            messagePublisher.publish(
                    KafkaTopics.ORDER_COMPLETED,
                    event.orderId().toString(),
                    event
            );

            log.info("메시지 발행 완료: orderId={}", event.orderId());
        } catch (Exception e) {
            // 메시지 발행 실패해도 주문은 이미 성공
            // 별도 모니터링/재시도 로직으로 처리 가능
            log.error("메시지 발행 실패 (주문은 정상 완료됨): orderId={}, error={}", event.orderId(), e.getMessage(), e);
        }
    }
}
