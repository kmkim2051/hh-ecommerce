package com.hh.ecom.order.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode {
    ORDER_NOT_FOUND("ORDER_001", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS("ORDER_002", "유효하지 않은 주문 상태입니다.", HttpStatus.BAD_REQUEST),
    ORDER_NOT_CANCELABLE("ORDER_003", "취소할 수 없는 주문입니다.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ORDER_ACCESS("ORDER_004", "주문에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    INVALID_ORDER_AMOUNT("ORDER_005", "유효하지 않은 주문 금액입니다.", HttpStatus.BAD_REQUEST),
    ORDER_ITEM_NOT_FOUND("ORDER_006", "주문 아이템을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMPTY_ORDER_ITEMS("ORDER_007", "주문 아이템이 비어있습니다.", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_AMOUNT("ORDER_008", "할인 금액이 주문 금액을 초과할 수 없습니다.", HttpStatus.BAD_REQUEST),
    PRODUCT_IN_ORDER_NOT_FOUND("ORDER_009", "주문 아이템의 상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COUPON_IN_ORDER_NOT_FOUND("ORDER_010", "주문에 사용할 쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EMPTY_ORDER_CART_ITEM("ORDER_011", "주문에 사용할 장바구니가 비어있습니다.", HttpStatus.BAD_REQUEST),
    ORDER_CART_ITEM_NOT_FOUND("ORDER_012", "주문에 사용할 장바구니 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ;

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
