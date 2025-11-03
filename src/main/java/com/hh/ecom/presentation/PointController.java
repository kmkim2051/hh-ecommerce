package com.hh.ecom.presentation;

import com.hh.ecom.presentation.api.PointApi;
import com.hh.ecom.presentation.dto.request.ChargePointRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/points")
public class PointController implements PointApi {

    @Override
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getPointBalance() {
        Map<String, Object> response = new HashMap<>();

        response.put("userId", 1L);
        response.put("balance", 500000);

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/charge")
    public ResponseEntity<Map<String, Object>> chargePoint(@RequestBody ChargePointRequest request) {
        Map<String, Object> response = new HashMap<>();

        int currentBalance = 500000;
        int chargeAmount = request.amount();
        int balanceAfter = currentBalance + chargeAmount;

        response.put("userId", 1L);
        response.put("transactionId", System.currentTimeMillis() % 10000);
        response.put("type", "CHARGE");
        response.put("amount", chargeAmount);
        response.put("balanceBefore", currentBalance);
        response.put("balanceAfter", balanceAfter);
        response.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("message", chargeAmount + "원이 충전되었습니다.");

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getPointTransactions() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> transactions = new ArrayList<>();

        Map<String, Object> transaction1 = new HashMap<>();
        transaction1.put("id", 1L);
        transaction1.put("userId", 1L);
        transaction1.put("type", "CHARGE");
        transaction1.put("amount", 500000);
        transaction1.put("balanceAfter", 500000);
        transaction1.put("orderId", null);
        transaction1.put("createdAt", "2025-10-25T14:20:00");
        transactions.add(transaction1);

        Map<String, Object> transaction2 = new HashMap<>();
        transaction2.put("id", 2L);
        transaction2.put("userId", 1L);
        transaction2.put("type", "USE");
        transaction2.put("amount", -1570000);
        transaction2.put("balanceAfter", 500000);
        transaction2.put("orderId", 1L);
        transaction2.put("createdAt", "2025-10-30T10:30:00");
        transactions.add(transaction2);

        Map<String, Object> transaction3 = new HashMap<>();
        transaction3.put("id", 3L);
        transaction3.put("userId", 1L);
        transaction3.put("type", "REFUND");
        transaction3.put("amount", 1570000);
        transaction3.put("balanceAfter", 2070000);
        transaction3.put("orderId", 1L);
        transaction3.put("createdAt", "2025-10-30T14:15:00");
        transactions.add(transaction3);

        response.put("transactions", transactions);
        response.put("totalCount", transactions.size());
        response.put("currentBalance", 500000);

        return ResponseEntity.ok(response);
    }
}