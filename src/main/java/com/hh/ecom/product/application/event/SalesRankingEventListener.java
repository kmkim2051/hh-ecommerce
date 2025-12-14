package com.hh.ecom.product.application.event;

import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.order.domain.OrderItem;
import com.hh.ecom.order.domain.OrderItemRepository;
import com.hh.ecom.product.application.SalesRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 판매 랭킹 이벤트 리스너
 * - 주문 완료 이벤트 -> 판매량 랭킹을 Redis에 기록
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesRankingEventListener {
    private final SalesRankingRepository salesRankingRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 주문 완료 이벤트 처리
     * - 주문 트랜잭션 커밋 후 실행
     * @param event 주문 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        try {
            log.info("주문 완료 이벤트 수신 (판매 랭킹): orderId={}, orderStatus={}", event.orderId(), event.orderStatus());

            // 주문 아이템 조회
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(event.orderId());

            if (orderItems.isEmpty()) {
                log.warn("주문 아이템이 없습니다: orderId={}", event.orderId());
                return;
            }

            // 판매량 랭킹 기록
            salesRankingRepository.recordBatchSales(event.orderId(), orderItems);

            log.info("판매 랭킹 기록 완료: orderId={}, itemCount={}",
                    event.orderId(), orderItems.size());
        } catch (Exception e) {
            // 판매량 랭킹 기록 실패해도 주문은 이미 성공
            // 별도 모니터링/재시도 로직으로 처리 가능
            log.error("판매 랭킹 기록 실패 (주문은 정상 완료됨): orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }
}
