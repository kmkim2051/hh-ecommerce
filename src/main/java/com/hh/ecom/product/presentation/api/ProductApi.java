package com.hh.ecom.product.presentation.api;


import com.hh.ecom.product.domain.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Product", description = "상품 관리 API")
public interface ProductApi {

    @Operation(
            summary = "상품 목록 조회",
            description = "전체 상품 목록을 페이징 처리하여 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Product.class))
            )
    })
    ResponseEntity<Page<Product>> getProducts(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            Integer page,
            @Parameter(description = "페이지 크기", example = "20")
            Integer size
    );

    @Operation(
            summary = "상품 상세 조회",
            description = "특정 상품의 상세 정보(가격, 재고 포함)를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Product.class))
            )
    })
    ResponseEntity<Product> getProduct(
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
                    content = @Content(schema = @Schema(implementation = Product.class))
            )
    })
    ResponseEntity<Product> getProductStock(
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
                    content = @Content(schema = @Schema(implementation = Product.class))
            )
    })
    ResponseEntity<List<Product>> getProductsByViewCount(
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
                    content = @Content(schema = @Schema(implementation = Product.class))
            )
    })
    ResponseEntity<List<Product>> getProductsBySalesCount(
            @Parameter(description = "조회할 상품 개수", required = false)
            Integer limit
    );
}
