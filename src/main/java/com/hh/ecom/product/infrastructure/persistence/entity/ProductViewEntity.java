package com.hh.ecom.product.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_views", indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_viewed_at", columnList = "viewed_at"),
        @Index(name = "idx_product_viewed", columnList = "product_id,viewed_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductViewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
