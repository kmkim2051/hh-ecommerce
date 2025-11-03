package com.hh.ecom.controller.api;

import com.hh.ecom.dto.request.ChargePointRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Tag(name = "Point", description = "포인트 관리 API")
public interface PointApi {

    @Operation(
            summary = "포인트 잔액 조회",
            description = "현재 사용자의 포인트 잔액을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getPointBalance();

    @Operation(
            summary = "포인트 충전",
            description = "사용자의 포인트를 충전합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "충전 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> chargePoint(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "포인트 충전 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
            ChargePointRequest request
    );

    @Operation(
            summary = "포인트 거래 내역 조회",
            description = "사용자의 포인트 충전/사용/환불 내역을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getPointTransactions();
}
