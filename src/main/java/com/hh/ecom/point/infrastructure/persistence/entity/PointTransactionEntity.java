package com.hh.ecom.point.infrastructure.persistence.entity;

import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * PointTransaction JPA Entity
 */
@Entity
@Table(name = "point_transactions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PointTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_id", nullable = false)
    private Long pointId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PointTransaction toDomain() {
        return PointTransaction.builder()
                .id(this.id)
                .pointId(this.pointId)
                .amount(this.amount)
                .type(this.type)
                .orderId(this.orderId)
                .balanceAfter(this.balanceAfter)
                .createdAt(this.createdAt)
                .build();
    }

    public static PointTransactionEntity from(PointTransaction tx) {
        return PointTransactionEntity.builder()
                .id(tx.getId())
                .pointId(tx.getPointId())
                .amount(tx.getAmount())
                .type(tx.getType())
                .orderId(tx.getOrderId())
                .balanceAfter(tx.getBalanceAfter())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    // InMemory 구현체 호환성을 위한 오버로드 (Deprecated)
    public static PointTransactionEntity from(PointTransaction tx, Supplier<Long> idGenerator) {
        Long id = !(tx.isNew()) ? tx.getId() : idGenerator.get();

        return PointTransactionEntity.builder()
                .id(id)
                .pointId(tx.getPointId())
                .amount(tx.getAmount())
                .type(tx.getType())
                .orderId(tx.getOrderId())
                .balanceAfter(tx.getBalanceAfter())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
