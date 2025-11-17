package com.hh.ecom.point.infrastructure.persistence.entity;

import com.hh.ecom.point.domain.Point;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Point JPA Entity
 * 낙관적 락(@Version)을 사용한 동시성 제어
 */
@Entity
@Table(name = "points")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PointEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Point toDomain() {
        return Point.builder()
                .id(this.id)
                .userId(this.userId)
                .balance(this.balance)
                .updatedAt(this.updatedAt)
                // version은 Entity에서만 관리, Domain에 노출하지 않음
                .build();
    }

    public static PointEntity from(Point point) {
        return PointEntity.builder()
                .id(point.getId())
                .userId(point.getUserId())
                .balance(point.getBalance())
                .updatedAt(point.getUpdatedAt())
                // version은 JPA가 자동으로 관리 (save 시 자동 증가)
                .build();
    }
}
