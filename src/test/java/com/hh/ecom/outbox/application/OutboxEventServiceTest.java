package com.hh.ecom.outbox.application;

import com.hh.ecom.order.domain.OrderStatus;
import com.hh.ecom.outbox.domain.OutboxEvent;
import com.hh.ecom.outbox.domain.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Test
    @DisplayName("Outbox 이벤트 발행 - 성공")
    void publishOrderEvent_Success() {
        // given
        Long orderId = 1L;
        OrderStatus orderStatus = OrderStatus.PAID;

        OutboxEvent savedEvent = OutboxEvent.builder()
                .id(1L)
                .orderId(orderId)
                .orderStatus(orderStatus)
                .createdAt(LocalDateTime.now())
                .build();

        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willReturn(savedEvent);

        // when
        OutboxEvent result = outboxEventService.publishOrderEvent(orderId, orderStatus);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getOrderStatus()).isEqualTo(orderStatus);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("주문 ID로 이벤트 조회 - 성공")
    void getEventsByOrderId_Success() {
        // given
        Long orderId = 1L;
        List<OutboxEvent> events = List.of(
                OutboxEvent.builder()
                        .id(1L)
                        .orderId(orderId)
                        .orderStatus(OrderStatus.PAID)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        given(outboxEventRepository.findByOrderId(orderId))
                .willReturn(events);

        // when
        List<OutboxEvent> result = outboxEventService.getEventsByOrderId(orderId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo(orderId);
        verify(outboxEventRepository).findByOrderId(orderId);
    }

    @Test
    @DisplayName("모든 이벤트 조회 - 성공")
    void getAllEvents_Success() {
        // given
        List<OutboxEvent> events = List.of(
                OutboxEvent.builder()
                        .id(1L)
                        .orderId(1L)
                        .orderStatus(OrderStatus.PAID)
                        .createdAt(LocalDateTime.now())
                        .build(),
                OutboxEvent.builder()
                        .id(2L)
                        .orderId(2L)
                        .orderStatus(OrderStatus.COMPLETED)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        given(outboxEventRepository.findAll())
                .willReturn(events);

        // when
        List<OutboxEvent> result = outboxEventService.getAllEvents();

        // then
        assertThat(result).hasSize(2);
        verify(outboxEventRepository).findAll();
    }
}
