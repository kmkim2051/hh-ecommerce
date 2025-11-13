# 상품 판매량 조회 쿼리 성능 최적화 보고서

## 개요

본 문서는 판매량 상위 상품 조회 `ProductService.getProductsBySalesCount()` 메서드의 데이터베이스 쿼리 성능 최적화 과정과 결과를 기록합니다.

**최적화 대상 쿼리:**
```sql
SELECT
  oi.product_id as productId,
  SUM(oi.quantity) as salesCount
FROM order_items oi
INNER JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'COMPLETED'
GROUP BY oi.product_id
ORDER BY salesCount DESC
LIMIT 10
```
---

## 1. 최적화 전 상태
### 1.1 EXPLAIN 분석 결과 (최적화 전)

```sql
EXPLAIN
SELECT
  oi.product_id as productId,
  SUM(oi.quantity) as salesCount
FROM order_items oi
INNER JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'COMPLETED'
GROUP BY oi.product_id
ORDER BY salesCount DESC
LIMIT 10;
```

**실행 계획:**
```
-> Limit: 10 row(s)
    -> Sort: salesCount DESC, limit input to 10 row(s) per chunk
        -> Table scan on <temporary>
            -> Aggregate using temporary table
                -> Nested loop inner join  (cost=101525 rows=100000)
                    -> Table scan on oi  (cost=10152 rows=100000)
                        # 🔴 문제점: order_items 전체 테이블 스캔
                    -> Filter: (o.`status` = 'COMPLETED')  (cost=0.81 rows=1)
                        -> Single-row index lookup on o using PRIMARY (id=oi.order_id)
                        (cost=0.81 rows=1)
                        # 🔴 문제점: 100,000번의 PK 조회 발생
```

**주요 문제점:**

1. **Full Table Scan on order_items**
   - 전체 행을 모두 스캔
   - 인덱스를 사용하지 않음
   - Disk I/O 대량 발생

2. **Nested Loop 비효율**
   - orders 테이블 PK 조회 행 수 만큼 반복
   - JOIN 최적화 미비

3. **높은 Cost 추정**
   - MySQL 옵티마이저 Cost: **101,525**
   - 매우 비효율적인 실행 계획

---

## 2. 인덱스 설계 및 적용

### 2.1 Covering Index 전략

**핵심 개념:**
- **Covering Index**: 쿼리에 필요한 모든 컬럼을 인덱스에 포함하여 테이블 접근을 제거하는 기법
- 인덱스만으로 쿼리 실행 가능 → Disk I/O 최소화

**적용된 인덱스:**

#### 1) orders 테이블 인덱스
```sql
CREATE INDEX idx_orders_status_id ON orders(status, id);
```

**설계 이유:**
- `status = 'COMPLETED'` 조건 필터링 최적화
- `id`는 JOIN 키로 사용되므로 함께 포함
- Composite Index로 WHERE + JOIN 동시 최적화

#### 2) order_items 테이블 Covering Index
```sql
CREATE INDEX idx_order_items_join_group_covering
ON order_items(order_id, product_id, quantity);
```

**설계 이유:**

| 컬럼 순서 | 역할 | 이유 |
|----------|------|------|
| `order_id` | JOIN 키 | INNER JOIN 조건에서 가장 먼저 사용 |
| `product_id` | GROUP BY 키 | 집계 그룹핑에 사용 |
| `quantity` | SUM() 집계 대상 | **Covering Index 완성** (테이블 접근 제거) |

**왜 quantity도 인덱스에 포함하나?**

```
❌ quantity가 인덱스에 없을 때:
1. 인덱스에서 order_id, product_id 스캔
2. 각 행마다 테이블에서 quantity 컬럼 조회 (100,000번 테이블 접근)
3. SUM() 집계 수행

✅ quantity가 인덱스에 있을 때:
1. 인덱스에서 order_id, product_id, quantity 한번에 스캔
2. 테이블 접근 0번 (Index-Only Scan)
3. SUM() 집계 수행
```


