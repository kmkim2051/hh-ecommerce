package com.hh.ecom.coupon.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode {
    // 쿠폰 조회 관련
    COUPON_NOT_FOUND("CP001", "쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // 쿠폰 발급 관련
    COUPON_SOLD_OUT("CP101", "쿠폰 수량이 소진되었습니다.", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED("CP102", "쿠폰 발급 기간이 만료되었습니다.", HttpStatus.BAD_REQUEST),
    COUPON_NOT_ACTIVE("CP103", "쿠폰이 활성화 상태가 아닙니다.", HttpStatus.BAD_REQUEST),
    COUPON_ALREADY_ISSUED("CP104", "이미 발급받은 쿠폰입니다.", HttpStatus.CONFLICT),
    INVALID_QUANTITY("CP105", "유효하지 않은 수량입니다.", HttpStatus.BAD_REQUEST),
    OPTIMISTIC_LOCK_CONFLICT("CP106", "동시에 쿠폰 발급 요청이 처리되었습니다. 다시 시도해주세요.", HttpStatus.CONFLICT),
    COUPON_ISSUE_FAILED("CP107", "쿠폰 발급에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // 쿠폰 사용 관련
    COUPON_USER_NOT_FOUND("CP201", "발급받은 쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COUPON_ALREADY_USED("CP202", "이미 사용된 쿠폰입니다.", HttpStatus.BAD_REQUEST),
    COUPON_USER_EXPIRED("CP203", "쿠폰 사용 기간이 만료되었습니다.", HttpStatus.BAD_REQUEST);

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
