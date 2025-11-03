package com.hh.ecom.controller;

import com.hh.ecom.controller.api.ProductApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/products")
public class ProductController implements ProductApi {

    @Override
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProducts() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> products = new ArrayList<>();

        Map<String, Object> product1 = new HashMap<>();
        product1.put("id", 1L);
        product1.put("name", "노트북");
        product1.put("price", 1500000);
        product1.put("description", "고성능 노트북");
        product1.put("stockQuantity", 50);
        products.add(product1);

        Map<String, Object> product2 = new HashMap<>();
        product2.put("id", 2L);
        product2.put("name", "무선 마우스");
        product2.put("price", 35000);
        product2.put("description", "편안한 무선 마우스");
        product2.put("stockQuantity", 200);
        products.add(product2);

        Map<String, Object> product3 = new HashMap<>();
        product3.put("id", 3L);
        product3.put("name", "기계식 키보드");
        product3.put("price", 120000);
        product3.put("description", "청축 기계식 키보드");
        product3.put("stockQuantity", 80);
        products.add(product3);

        Map<String, Object> product4 = new HashMap<>();
        product4.put("id", 4L);
        product4.put("name", "모니터");
        product4.put("price", 350000);
        product4.put("description", "27인치 4K 모니터");
        product4.put("stockQuantity", 30);
        products.add(product4);

        Map<String, Object> product5 = new HashMap<>();
        product5.put("id", 5L);
        product5.put("name", "웹캠");
        product5.put("price", 85000);
        product5.put("description", "1080p 웹캠");
        product5.put("stockQuantity", 100);
        products.add(product5);

        response.put("products", products);
        response.put("totalCount", products.size());

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", id);
        response.put("name", "노트북");
        response.put("price", 1500000);
        response.put("description", "고성능 노트북입니다. Intel Core i7 프로세서와 16GB RAM을 탑재했습니다.");
        response.put("stockQuantity", 50);
        response.put("category", "전자제품");

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}/stock")
    public ResponseEntity<Map<String, Object>> getProductStock(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        response.put("productId", id);
        response.put("productName", "노트북");
        response.put("stockQuantity", 50);
        response.put("available", true);

        return ResponseEntity.ok(response);
    }
}