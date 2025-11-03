package com.hh.ecom.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Tag(name = "Coupon", description = "쿠폰 관리 API")
public interface CouponApi {

    @Operation(
            summary = "발급 가능한 쿠폰 목록 조회",
            description = "현재 발급 가능한 모든 쿠폰 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getAvailableCoupons();

    @Operation(
            summary = "쿠폰 발급",
            description = "선착순으로 쿠폰을 발급받습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "발급 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> issueCoupon(
            @Parameter(description = "쿠폰 ID", required = true)
            Long couponId
    );

    @Operation(
            summary = "내 쿠폰 목록 조회",
            description = "사용자가 보유한 쿠폰 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getMyCoupons();
}
