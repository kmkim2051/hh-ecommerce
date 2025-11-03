package com.hh.ecom.presentation;

import com.hh.ecom.presentation.api.CouponApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/coupons")
public class CouponController implements CouponApi {

    @Override
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAvailableCoupons() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> coupons = new ArrayList<>();

        Map<String, Object> coupon1 = new HashMap<>();
        coupon1.put("id", 1L);
        coupon1.put("name", "신규 회원 쿠폰");
        coupon1.put("discountAmount", 10000);
        coupon1.put("availableQuantity", 100);
        coupon1.put("status", "AVAILABLE");
        coupon1.put("expiredAt", "2025-12-31");
        coupons.add(coupon1);

        Map<String, Object> coupon2 = new HashMap<>();
        coupon2.put("id", 2L);
        coupon2.put("name", "블랙프라이데이 쿠폰");
        coupon2.put("discountAmount", 50000);
        coupon2.put("availableQuantity", 50);
        coupon2.put("status", "AVAILABLE");
        coupon2.put("expiredAt", "2025-11-30");
        coupons.add(coupon2);

        Map<String, Object> coupon3 = new HashMap<>();
        coupon3.put("id", 3L);
        coupon3.put("name", "VIP 쿠폰");
        coupon3.put("discountAmount", 100000);
        coupon3.put("availableQuantity", 0);
        coupon3.put("status", "SOLD_OUT");
        coupon3.put("expiredAt", "2025-12-31");
        coupons.add(coupon3);

        response.put("coupons", coupons);
        response.put("totalCount", coupons.size());

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<Map<String, Object>> issueCoupon(@PathVariable Long couponId) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", System.currentTimeMillis() % 10000);
        response.put("userId", 1L);
        response.put("couponId", couponId);
        response.put("couponName", "신규 회원 쿠폰");
        response.put("discountAmount", 10000);
        response.put("isUsed", false);
        response.put("expiredAt", "2025-12-31");
        response.put("message", "쿠폰이 발급되었습니다.");

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyCoupons() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> myCoupons = new ArrayList<>();

        Map<String, Object> userCoupon1 = new HashMap<>();
        userCoupon1.put("id", 1L);
        userCoupon1.put("couponId", 1L);
        userCoupon1.put("couponName", "신규 회원 쿠폰");
        userCoupon1.put("discountAmount", 10000);
        userCoupon1.put("isUsed", false);
        userCoupon1.put("usedAt", null);
        userCoupon1.put("expiredAt", "2025-12-31");
        myCoupons.add(userCoupon1);

        Map<String, Object> userCoupon2 = new HashMap<>();
        userCoupon2.put("id", 2L);
        userCoupon2.put("couponId", 2L);
        userCoupon2.put("couponName", "블랙프라이데이 쿠폰");
        userCoupon2.put("discountAmount", 50000);
        userCoupon2.put("isUsed", false);
        userCoupon2.put("usedAt", null);
        userCoupon2.put("expiredAt", "2025-11-30");
        myCoupons.add(userCoupon2);

        response.put("coupons", myCoupons);
        response.put("totalCount", myCoupons.size());

        return ResponseEntity.ok(response);
    }
}