package com.hh.ecom.coupon.presentation;

import com.hh.ecom.coupon.application.CouponQueryService;
import com.hh.ecom.coupon.domain.Coupon;
import com.hh.ecom.coupon.domain.CouponUserWithCoupon;
import com.hh.ecom.coupon.infrastructure.kafka.CouponIssueKafkaProducer;
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

    private final CouponQueryService couponQueryService;
    private final CouponIssueKafkaProducer couponIssueKafkaProducer;

    @Override
    @GetMapping
    public ResponseEntity<CouponListResponse> getAvailableCoupons() {
        List<Coupon> coupons = couponQueryService.getAvailableCoupons();
        CouponListResponse response = CouponListResponse.from(coupons);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @RequestHeader("userId") Long userId,
            @PathVariable Long couponId
    ) {
        // Kafka 기반 비동기 쿠폰 발급
        String requestId = couponIssueKafkaProducer.publishCouponIssueRequest(userId, couponId);

        // 비동기 즉시 응답 (requestId 포함)
        CouponIssueResponse response = CouponIssueResponse.queued(
                userId,
                couponId,
                requestId,  // 클라이언트가 비동기 처리 상태를 추적할 수 있도록 requestId 전달
                "쿠폰 발급 요청이 접수되었습니다. 곧 처리됩니다."
        );

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/my")
    public ResponseEntity<MyCouponListResponse> getMyCoupons(
            @RequestHeader("userId") Long userId
    ) {
        List<CouponUserWithCoupon> myCoupons = couponQueryService.getMyCoupons(userId);
        MyCouponListResponse response = MyCouponListResponse.from(myCoupons);

        return ResponseEntity.ok(response);
    }
}