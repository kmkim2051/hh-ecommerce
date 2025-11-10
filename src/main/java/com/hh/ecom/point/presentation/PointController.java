package com.hh.ecom.point.presentation;

import com.hh.ecom.point.application.PointService;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.presentation.api.PointApi;
import com.hh.ecom.product.presentation.dto.request.ChargePointRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointController implements PointApi {

    private final PointService pointService;

    @Override
    @GetMapping("/balance")
    public ResponseEntity<Point> getPointBalance(@RequestHeader("userId") Long userId) {
        Point point = pointService.getPoint(userId);
        return ResponseEntity.ok(point);
    }

    @Override
    @PostMapping("/charge")
    public ResponseEntity<Point> chargePoint(
            @RequestHeader("userId") Long userId,
            @RequestBody ChargePointRequest request
    ) {
        BigDecimal amount = BigDecimal.valueOf(request.amount());
        Point chargedPoint = pointService.chargePoint(userId, amount);
        return ResponseEntity.ok(chargedPoint);
    }

    @Override
    @GetMapping("/transactions")
    public ResponseEntity<List<PointTransaction>> getPointTransactions(@RequestHeader("userId") Long userId) {
        List<PointTransaction> transactions = pointService.getTransactionHistory(userId);
        return ResponseEntity.ok(transactions);
    }
}