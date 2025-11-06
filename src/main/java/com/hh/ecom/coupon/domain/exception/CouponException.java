package com.hh.ecom.coupon.domain.exception;

import lombok.Getter;

@Getter
public class CouponException extends RuntimeException {
    private final CouponErrorCode errorCode;
    private final Object[] details;

    public CouponException(CouponErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public CouponException(CouponErrorCode errorCode, Object... details) {
        super(errorCode.getMessageWithDetails(details));
        this.errorCode = errorCode;
        this.details = details;
    }

    public CouponException(CouponErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public CouponException(CouponErrorCode errorCode, Throwable cause, Object... details) {
        super(errorCode.getMessageWithDetails(details), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
