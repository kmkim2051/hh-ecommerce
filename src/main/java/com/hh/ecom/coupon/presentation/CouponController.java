package com.hh.ecom.coupon.presentation;

import com.hh.ecom.coupon.application.CouponService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUser;
import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import com.hh.ecom.coupon.presentation.api.CouponApi;
import com.hh.ecom.coupon.presentation.dto.response.CouponIssueResponse;
import com.hh.ecom.coupon.presentation.dto.response.CouponListResponse;
import com.hh.ecom.coupon.presentation.dto.response.MyCouponListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController implements CouponApi {

    private final CouponService couponService;

    @Override
    @GetMapping
    public ResponseEntity<CouponListResponse> getAvailableCoupons() {
        List<Coupon> coupons = couponService.getAvailableCoupons();
        CouponListResponse response = CouponListResponse.from(coupons);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @RequestHeader("userId") Long userId,
            @PathVariable Long couponId
    ) {
        CouponUser issuedCouponUser = couponService.issueCoupon(userId, couponId);
        Coupon coupon = couponService.getCoupon(couponId);

        CouponIssueResponse response = CouponIssueResponse.from(
                issuedCouponUser,
                coupon,
                "쿠폰이 발급되었습니다."
        );

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/my")
    public ResponseEntity<MyCouponListResponse> getMyCoupons(
            @RequestHeader("userId") Long userId
    ) {
        List<CouponUserWithCoupon> myCoupons = couponService.getMyCoupons(userId);
        MyCouponListResponse response = MyCouponListResponse.from(myCoupons);

        return ResponseEntity.ok(response);
    }
}