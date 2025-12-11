package com.hh.ecom.outbox.application.event;

import com.hh.ecom.order.application.event.OrderCompletedEvent;
import com.hh.ecom.outbox.application.OutboxEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Outbox 이벤트 리스너
 * - 주문 완료 이벤트를 받아 Outbox 테이블에 기록
 * - 주문 트랜잭션 커밋 후 독립적인 트랜잭션에서 실행
 * - Outbox 이벤트 발행 실패가 주문 성공에 영향을 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {
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
            log.info("주문 완료 이벤트 수신 (Outbox): orderId={}, orderStatus={}",
                    event.orderId(), event.orderStatus());

            // Outbox 이벤트 발행 (외부 시스템 알림용)
            outboxEventService.publishOrderEvent(event.orderId(), event.orderStatus());

            log.info("Outbox 이벤트 발행 완료: orderId={}", event.orderId());
        } catch (Exception e) {
            // Outbox 이벤트 발행 실패해도 주문은 이미 성공
            // 별도 모니터링/재시도 로직으로 처리 가능
            log.error("Outbox 이벤트 발행 실패 (주문은 정상 완료됨): orderId={}, error={}", event.orderId(), e.getMessage(), e);
        }
    }
}
