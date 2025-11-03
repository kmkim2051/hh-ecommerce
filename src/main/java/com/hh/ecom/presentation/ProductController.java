package com.hh.ecom.presentation;

import com.hh.ecom.presentation.api.ProductApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @Override
    @GetMapping("/ranking/views")
    public ResponseEntity<Map<String, Object>> getProductsByViewCount(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> products = new ArrayList<>();

        Map<String, Object> product1 = new HashMap<>();
        product1.put("rank", 1);
        product1.put("id", 1L);
        product1.put("name", "노트북");
        product1.put("price", 1500000);
        product1.put("viewCount", 15230);
        products.add(product1);

        Map<String, Object> product2 = new HashMap<>();
        product2.put("rank", 2);
        product2.put("id", 4L);
        product2.put("name", "모니터");
        product2.put("price", 350000);
        product2.put("viewCount", 12450);
        products.add(product2);

        Map<String, Object> product3 = new HashMap<>();
        product3.put("rank", 3);
        product3.put("id", 3L);
        product3.put("name", "기계식 키보드");
        product3.put("price", 120000);
        product3.put("viewCount", 9870);
        products.add(product3);

        Map<String, Object> product4 = new HashMap<>();
        product4.put("rank", 4);
        product4.put("id", 5L);
        product4.put("name", "웹캠");
        product4.put("price", 85000);
        product4.put("viewCount", 7230);
        products.add(product4);

        Map<String, Object> product5 = new HashMap<>();
        product5.put("rank", 5);
        product5.put("id", 2L);
        product5.put("name", "무선 마우스");
        product5.put("price", 35000);
        product5.put("viewCount", 5890);
        products.add(product5);

        if (limit != null && limit > 0 && limit < products.size()) {
            products = products.subList(0, limit);
        }

        response.put("products", products);
        response.put("totalCount", products.size());
        response.put("rankingType", "VIEW_COUNT");

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/ranking/sales")
    public ResponseEntity<Map<String, Object>> getProductsBySalesCount(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> products = new ArrayList<>();

        Map<String, Object> product1 = new HashMap<>();
        product1.put("rank", 1);
        product1.put("id", 2L);
        product1.put("name", "무선 마우스");
        product1.put("price", 35000);
        product1.put("salesCount", 3420);
        products.add(product1);

        Map<String, Object> product2 = new HashMap<>();
        product2.put("rank", 2);
        product2.put("id", 1L);
        product2.put("name", "노트북");
        product2.put("price", 1500000);
        product2.put("salesCount", 2150);
        products.add(product2);

        Map<String, Object> product3 = new HashMap<>();
        product3.put("rank", 3);
        product3.put("id", 3L);
        product3.put("name", "기계식 키보드");
        product3.put("price", 120000);
        product3.put("salesCount", 1890);
        products.add(product3);

        Map<String, Object> product4 = new HashMap<>();
        product4.put("rank", 4);
        product4.put("id", 5L);
        product4.put("name", "웹캠");
        product4.put("price", 85000);
        product4.put("salesCount", 980);
        products.add(product4);

        Map<String, Object> product5 = new HashMap<>();
        product5.put("rank", 5);
        product5.put("id", 4L);
        product5.put("name", "모니터");
        product5.put("price", 350000);
        product5.put("salesCount", 750);
        products.add(product5);

        if (limit != null && limit > 0 && limit < products.size()) {
            products = products.subList(0, limit);
        }

        response.put("products", products);
        response.put("totalCount", products.size());
        response.put("rankingType", "SALES_COUNT");

        return ResponseEntity.ok(response);
    }
}