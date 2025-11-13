package com.hh.ecom.order.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품별 판매 수량 집계 결과
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductSalesCount {
    private final Long productId;
    private final Long salesCount;
    public static ProductSalesCount of(final Long productId, final Long salesCount) {
        return new ProductSalesCount(productId, salesCount);
    }
}
