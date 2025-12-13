package com.hh.ecom.outbox.infrastructure.persistence.entity;

import com.hh.ecom.order.domain.OrderStatus;
import com.hh.ecom.outbox.domain.OutboxEvent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus orderStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OutboxEvent toDomain() {
        return OutboxEvent.builder()
                .id(this.id)
                .orderId(this.orderId)
                .orderStatus(this.orderStatus)
                .createdAt(this.createdAt)
                .build();
    }

    public static OutboxEventEntity from(OutboxEvent outboxEvent) {
        return OutboxEventEntity.builder()
                .id(outboxEvent.getId())
                .orderId(outboxEvent.getOrderId())
                .orderStatus(outboxEvent.getOrderStatus())
                .createdAt(outboxEvent.getCreatedAt())
                .build();
    }
}