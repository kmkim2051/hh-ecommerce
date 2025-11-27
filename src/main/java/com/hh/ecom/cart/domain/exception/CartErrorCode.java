package com.hh.ecom.cart.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode {
    // 장바구니 아이템 관련
    CART_ITEM_NOT_FOUND("CA001", "장바구니 아이템을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CART_ITEM_ALREADY_EXISTS("CA002", "이미 장바구니에 담긴 상품입니다.", HttpStatus.CONFLICT),

    // 수량 관련
    INVALID_QUANTITY("CA100", "유효하지 않은 수량입니다.", HttpStatus.BAD_REQUEST),
    QUANTITY_EXCEEDS_STOCK("CA101", "재고 수량을 초과할 수 없습니다.", HttpStatus.BAD_REQUEST),

    // 권한 관련
    UNAUTHORIZED_CART_ACCESS("CA200", "장바구니에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // 입력 검증 관련
    INVALID_USER_ID("CA300", "유효하지 않은 사용자 ID입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_ID("CA301", "유효하지 않은 상품 ID입니다.", HttpStatus.BAD_REQUEST),
    INVALID_CART_ITEM_ID("CA302", "유효하지 않은 장바구니 아이템 ID입니다.", HttpStatus.BAD_REQUEST);

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
