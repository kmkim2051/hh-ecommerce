package com.hh.ecom.product.domain.exception;

import lombok.Getter;

@Getter
public class ProductException extends RuntimeException {
    private final ProductErrorCode errorCode;
    private final Object[] details;

    public ProductException(ProductErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public ProductException(ProductErrorCode errorCode, Object... details) {
        super(errorCode.getMessageWithDetails(details));
        this.errorCode = errorCode;
        this.details = details;
    }

    public ProductException(ProductErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public ProductException(ProductErrorCode errorCode, Throwable cause, Object... details) {
        super(errorCode.getMessageWithDetails(details), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
