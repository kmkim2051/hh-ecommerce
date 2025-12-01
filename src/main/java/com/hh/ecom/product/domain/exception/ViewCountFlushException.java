package com.hh.ecom.product.domain.exception;

public class ViewCountFlushException extends RuntimeException {

    public ViewCountFlushException(String message) {
        super(message);
    }

    public ViewCountFlushException(String message, Throwable cause) {
        super(message, cause);
    }
}
