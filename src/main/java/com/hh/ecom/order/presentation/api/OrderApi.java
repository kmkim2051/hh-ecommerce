package com.hh.ecom.order.presentation.api;

import com.hh.ecom.order.presentation.dto.response.OrderListResponse;
import com.hh.ecom.order.presentation.dto.response.OrderResponse;
import com.hh.ecom.product.presentation.dto.request.CreateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

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
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (주문 아이템 비어있음, 유효하지 않은 금액 등)"
            )
    })
    ResponseEntity<OrderResponse> createOrder(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER, example = "1", schema = @Schema(type = "integer"))
            Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "주문 생성 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateOrderRequest.class))
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
                    content = @Content(schema = @Schema(implementation = OrderListResponse.class))
            )
    })
    ResponseEntity<OrderListResponse> getOrders(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER, example = "1", schema = @Schema(type = "integer"))
            Long userId
    );

    @Operation(
            summary = "주문 상세 조회",
            description = "특정 주문의 상세 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "주문을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "주문에 대한 접근 권한 없음"
            )
    })
    ResponseEntity<OrderResponse> getOrder(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER, example = "1", schema = @Schema(type = "integer"))
            Long userId,
            @Parameter(name = "id", description = "주문 ID", required = true, in = ParameterIn.PATH, example = "1")
            Long id
    );
}
