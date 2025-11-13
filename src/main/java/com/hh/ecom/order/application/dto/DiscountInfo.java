package com.hh.ecom.order.application.dto;

import java.math.BigDecimal;

public record DiscountInfo(BigDecimal discountAmount, Long couponUserId) {
    public static final DiscountInfo NONE = new DiscountInfo(BigDecimal.ZERO, null);

    public static DiscountInfo of(BigDecimal discountAmount, Long couponUserId) {
        return new DiscountInfo(discountAmount, couponUserId);
    }
}