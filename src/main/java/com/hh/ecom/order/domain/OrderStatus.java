package com.hh.ecom.order.domain;

public enum OrderStatus {
    PENDING,    // 주문 대기
    PAID,       // 결제 완료
    COMPLETED,  // 주문 완료
    CANCELED    // 주문 취소
}
