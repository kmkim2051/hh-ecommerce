package com.hh.ecom.point.infrastructure.persistence.entity;

import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionEntity {
    private Long id;
    private Long pointId;
    private BigDecimal amount;
    private TransactionType type;
    private Long orderId;
    private BigDecimal balanceAfter;
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

    public static PointTransactionEntity from(PointTransaction transaction) {
        return PointTransactionEntity.builder()
                .id(transaction.getId())
                .pointId(transaction.getPointId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .orderId(transaction.getOrderId())
                .balanceAfter(transaction.getBalanceAfter())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
