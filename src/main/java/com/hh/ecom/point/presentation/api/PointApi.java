package com.hh.ecom.point.presentation.api;

import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.product.presentation.dto.request.ChargePointRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

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
                    content = @Content(schema = @Schema(implementation = Point.class))
            )
    })
    ResponseEntity<Point> getPointBalance(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER)
            @RequestHeader("userId") Long userId
    );

    @Operation(
            summary = "포인트 충전",
            description = "사용자의 포인트를 충전합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "충전 성공",
                    content = @Content(schema = @Schema(implementation = Point.class))
            )
    })
    ResponseEntity<Point> chargePoint(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER)
            @RequestHeader("userId") Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "포인트 충전 요청",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ChargePointRequest.class))
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
                    content = @Content(schema = @Schema(implementation = PointTransaction.class))
            )
    })
    ResponseEntity<List<PointTransaction>> getPointTransactions(
            @Parameter(name = "userId", description = "사용자 ID", required = true, in = ParameterIn.HEADER)
            @RequestHeader("userId") Long userId
    );
}
