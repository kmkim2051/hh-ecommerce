package com.hh.ecom.order.application.event;

import com.hh.ecom.outbox.application.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 리스너
 * - 주문 트랜잭션이 커밋된 후 실행됨 (AFTER_COMMIT)
 * - Outbox 이벤트 발행 실패가 주문 성공에 영향을 주지 않음
 * - 비동기적으로 외부 시스템 알림 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {
    private final OutboxEventService outboxEventService;

    /**
     * 주문 완료 이벤트 처리
     * - 주문 트랜잭션 커밋 후 실행 (별도 트랜잭션)
     * - Outbox 이벤트 발행 실패해도 주문은 성공
     * - REQUIRES_NEW로 독립적인 트랜잭션에서 실행하여 Outbox 저장 보장
     *
     * @param event 주문 완료 이벤트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        try {
            log.info("주문 완료 이벤트 수신: orderId={}, orderStatus={}",
                    event.getOrderId(), event.getOrderStatus());

            // Outbox 이벤트 발행 (외부 시스템 알림용)
            outboxEventService.publishOrderEvent(event.getOrderId(), event.getOrderStatus());

            log.info("Outbox 이벤트 발행 완료: orderId={}", event.getOrderId());
        } catch (Exception e) {
            // Outbox 이벤트 발행 실패해도 주문은 이미 성공
            // 별도 모니터링/재시도 로직으로 처리 가능
            log.error("Outbox 이벤트 발행 실패 (주문은 정상 완료됨): orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