**실행 순서:**
1. TestContainers MySQL 시작
2. `schema.sql` 실행 (테이블 + 인덱스 생성)
3. 테스트 실행

---

## 3. 최적화 후 상태

### 3.1 EXPLAIN 분석 결과 (최적화 후)

```sql
EXPLAIN
SELECT
  oi.product_id as productId,
  SUM(oi.quantity) as salesCount
FROM order_items oi
INNER JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'COMPLETED'
GROUP BY oi.product_id
ORDER BY salesCount DESC
LIMIT 10;
```

**실행 계획:**
```
-> Limit: 10 row(s)
    -> Sort: salesCount DESC
        -> Table scan on <temporary>
            -> Aggregate using temporary table
                -> Nested loop inner join  (cost=0.7 rows=1)
                    -> Covering index scan on oi using idx_order_items_join_group_covering
                        # ✅ 개선: Covering Index 사용, 테이블 접근 0번
                    -> Filter: (o.status = 'COMPLETED')
                        -> Single-row index lookup on o using PRIMARY
                        # ✅ 개선: PK 조회는 메모리 캐싱으로 매우 빠름 (O(1))
```


## 4. 성능 테스트 결과

### 4.1 테스트 환경

**프로덕션 환경 (예상):**
- MySQL 8.0 (Dedicated Server)
- SSD 스토리지
- 16GB+ 메모리
- Buffer Pool 최적화

**TestContainers 환경:**
- MySQL 8.0 (Docker Container)
- 컨테이너 오버헤드 존재
- 제한된 리소스 할당
- 호스트 파일 시스템 I/O

### 4.2 성능 측정 결과
TestContainers 환경

| 데이터 규모 | 인덱스 적용 전(평균) | 인덱스 적용 후 | 감소율 |
|------------|--------------|----------|-----|
| 1,000 rows | 50ms         | 40ms     | 20% |
| 10,000 rows | 100ms        | 75ms     | 25% |
| 100,000 rows | 150ms        | 100ms    | 33% |

**테스트 코드:**
```java
@Test
@DisplayName("판매량 기준 상품 조회 - 10만 건 성능 테스트 (인덱스 적용)")
void getProductsBySalesCount_100k_withIndex() {
    // Given: 10만 건 데이터 생성
    createTestDataWithJdbc(1000, 100_000);

    // When: 판매량 기준 조회
    long startQuery = System.currentTimeMillis();
    List<Product> result = productService.getProductsBySalesCount(10);
    long queryTime = System.currentTimeMillis() - startQuery;

    // Then: 성능 측정
    System.out.println("✓ 조회 시간: " + queryTime + "ms");
    assertThat(result).hasSize(10);
    assertThat(queryTime).isLessThan(2000); // 2초 이내
}
```

### 5.3 TestContainers 성능 한계

#### 5.3.1 컨테이너 환경의 제약사항

**1. 가상화 오버헤드**
- 컨테이너 네트워크 레이어 추가
- Volume Mount I/O 오버헤드
- 제한된 CPU/메모리 할당

**2. 파일 시스템 I/O 병목**
- Native MySQL (Bare Metal)
  - Application → MySQL → Direct SSD Access
  - 성능: 10,000 IOPS

- TestContainers 
  - Application → Docker Network → Container → Volume Mount → Host FS
  - 성능: 1,000-3,000 IOPS (3-10배 느림)

**3. Buffer Pool 미최적화**
- 프로덕션: 수 GB의 Buffer Pool (데이터 대부분 메모리 캐싱)
- TestContainers: 수백 MB (컨테이너 재시작 시 초기화)

**4. 테스트 데이터 생성 오버헤드**
- 매 테스트마다 TRUNCATE + INSERT
- 인덱스 재구축 시간 포함
- 통계 정보 미최적화

#### 5.3.2 TestContainers 사용 시 권장사항

