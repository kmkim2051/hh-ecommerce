package com.hh.ecom.user.infrastructure.persistence.entity;

import com.hh.ecom.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * In-Memory DB를 위한 POJO entity
 * JPA 도입 시 변경 예정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    private Long id;
    private String nickname;
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
