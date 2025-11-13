package com.hh.ecom.user.infrastructure.persistence.entity;

import com.hh.ecom.user.domain.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA 기반 User Entity
 * User 도메인과 분리되어 매핑 가능
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User toDomain() {
        return User.builder()
                .id(this.id)
                .nickname(this.nickname)
                .createdAt(this.createdAt)
                .build();
    }

    public static UserEntity from(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
