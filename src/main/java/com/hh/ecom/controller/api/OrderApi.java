package com.hh.ecom.controller.api;

import com.hh.ecom.dto.request.CreateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Tag(name = "Order", description = "주문 관리 API")
public interface OrderApi {

    @Operation(
            summary = "주문 생성",
            description = "장바구니 상품을 주문하고 포인트로 결제합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "주문 생성 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "주문 생성 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
            CreateOrderRequest request
    );

    @Operation(
            summary = "주문 목록 조회",
            description = "현재 사용자의 전체 주문 내역을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getOrders();

    @Operation(
            summary = "주문 상세 조회",
            description = "특정 주문의 상세 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getOrder(
            @Parameter(description = "주문 ID", required = true)
            Long id
    );

    @Operation(
            summary = "주문 전체 취소",
            description = "주문을 취소하고 사용한 포인트를 환불합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "취소 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> cancelOrder(
            @Parameter(description = "주문 ID", required = true)
            Long id
    );
}
