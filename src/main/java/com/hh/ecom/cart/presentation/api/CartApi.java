package com.hh.ecom.cart.presentation.api;

import com.hh.ecom.cart.presentation.dto.CartItemListResponse;
import com.hh.ecom.cart.presentation.dto.CartItemMessageResponse;
import com.hh.ecom.product.presentation.dto.request.CreateCartItemRequest;
import com.hh.ecom.product.presentation.dto.request.UpdateCartItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Cart", description = "장바구니 관리 API")
public interface CartApi {

    @Operation(
            summary = "장바구니 목록 조회",
            description = "현재 사용자의 장바구니에 담긴 상품 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CartItemListResponse.class))
            )
    })
    ResponseEntity<CartItemListResponse> getCartItems(
            @Parameter(description = "사용자 ID", required = true)
            Long userId
    );

    @Operation(
            summary = "장바구니에 상품 추가",
            description = "선택한 상품을 장바구니에 추가합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "추가 성공",
                    content = @Content(schema = @Schema(implementation = CartItemMessageResponse.class))
            )
    })
    ResponseEntity<CartItemMessageResponse> addCartItem(
            @Parameter(description = "사용자 ID", required = true)
            Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "장바구니 추가 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateCartItemRequest.class))
            )
            CreateCartItemRequest request
    );

    @Operation(
            summary = "장바구니 상품 수량 수정",
            description = "장바구니에 담긴 상품의 수량을 변경합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = CartItemMessageResponse.class))
            )
    })
    ResponseEntity<CartItemMessageResponse> updateCartItemQuantity(
            @Parameter(description = "사용자 ID", required = true)
            Long userId,
            @Parameter(description = "장바구니 아이템 ID", required = true)
            Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수량 변경 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateCartItemRequest.class))
            )
            UpdateCartItemRequest request
    );

    @Operation(
            summary = "장바구니 상품 삭제",
            description = "장바구니에서 선택한 상품을 삭제합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(schema = @Schema(implementation = CartItemMessageResponse.class))
            )
    })
    ResponseEntity<CartItemMessageResponse> deleteCartItem(
            @Parameter(description = "사용자 ID", required = true)
            Long userId,
            @Parameter(description = "장바구니 아이템 ID", required = true)
            Long id
    );
}