**1. 성능 테스트 목적이 아닌 경우:**
```java
// ✅ 기능 테스트에는 적합
@Test
void shouldReturnTopSellingProducts() {
    // 소량 데이터로 비즈니스 로직 검증
    createTestData(100);
    List<Product> result = service.getTopProducts(10);
    assertThat(result).hasSize(10);
}
```

**2. 성능 테스트가 필요한 경우:**
```java
// ⚠️ 성능 절대값이 아닌 '인덱스 사용 여부' 검증
@Test
void shouldUseCoveringIndex() {
    // EXPLAIN 분석으로 인덱스 사용 확인
    String explain = jdbcTemplate.queryForObject(
        "EXPLAIN SELECT ... FROM order_items ...",
        String.class
    );
    assertThat(explain).contains("Covering index scan");
}
```

## 6. 추가 최적화 고려사항

### 6.1 현재 성능으로 충분한 경우 (프로덕션 기준)

현재 **100-200ms** 성능은 대부분의 실시간 조회 요구사항에 충분

**권장 사항**
- 현재 인덱스 유지
- 모니터링 추가 (쿼리 실행 시간, Slow Query Log)
- 데이터가 1000만 건 이상 증가 시 재검토

### 6.2 더 빠른 성능이 필요한 경우

**옵션 1: 집계 테이블 (Materialized View)**
```sql
CREATE TABLE product_sales_summary (
    product_id BIGINT PRIMARY KEY,
    sales_count BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sales_count (sales_count DESC)
);

-- 배치 작업으로 주기적 갱신 (예: 1시간마다)
INSERT INTO product_sales_summary (product_id, sales_count)
SELECT oi.product_id, SUM(oi.quantity)
FROM order_items oi
INNER JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'COMPLETED'
GROUP BY oi.product_id
ON DUPLICATE KEY UPDATE
    sales_count = VALUES(sales_count),
    updated_at = CURRENT_TIMESTAMP;

-- 조회 쿼리 (매우 빠름)
SELECT product_id, sales_count
FROM product_sales_summary
ORDER BY sales_count DESC
LIMIT 10;
```

**장점**
- 빠른 조회 시간 (단순 정렬 쿼리)
- 대규모 데이터에도 일정한 성능
- 인덱스만 스캔하면 되므로 매우 빠름

**단점**
- 실시간 데이터 아님 (배치 주기만큼 지연)
- 추가 테이블 관리 필요
- 스토리지 사용량 증가

**옵션 2: Redis 캐싱**
```java
@Cacheable(value = "topSalesProducts", key = "#limit")
public List<Product> getProductsBySalesCount(Integer limit) {
    return productRepository.findTopBySalesCount(limit);
}
```

**장점:**
- 첫 조회 후 캐시 히트 시 **1ms 이하**
- 구현 간단 (Spring Cache 애노테이션)
- TTL 설정으로 실시간성 조절 가능

**단점:**
- 캐시 무효화 전략 필요
- 메모리 사용량 증가
- 주문 발생 시 캐시 갱신 필요

---

## 7. 결론
#### 7.1. 적절한 인덱스 사용 (이번 경우에는 Covering Index 전략)
- 쿼리에 필요한 모든 컬럼을 인덱스에 포함
- 약간의 저장 공간 증가로 10-100배 성능 향상 가능
- Index-Only Scan은 최고의 성능 최적화 기법

#### 7.2. TestContainers의 한계 인식
- **기능 테스트**에는 적합, **성능 테스트**에는 부적합
- 컨테이너 오버헤드로 인해 실제 성능의 20-30% 수준
- 인덱스 효과는 확인 가능하나 절대값은 참고용
- 프로덕션 성능 예측은 별도 환경 필요


### 7.3 관련 SQL문
```sql
-- orders 테이블: status 필터링 최적화
CREATE INDEX idx_orders_status_id ON orders(status, id);

-- order_items 테이블: Covering Index (JOIN + GROUP BY + SUM 최적화)
CREATE INDEX idx_order_items_join_group_covering
ON order_items(order_id, product_id, quantity);
```
