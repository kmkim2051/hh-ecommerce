package com.hh.ecom.point.domain.exception;

import lombok.Getter;

@Getter
public class PointException extends RuntimeException {
    private final PointErrorCode errorCode;
    private final Object[] details;

    public PointException(PointErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public PointException(PointErrorCode errorCode, Object... details) {
        super(errorCode.getMessageWithDetails(details));
        this.errorCode = errorCode;
        this.details = details;
    }

    public PointException(PointErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public PointException(PointErrorCode errorCode, Throwable cause, Object... details) {
        super(errorCode.getMessageWithDetails(details), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
