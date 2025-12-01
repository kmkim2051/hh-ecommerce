package com.hh.ecom.point.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode {
    // 포인트 잔액 관련
    INSUFFICIENT_BALANCE("PT001", "포인트 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT("PT002", "유효하지 않은 금액입니다.", HttpStatus.BAD_REQUEST),

    // 포인트 계좌 관련
    POINT_NOT_FOUND("PT100", "포인트 계좌를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    POINT_ALREADY_EXISTS("PT101", "이미 포인트 계좌가 존재합니다.", HttpStatus.CONFLICT),

    // 트랜잭션 관련
    TRANSACTION_NOT_FOUND("PT200", "포인트 거래 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_TRANSACTION_TYPE("PT201", "유효하지 않은 거래 유형입니다.", HttpStatus.BAD_REQUEST),

    // 동시성 제어 관련
    OPTIMISTIC_LOCK_FAILURE("PT300", "동시에 처리 중인 요청이 있습니다. 다시 시도해주세요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    public String getMessageWithDetails(Object... args) {
        return "%s (현재값: %s)".formatted(message, formatArgs(args));
    }

    private String formatArgs(Object... args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return String.join(", ", java.util.Arrays.stream(args)
                .map(String::valueOf)
                .toArray(String[]::new));
    }
}
