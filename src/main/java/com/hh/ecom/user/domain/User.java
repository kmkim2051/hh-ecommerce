package com.hh.ecom.user.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {
    private final Long id;
    private final String nickname;
    private final LocalDateTime createdAt;

    public static User create(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }

        return User.builder()
                .nickname(nickname)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
