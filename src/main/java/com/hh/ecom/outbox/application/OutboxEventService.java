package com.hh.ecom.outbox.application;

import com.hh.ecom.order.domain.OrderStatus;
import com.hh.ecom.outbox.domain.OutboxEvent;
import com.hh.ecom.outbox.domain.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Outbox Event 발행 (DB 저장)
     * - 주문 완료 시 이벤트를 Outbox 테이블에 저장
     * - 주문 트랜잭션과 함께 커밋되어 데이터 일관성 보장
     *
     * @param orderId 주문 ID
     * @param orderStatus 주문 상태
     * @return 저장된 OutboxEvent
     */
    @Transactional
    public OutboxEvent publishOrderEvent(Long orderId, OrderStatus orderStatus) {
        log.info("Outbox 기록 시작: orderId={}, orderStatus={}", orderId, orderStatus);

        OutboxEvent outboxEvent = OutboxEvent.create(orderId, orderStatus);
        OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);

        log.info("Outbox 기록 완료: eventId={}, orderId={}, orderStatus={}",
                savedEvent.getId(), orderId, orderStatus);

        return savedEvent;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> getEventsByOrderId(Long orderId) {
        return outboxEventRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> getAllEvents() {
        return outboxEventRepository.findAll();
    }
}
