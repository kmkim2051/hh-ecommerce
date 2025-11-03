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

@Tag(name = "Product", description = "상품 관리 API")
public interface ProductApi {

    @Operation(
            summary = "상품 목록 조회",
            description = "전체 상품 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getProducts();

    @Operation(
            summary = "상품 상세 조회",
            description = "특정 상품의 상세 정보(가격, 재고 포함)를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getProduct(
            @Parameter(description = "상품 ID", required = true)
            Long id
    );

    @Operation(
            summary = "상품 재고 조회",
            description = "특정 상품의 실시간 재고 수량을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getProductStock(
            @Parameter(description = "상품 ID", required = true)
            Long id
    );

    @Operation(
            summary = "조회수 기반 상품 순위 조회",
            description = "조회수가 높은 순으로 상품 순위를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getProductsByViewCount(
            @Parameter(description = "조회할 상품 개수", required = false)
            Integer limit
    );

    @Operation(
            summary = "판매량 기반 상품 순위 조회",
            description = "판매량이 높은 순으로 상품 순위를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            )
    })
    ResponseEntity<Map<String, Object>> getProductsBySalesCount(
            @Parameter(description = "조회할 상품 개수", required = false)
            Integer limit
    );
}
