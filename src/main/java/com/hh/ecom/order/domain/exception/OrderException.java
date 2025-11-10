package com.hh.ecom.order.domain.exception;

import lombok.Getter;

@Getter
public class OrderException extends RuntimeException {
    private final OrderErrorCode errorCode;
    private final Object[] details;

    public OrderException(OrderErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public OrderException(OrderErrorCode errorCode, Object... details) {
        super(errorCode.getMessageWithDetails(details));
        this.errorCode = errorCode;
        this.details = details;
    }

    public OrderException(OrderErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public OrderException(OrderErrorCode errorCode, Throwable cause, Object... details) {
        super(errorCode.getMessageWithDetails(details), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
