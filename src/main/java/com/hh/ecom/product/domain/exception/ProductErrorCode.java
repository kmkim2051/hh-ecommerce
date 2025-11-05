package com.hh.ecom.product.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode {
    // 재고 관련
    INSUFFICIENT_STOCK("P001", "재고가 부족합니다.", HttpStatus.BAD_REQUEST),
    INVALID_STOCK_QUANTITY("P002", "유효하지 않은 재고 수량입니다.", HttpStatus.BAD_REQUEST),

    // 상품 상태 관련
    PRODUCT_NOT_FOUND("P100", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_DELETED("P101", "이미 삭제된 상품입니다.", HttpStatus.GONE),
    PRODUCT_NOT_ACTIVE("P102", "활성화되지 않은 상품입니다.", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_AVAILABLE_FOR_SALE("P103", "판매 불가능한 상품입니다.", HttpStatus.BAD_REQUEST),

    // 입력 검증 관련
    INVALID_PRODUCT_NAME("P200", "유효하지 않은 상품명입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_PRICE("P201", "유효하지 않은 상품 가격입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT_DESCRIPTION("P202", "유효하지 않은 상품 설명입니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    public String getMessageWithDetails(Object... args) {
        return String.format("%s (현재값: %s)", message, formatArgs(args));
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
