package com.hh.ecom.coupon.domain.exception;

/**
 * 낙관적 락 충돌 시 발생하는 예외
 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }

    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
