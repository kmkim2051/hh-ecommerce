package com.hh.ecom.cart.domain.exception;

import lombok.Getter;

@Getter
public class CartException extends RuntimeException {
    private final CartErrorCode errorCode;
    private final Object[] details;

    public CartException(CartErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public CartException(CartErrorCode errorCode, Object... details) {
        super(errorCode.getMessageWithDetails(details));
        this.errorCode = errorCode;
        this.details = details;
    }

    public CartException(CartErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public CartException(CartErrorCode errorCode, Throwable cause, Object... details) {
        super(errorCode.getMessageWithDetails(details), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
