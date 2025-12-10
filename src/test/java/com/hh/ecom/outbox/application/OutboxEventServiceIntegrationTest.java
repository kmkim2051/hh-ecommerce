package com.hh.ecom.outbox.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.order.domain.OrderStatus;
import com.hh.ecom.outbox.domain.OutboxEvent;
import com.hh.ecom.outbox.domain.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("OutboxEventService 통합 테스트 (Service + Repository + DB)")
class OutboxEventServiceIntegrationTest extends TestContainersConfig {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Outbox 이벤트 발행 및 조회 - 성공")
    void publishOrderEvent_And_FindByOrderId_Success() {
        // given
        Long orderId = 100L;
        OrderStatus orderStatus = OrderStatus.PAID;

        // when - 이벤트 발행
        OutboxEvent publishedEvent = outboxEventService.publishOrderEvent(orderId, orderStatus);

        // then - 이벤트 저장 확인
        assertThat(publishedEvent).isNotNull();
        assertThat(publishedEvent.getId()).isNotNull();
        assertThat(publishedEvent.getOrderId()).isEqualTo(orderId);
        assertThat(publishedEvent.getOrderStatus()).isEqualTo(orderStatus);
        assertThat(publishedEvent.getCreatedAt()).isNotNull();

        // when - 주문 ID로 이벤트 조회
        List<OutboxEvent> events = outboxEventService.getEventsByOrderId(orderId);

        // then - 조회 확인
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOrderId()).isEqualTo(orderId);
        assertThat(events.get(0).getOrderStatus()).isEqualTo(orderStatus);
    }

    @Test
    @DisplayName("여러 주문의 Outbox 이벤트 발행 및 전체 조회 - 성공")
    void publishMultipleOrderEvents_And_FindAll_Success() {
        // given
        Long orderId1 = 100L;
        Long orderId2 = 200L;

        // when - 여러 이벤트 발행
        outboxEventService.publishOrderEvent(orderId1, OrderStatus.PAID);
        outboxEventService.publishOrderEvent(orderId2, OrderStatus.PAID);
        outboxEventService.publishOrderEvent(orderId2, OrderStatus.COMPLETED);

        // then - 전체 조회
        List<OutboxEvent> allEvents = outboxEventService.getAllEvents();
        assertThat(allEvents).hasSize(3);

        // 주문 1번 이벤트 조회
        List<OutboxEvent> order1Events = outboxEventService.getEventsByOrderId(orderId1);
        assertThat(order1Events).hasSize(1);

        // 주문 2번 이벤트 조회 (2개 상태 변경)
        List<OutboxEvent> order2Events = outboxEventService.getEventsByOrderId(orderId2);
        assertThat(order2Events).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회 - 빈 리스트 반환")
    void getEventsByOrderId_NotFound_ReturnsEmptyList() {
        // given
        Long nonExistentOrderId = 999L;

        // when
        List<OutboxEvent> events = outboxEventService.getEventsByOrderId(nonExistentOrderId);

        // then
        assertThat(events).isEmpty();
    }
}
