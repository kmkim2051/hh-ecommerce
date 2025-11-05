package com.hh.ecom.common.exception;

import com.hh.ecom.point.domain.exception.PointException;
import com.hh.ecom.product.domain.exception.ProductException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ErrorResponse> handleProductException(
            ProductException e,
            HttpServletRequest request
    ) {
        log.warn("ProductException occurred: code={}, message={}, path={}",
                e.getCode(), e.getMessage(), request.getRequestURI(), e);

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getCode(),
                e.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(PointException.class)
    public ResponseEntity<ErrorResponse> handlePointException(
            PointException e,
            HttpServletRequest request
    ) {
        log.warn("PointException occurred: code={}, message={}, path={}",
                e.getCode(), e.getMessage(), request.getRequestURI(), e);

        return buildErrorResponseEntity(e, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        log.error("Unexpected exception occurred: message={}, path={}",
                e.getMessage(), request.getRequestURI(), e);

        ErrorResponse errorResponse = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.",
                request.getRequestURI()
        );

        return ResponseEntity
                .internalServerError()
                .body(errorResponse);
    }

    private static ResponseEntity<ErrorResponse> buildErrorResponseEntity(PointException e, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.of(
                e.getCode(),
                e.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(errorResponse);
    }
}
