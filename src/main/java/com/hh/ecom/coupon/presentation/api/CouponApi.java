package com.hh.ecom.coupon.presentation.api;

import com.hh.ecom.coupon.presentation.dto.response.CouponIssueResponse;
import com.hh.ecom.coupon.presentation.dto.response.CouponListResponse;
import com.hh.ecom.coupon.presentation.dto.response.MyCouponListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Coupon", description = "쿠폰 관리 API")
public interface CouponApi {

    @Operation(
            summary = "발급 가능한 쿠폰 목록 조회",
            description = "현재 발급 가능한 모든 쿠폰 목록을 조회합니다. 활성화되어 있고, 발급 기간 내이며, 재고가 남아있는 쿠폰들을 반환합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CouponListResponse.class))
            )
    })
    ResponseEntity<CouponListResponse> getAvailableCoupons();

    @Operation(
            summary = "쿠폰 발급",
            description = "선착순으로 쿠폰을 발급받습니다. 발급 가능 여부를 검증하고, 중복 발급을 방지하며, 수량을 차감합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "발급 성공",
                    content = @Content(schema = @Schema(implementation = CouponIssueResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "발급 실패 (재고 소진, 중복 발급, 기간 만료 등)",
                    content = @Content(schema = @Schema(implementation = com.hh.ecom.common.exception.ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "쿠폰을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = com.hh.ecom.common.exception.ErrorResponse.class))
            )
    })
    ResponseEntity<CouponIssueResponse> issueCoupon(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER, example = "1", schema = @Schema(type = "integer"))
            Long userId,
            @Parameter(name = "couponId", description = "쿠폰 ID", required = true, in = ParameterIn.PATH, example = "1")
            Long couponId
    );

    @Operation(
            summary = "내 쿠폰 목록 조회",
            description = "사용자가 보유한 미사용 쿠폰 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MyCouponListResponse.class))
            )
    })
    ResponseEntity<MyCouponListResponse> getMyCoupons(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER, example = "1", schema = @Schema(type = "integer"))
            Long userId
    );
}
