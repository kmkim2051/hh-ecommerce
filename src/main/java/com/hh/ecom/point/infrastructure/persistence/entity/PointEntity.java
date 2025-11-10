package com.hh.ecom.point.infrastructure.persistence.entity;

import com.hh.ecom.point.domain.Point;
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
public class PointEntity {
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private Long version; // 낙관적 락을 위한 버전 필드
    private LocalDateTime updatedAt;

    public Point toDomain() {
        return Point.builder()
                .id(this.id)
                .userId(this.userId)
                .balance(this.balance)
                .version(this.version)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static PointEntity from(Point point) {
        return PointEntity.builder()
                .id(point.getId())
                .userId(point.getUserId())
                .balance(point.getBalance())
                .version(point.getVersion())
                .updatedAt(point.getUpdatedAt())
                .build();
    }
}
