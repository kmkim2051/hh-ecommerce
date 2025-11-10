package com.hh.ecom.common.exception;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ErrorResponse(String code, String message, LocalDateTime timestamp, String path) {
    public static ErrorResponse of(String code, String message, String path) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
}
