package com.hh.ecom.common.exception;

import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    private static final String API_PREFIX = "/api/products/1";
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI(API_PREFIX);
        request = mockRequest;
    }

    @Test
    @DisplayName("ProductException을 처리하여 적절한 ErrorResponse를 반환한다")
    void handleProductException() {
        // given
        ProductException exception = new ProductException(
                ProductErrorCode.INSUFFICIENT_STOCK,
                "요청: 10, 현재 재고: 5"
        );

        // when
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleProductException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("P001");
        assertThat(response.getBody().message()).contains("재고가 부족합니다");
        assertThat(response.getBody().path()).isEqualTo(API_PREFIX);
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("PRODUCT_NOT_FOUND 예외는 404 상태를 반환한다")
    void handleProductNotFoundException() {
        // given
        ProductException exception = new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND);

        // when
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleProductException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("P100");
    }

    @Test
    @DisplayName("PRODUCT_ALREADY_DELETED 예외는 410 GONE 상태를 반환한다")
    void handleProductAlreadyDeletedException() {
        // given
        ProductException exception = new ProductException(ProductErrorCode.PRODUCT_ALREADY_DELETED);

        // when
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleProductException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("P101");
    }

    @Test
    @DisplayName("일반 예외를 처리하여 500 에러를 반환한다")
    void handleGeneralException() {
        // given
        Exception exception = new RuntimeException("Unexpected error");

        // when
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("에러 코드별로 정의된 HTTP 상태 코드가 올바르게 반환된다")
    void validateHttpStatusMapping() {
        // given & when & then
        assertHttpStatus(ProductErrorCode.INSUFFICIENT_STOCK, HttpStatus.BAD_REQUEST);
        assertHttpStatus(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertHttpStatus(ProductErrorCode.PRODUCT_ALREADY_DELETED, HttpStatus.GONE);
        assertHttpStatus(ProductErrorCode.INVALID_PRODUCT_NAME, HttpStatus.BAD_REQUEST);
    }

    private void assertHttpStatus(ProductErrorCode errorCode, HttpStatus expectedStatus) {
        ProductException exception = new ProductException(errorCode);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleProductException(exception, request);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
    }
}
