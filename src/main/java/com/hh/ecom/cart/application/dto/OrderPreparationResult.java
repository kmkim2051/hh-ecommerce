package com.hh.ecom.cart.application.dto;

import com.hh.ecom.cart.domain.CartItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OrderPreparationResult(
        List<CartItem> validatedCartItems,
        BigDecimal totalAmount,
        List<Long> productIds,
        Map<Long, Integer> productQuantities) {
    public static OrderPreparationResult of(
            List<CartItem> validatedCartItems,
            BigDecimal totalAmount,
            List<Long> productIds,
            Map<Long, Integer> productQuantities
    ) {
        return new OrderPreparationResult(
                validatedCartItems,
                totalAmount,
                productIds,
                productQuantities
        );
    }
}
