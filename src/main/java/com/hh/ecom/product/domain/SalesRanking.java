package com.hh.ecom.product.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SalesRanking {
    private final Long productId;
    private final Long salesCount;

    public static SalesRanking of(Long productId, Long salesCount) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId는 양수여야 합니다. productId=" + productId);
        }
        if (salesCount == null || salesCount < 0) {
            throw new IllegalArgumentException("salesCount는 0 이상이어야 합니다. salesCount=" + salesCount);
        }

        return new SalesRanking(productId, salesCount);
    }

    @Override
    public String toString() {
        return "SalesRanking{" +
                "productId=" + productId +
                ", salesCount=" + salesCount +
                '}';
    }
}
