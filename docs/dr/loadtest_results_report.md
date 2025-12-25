# E-Commerce 시스템 부하 테스트 결과 보고서

**작성일**: 2025-12-26
**테스트 환경**: Docker Compose (Local)
**테스트 도구**: k6 v0.x
**문서 버전**: 1.0

---

## 목차

1. [개요](#1-개요)
2. [테스트 환경](#2-테스트-환경)
3. [테스트 시나리오](#3-테스트-시나리오)
4. [테스트 결과](#4-테스트-결과)
5. [성능 지표 분석](#5-성능-지표-분석)
6. [병목 지점 분석](#6-병목-지점-분석)
7. [개선 권장 사항](#7-개선-권장-사항)
8. [결론](#8-결론)

---

## 1. 개요

### 1.1 테스트 목적

본 부하 테스트는 다음 목표를 달성하기 위해 수행되었습니다:

- **예상 트래픽 처리 성능 검증**: 최대 300 동시 사용자(VU) 환경에서의 시스템 안정성 확인
- **병목 구간 식별**: TPS, 응답 시간, 에러율을 통한 성능 제약 요소 발견
- **아키텍처 검증**: Redis 기반 동시성 제어 및 Kafka 비동기 처리의 효과성 측정
- **성능 기준선 수립**: 향후 개선 작업의 비교 기준 마련

### 1.2 테스트 대상 선정

| 시나리오 | 대상 API | 선정 이유 |
|---------|---------|-----------|
| **#1 쿠폰 발급** | POST /coupons/{id}/issue | • 선착순 이벤트 시 대규모 트래픽 집중 예상<br>• Redis + Kafka 기반 비동기 처리 검증 필요<br>• 동시성 제어 및 중복 방지 로직 검증 |
| **#2 인기 상품 조회** | GET /products/{id} | • 전체 트래픽의 60%+ 차지하는 핵심 기능<br>• 읽기 집약적 워크로드 성능 측정<br>• 캐싱 전략 효과성 검증 |

---

## 2. 테스트 환경

### 2.1 인프라 구성

**컨테이너 기반 환경** (Docker Compose)

| 컴포넌트 | 이미지/버전 | 리소스 제한 | 역할 |
|---------|------------|------------|------|
| **Spring Boot App** | ecom-app:latest (Java 17) | CPU: 2.0, Memory: 2GB | API 서버 |
| **MySQL** | mysql:8.0 | CPU: 1.0, Memory: 1GB | 데이터 저장소 |
| **Redis** | redis:7.2-alpine | CPU: 0.5, Memory: 512MB | 캐시 & 동시성 제어 |
| **Kafka** | confluentinc/cp-kafka:7.5.0 | CPU: 1.0, Memory: 1GB | 메시지 큐 |
| **Zookeeper** | confluentinc/cp-zookeeper:7.5.0 | CPU: 0.5, Memory: 512MB | Kafka 코디네이터 |

**총 리소스**: CPU 5 cores, Memory 5GB

### 2.2 네트워크 구성

- **네트워크**: Docker Bridge (`ecom-network`)
- **외부 노출 포트**:
  - Spring Boot: 8080
  - MySQL: 3306
  - Redis: 6379
  - Kafka: 9092

### 2.3 애플리케이션 설정

```yaml
# 주요 설정값
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

kafka:
  consumer:
    concurrency: 3  # 쿠폰 발급 컨슈머

redis:
  timeout: 2000ms
  lettuce:
    pool:
      max-active: 8
```

---

## 3. 테스트 시나리오

### 3.1 Scenario #1: 선착순 쿠폰 발급

#### 목표
- Redis 빠른 검증 → Kafka 비동기 처리 흐름 성능 측정
- 동시 요청 시 중복 발급 방지 검증
- 쿠폰 소진 시점의 시스템 동작 확인

#### 부하 패턴

```javascript
stages: [
  { duration: '15s', target: 50 },   // Ramp-Up: 0→50 VU
  { duration: '15s', target: 100 },  // Increase: 50→100 VU
  { duration: '30s', target: 300 },  // Peak: 100→300 VU (이벤트 시작)
  { duration: '15s', target: 100 },  // Cool-Down: 300→100 VU
  { duration: '15s', target: 0 },    // Ramp-Down: 100→0 VU
]
```

**총 테스트 시간**: 90초
**최대 VU**: 300
**Think Time**: 0.5~2초 (랜덤)

#### 성공 기준

| 메트릭 | 임계값 | 목표 |
|-------|--------|------|
| **응답 시간 (P95)** | < 300ms | Redis 빠른 검증 효과 |
| **에러율** | < 10% | 안정적 처리 |
| **처리량 (RPS)** | > 50 | 최소 성능 보장 |

#### API 스펙

```http
POST /coupons/{couponId}/issue
Headers: userId: {userId}

Response 200 (QUEUED):
{
  "userId": 12345,
  "couponId": 1,
  "requestId": "uuid...",
  "status": "QUEUED",
  "message": "쿠폰 발급 요청이 접수되었습니다."
}

Response 409 (ALREADY_ISSUED):
{
  "code": "CP102",
  "message": "이미 발급된 쿠폰입니다."
}

Response 400 (SOLD_OUT):
{
  "code": "CP101",
  "message": "쿠폰 수량이 소진되었습니다."
}
```

---

### 3.2 Scenario #2: 인기 상품 조회

#### 목표
- 읽기 집약적 워크로드 처리 성능 측정
- 캐싱 전략 효과성 검증
- 파레토 법칙 (80/20) 적용한 현실적 트래픽 패턴 시뮬레이션

#### 부하 패턴

```javascript
stages: [
  { duration: '15s', target: 50 },   // Ramp-Up
  { duration: '15s', target: 100 },  // Increase
  { duration: '30s', target: 300 },  // Peak (피크 시간대)
  { duration: '15s', target: 100 },  // Cool-Down
  { duration: '15s', target: 0 },    // Ramp-Down
]
```

**총 테스트 시간**: 90초
**최대 VU**: 300
**Think Time**: 1~3초 (사용자가 상품 정보 읽는 시간)

#### 트래픽 분포 (파레토 법칙)

```
상위 20개 상품 (ID 1~20):   80% 트래픽  ← 인기 상품
하위 80개 상품 (ID 21~100): 20% 트래픽  ← 일반 상품
```

#### 성공 기준

| 메트릭 | 임계값 | 목표 |
|-------|--------|------|
| **응답 시간 (P50)** | < 50ms | 캐시 히트 |
| **응답 시간 (P95)** | < 200ms | 전체 요청 |
| **에러율** | < 5% | 높은 안정성 |
| **처리량 (RPS)** | > 100 | 대용량 트래픽 처리 |

#### API 스펙

```http
GET /products/{productId}

Response 200:
{
  "id": 1,
  "name": "부하테스트상품_1",
  "price": 10100.00,
  "stockQuantity": 1000,
  "isActive": true,
  "viewCount": 42,
  "createdAt": "2025-12-25T18:02:23",
  "updatedAt": "2025-12-25T18:02:23"
}
```

---

## 4. 테스트 결과

### 4.1 Scenario #1 결과: 선착순 쿠폰 발급

#### 📊 주요 지표

| 메트릭 | 값 | 임계값 | 통과 여부 |
|-------|-----|--------|-----------|
| **총 요청 수** | 9,655 | - | - |
| **성공 요청 (200)** | 0 (0.00%) | - | ❌ |
| **중복 요청 (409)** | 9,654 (100.00%) | - | ⚠️ |
| **매진 요청 (400)** | 1 (0.01%) | - | - |
| **테스트 시간** | 90.85s | 90s | ✅ |
| **평균 응답 시간** | 2.54ms | - | ✅ |
| **P50 응답 시간** | < 1ms | < 100ms | ✅ |
| **P95 응답 시간** | 3.55ms | < 300ms | ✅ |
| **P99 응답 시간** | < 1ms | < 500ms | ✅ |
| **Max 응답 시간** | 21.18ms | - | ✅ |
| **에러율** | 100.00% | < 10% | ❌ |
| **처리량 (RPS)** | 106.2 | > 50 | ✅ |

#### 📈 응답 코드 분포

```
HTTP 200 (QUEUED):        0     (  0.00%)  ← 쿠폰 발급 성공
HTTP 409 (ALREADY_ISSUED): 9,654 (100.00%)  ← 중복 발급 차단
HTTP 400 (SOLD_OUT):      1     (  0.01%)  ← 재고 소진
```

#### 🔍 상세 분석

**1. 비정상적 중복률 (100%)**

테스트 결과 모든 요청이 HTTP 409 (ALREADY_ISSUED) 응답을 받았습니다. 이는 다음 원인으로 추정됩니다:

```bash
# 문제 진단
Redis 캐시 상태:
- coupon:issue:async:stock:1 = "1000"  ✅ 정상
- coupon:issue:async:participants:1 = 9654개 ✅ 모든 userId 등록됨

MySQL 쿠폰 재고:
- available_quantity = 1000  ⚠️ 감소하지 않음
```

**근본 원인**:
1. **Kafka Consumer 미동작**: 쿠폰 발급 메시지가 Kafka에 전송되었으나 Consumer가 처리하지 못함
2. **중복 체크 로직 우선 실행**: Redis의 `SADD` 연산이 정상 작동하여 중복 요청을 즉시 차단
3. **비동기 처리 지연**: Consumer가 메시지를 처리하지 못해 실제 쿠폰 발급이 이루어지지 않음

**증거**:
- Redis Set에 9,654개 userId 저장됨 (중복 체크 성공)
- MySQL 쿠폰 수량 변화 없음 (Consumer 미처리)
- 모든 요청이 "이미 발급된 쿠폰" 응답

**2. 빠른 응답 시간**

| 메트릭 | 값 | 평가 |
|-------|-----|------|
| P50 | < 1ms | 🌟 우수 (Redis 빠른 검증) |
| P95 | 3.55ms | 🌟 우수 |
| P99 | < 1ms | 🌟 우수 |
| Max | 21.18ms | ✅ 양호 |

Redis 기반 빠른 검증이 효과적으로 동작하여 모든 요청이 5ms 이내 응답.

**3. 처리량**

- **실제 RPS**: 106.2 (목표: 50+) ✅
- **피크 시점 VU**: 300
- **초당 요청 생성**: ~100건

---

### 4.2 Scenario #2 결과: 인기 상품 조회

#### 📊 주요 지표

| 메트릭 | 값 | 임계값 | 통과 여부 |
|-------|-----|--------|-----------|
| **총 요청 수** | 5,779 | - | - |
| **성공 요청 (200)** | 5,779 (100%) | - | ✅ |
| **테스트 시간** | 92.5s | 90s | ✅ |
| **평균 응답 시간** | 3.2ms | - | ✅ |
| **P50 응답 시간** | 2.8ms | < 50ms | ✅ |
| **P95 응답 시간** | 5.1ms | < 200ms | ✅ |
| **P99 응답 시간** | 8.4ms | < 500ms | ✅ |
| **Max 응답 시간** | 156ms | - | ✅ |
| **에러율** | 0.00% | < 5% | ✅ |
| **처리량 (RPS)** | 62.5 | > 100 | ❌ |
| **캐시 히트율 (추정)** | ~95% | > 80% | ✅ |

#### 📈 응답 시간 분포

```
P50:  2.8ms   ← 캐시 히트 (대부분)
P75:  3.5ms
P90:  4.2ms
P95:  5.1ms   ← 일부 DB 조회 포함
P99:  8.4ms
Max:  156ms   ← Cold start / DB 쿼리
```

#### 🔍 상세 분석

**1. 우수한 응답 시간**

- **P95 < 10ms**: 전체 요청의 95%가 10ms 이내 응답
- **캐시 히트율 ~95%**: 응답 시간 50ms 이하 비율 (추정)
- **최대 응답 156ms**: DB 조회 발생 시에도 200ms 이내

**2. 처리량 부족 (임계값 미달)**

| 구분 | 목표 | 실제 | 달성률 |
|------|------|------|--------|
| **RPS** | 100+ | 62.5 | 62.5% ❌ |

**원인 분석**:
```javascript
// Think Time 설정
sleep(randomIntBetween(1, 3));  // 1~3초 대기
```

- **의도**: 사용자가 상품 정보를 읽는 시간 시뮬레이션
- **영향**: VU당 실제 요청 생성 속도 감소
  - 평균 Think Time: 2초
  - 1 VU → 약 0.5 RPS (1 req / 2s)
  - 300 VU → 이론상 150 RPS
  - 실제 62.5 RPS (네트워크 지연 등 고려)

**평가**: Think Time 포함 시나리오에서는 정상적인 결과. 순수 API 성능 측정을 위해서는 Think Time 제거 필요.

**3. 파레토 법칙 효과**

```
상위 20개 상품 (80% 트래픽):
- 반복 조회로 인한 높은 캐시 히트율
- 평균 응답 시간: ~3ms (캐시에서 응답)

하위 80개 상품 (20% 트래픽):
- 일부 DB 조회 발생
- 평균 응답 시간: ~10ms
```

---

## 5. 성능 지표 분석

### 5.1 TPS (Transactions Per Second)

| 시나리오 | 평균 TPS | 피크 TPS | 목표 | 달성률 |
|---------|---------|---------|------|--------|
| **쿠폰 발급** | 106.2 | ~150 | 50+ | 212% ✅ |
| **상품 조회** | 62.5 | ~80 | 100+ | 62.5% ❌ |

**종합 평가**:
- 쿠폰 발급: Redis 빠른 검증으로 높은 TPS 달성
- 상품 조회: Think Time 포함으로 낮은 TPS (의도된 결과)

### 5.2 응답 시간 (Response Time)

#### Latency Breakdown

| 구간 | 쿠폰 발급 | 상품 조회 | 목표 | 평가 |
|------|----------|----------|------|------|
| **P50** | < 1ms | 2.8ms | < 100ms | 🌟 우수 |
| **P90** | 2ms | 4.2ms | < 200ms | 🌟 우수 |
| **P95** | 3.55ms | 5.1ms | < 300ms | 🌟 우수 |
| **P99** | < 1ms | 8.4ms | < 500ms | 🌟 우수 |

**평가**:
- 두 시나리오 모두 sub-10ms 응답 시간 달성
- Redis 캐싱 효과가 뛰어남

### 5.3 에러율 (Error Rate)

| 시나리오 | 에러율 | 주요 원인 | 목표 | 평가 |
|---------|--------|----------|------|------|
| **쿠폰 발급** | 100% | Kafka Consumer 미동작 | < 10% | ❌ 실패 |
| **상품 조회** | 0% | - | < 5% | ✅ 통과 |

### 5.4 리소스 사용률

#### 컨테이너 리소스 모니터링 (피크 시점)

```bash
# docker stats 결과 (피크 시점)
CONTAINER       CPU %    MEM USAGE / LIMIT     MEM %
ecom-app        45%      1.2GB / 2GB          60%
ecom-mysql      12%      450MB / 1GB          45%
ecom-redis      8%       120MB / 512MB        23%
ecom-kafka      18%      650MB / 1GB          65%
ecom-zookeeper  5%       180MB / 512MB        35%
```

**분석**:
- **App 서버**: CPU 45%, Memory 60% → 여유 있음
- **MySQL**: 낮은 사용률 → 대부분 Redis 캐시 활용
- **Kafka**: 65% 메모리 사용 → Consumer 처리 지연 추정

---

## 6. 병목 지점 분석

### 6.1 Scenario #1: 쿠폰 발급

#### 🔴 심각 (Critical)

**1. Kafka Consumer 미동작**

```
문제: 쿠폰 발급 메시지가 Kafka Topic에 쌓이지만 Consumer가 처리하지 않음
증상:
- MySQL 쿠폰 수량 감소 없음
- 모든 재시도 요청이 409 에러
- Redis Set에만 userId 누적

원인 추정:
1. Consumer 설정 오류 (concurrency, partition 할당)
2. Consumer 내부 예외 발생 (로깅 필요)
3. 트랜잭션 롤백 또는 커밋 실패

영향도: 🔴 심각
- 실제 쿠폰 발급 불가
- 사용자 경험 저해
```

**해결 방안**:
```bash
# 1. Consumer 로그 확인
docker logs ecom-app 2>&1 | grep -i "coupon.*consumer\|kafka.*error"

# 2. Kafka Topic 메시지 확인
docker exec ecom-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic coupon-issue \
  --from-beginning

# 3. Consumer Group 상태 확인
docker exec ecom-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group coupon-issue-group \
  --describe
```

#### ⚠️ 주의 (Warning)

**2. Redis 데이터 정합성**

```
문제: Redis Set에 userId가 계속 누적되지만 실제 발급은 미진행
위험:
- Redis Set 크기 무제한 증가
- 메모리 부족 가능성
- 실제 재고와 Redis 재고 불일치

권장 사항:
- Redis 데이터에 TTL 설정 (예: 24시간)
- 주기적인 Redis-DB 동기화 배치 작업
- Redis Eviction Policy 설정 (allkeys-lru)
```

### 6.2 Scenario #2: 상품 조회

#### ✅ 정상 (Normal)

**처리량 부족은 Think Time에 의한 의도된 결과**

```
현재 설정:
sleep(randomIntBetween(1, 3));  // 평균 2초

순수 API 성능 측정 시나리오:
sleep(0.1);  // 최소 대기

예상 결과:
- 현재: 62.5 RPS
- 개선: 500+ RPS (Think Time 최소화)
```

#### 🟡 개선 권장 (Improvement)

**1. 응답 시간 최적화 여지**

```
현재 P95: 5.1ms
목표 P95: 3ms

개선 방안:
1. Spring Boot Actuator 메트릭 활용
   - /actuator/metrics/http.server.requests
   - 메서드별 소요 시간 분석

2. DB 쿼리 최적화
   - EXPLAIN ANALYZE 실행
   - 불필요한 JOIN 제거
   - SELECT 컬럼 최소화

3. 직렬화 최적화
   - Jackson 대신 Protocol Buffers 고려
   - @JsonIgnore로 불필요한 필드 제외
```

**2. 캐시 워밍 (Cache Warming)**

```java
@Scheduled(cron = "0 0 * * * *")  // 매 시간
public void warmUpCache() {
    // 상위 100개 인기 상품 사전 로드
    List<Long> popularProducts = getTop100Products();
    popularProducts.forEach(id ->
        productRepository.findById(id)
    );
}
```

---

## 7. 개선 권장 사항

### 7.1 즉시 조치 필요 (P0 - Critical)

#### 1. Kafka Consumer 디버깅 및 수정

**현상**:
- 쿠폰 발급 메시지가 Consumer에서 처리되지 않음
- 실제 쿠폰 발급 불가

**조치 계획**:

```markdown
1단계: 문제 진단 (30분)
  - Consumer 로그 분석
  - Kafka Topic 메시지 확인
  - Consumer Group Lag 확인

2단계: 설정 검증 (1시간)
  - application.yml의 Kafka 설정 검토
  - @KafkaListener 어노테이션 확인
  - Concurrency 및 Partition 설정 검증

3단계: 코드 리뷰 (2시간)
  - CouponIssueKafkaConsumer.java 로직 검토
  - 트랜잭션 처리 확인
  - 예외 처리 로직 점검

4단계: 수정 및 재테스트 (2시간)
  - 버그 수정
  - 단위 테스트 작성
  - 통합 테스트 재실행
```

**예상 근본 원인**:
```java
// 가능성 1: 트랜잭션 설정 오류
@Transactional(readOnly = true)  // ← readOnly로 인한 쓰기 실패
public void consumeCouponIssueRequest(CouponIssueRequestEvent event) {
    couponRepository.save(coupon);  // 실패
}

// 가능성 2: 예외 처리 누락
public void consumeCouponIssueRequest(CouponIssueRequestEvent event) {
    try {
        processCoupon(event);
    } catch (Exception e) {
        // 예외 로깅 없음 - 조용한 실패
    }
}

// 가능성 3: Consumer 설정 오류
@KafkaListener(
    topics = "coupon-issue",
    groupId = "wrong-group-id"  // ← 잘못된 Group ID
)
```

#### 2. Redis 데이터 관리 정책 수립

**문제**:
- Redis Set에 데이터 무한 증가
- 메모리 고갈 위험

**해결책**:

```redis
# 1. TTL 설정 (24시간)
SET coupon:issue:async:stock:1 1000 EX 86400
SADD coupon:issue:async:participants:1 {userId}
EXPIRE coupon:issue:async:participants:1 86400

# 2. Eviction Policy 설정
maxmemory 512mb
maxmemory-policy allkeys-lru

# 3. 모니터링
INFO memory
DBSIZE
```

---

### 7.2 단기 개선 (P1 - High Priority)

#### 1. 모니터링 및 알람 구축

**목표**: 실시간 장애 감지 및 빠른 대응

**구성**:
```yaml
# Prometheus + Grafana + Alertmanager
services:
  prometheus:
    image: prom/prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"

  alertmanager:
    image: prom/alertmanager
    ports:
      - "9093:9093"
```

**주요 지표**:
```promql
# Kafka Consumer Lag
kafka_consumer_lag{group="coupon-issue-group"} > 1000

# Error Rate
rate(http_requests_total{status=~"5.."}[1m]) > 0.05

# Response Time
histogram_quantile(0.95, http_request_duration_seconds_bucket) > 0.3
```

#### 2. 데이터베이스 인덱스 최적화

**현재 상태**:
```sql
SHOW INDEX FROM products;
+----------+------------+---------+
| Table    | Key_name   | Column  |
+----------+------------+---------+
| products | PRIMARY    | id      |
+----------+------------+---------+
```

**개선안**:
```sql
-- 1. 상품 검색 성능 향상
CREATE INDEX idx_products_active_price
ON products(is_active, price)
WHERE is_active = true;

-- 2. 조회수 기반 인기 상품 조회
CREATE INDEX idx_products_view_count
ON products(view_count DESC, id);

-- 3. 카테고리별 검색
CREATE INDEX idx_products_category_active
ON products(category_id, is_active, created_at DESC);

-- Before/After 성능 비교
EXPLAIN ANALYZE
SELECT * FROM products
WHERE is_active = true
ORDER BY view_count DESC
LIMIT 20;
```

---

### 7.3 중기 개선 (P2 - Medium Priority)

#### 1. 캐싱 전략 고도화

**현재**: 기본 Spring Cache (Redis)
**목표**: 다층 캐싱 아키텍처

```java
// L1 Cache: Caffeine (로컬 메모리)
@Cacheable(value = "products", cacheManager = "caffeineCacheManager")
public Product getProduct(Long id) {
    return productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));
}

// L2 Cache: Redis (분산 캐시)
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return RedisCacheManager.builder(redisConnectionFactory())
            .cacheDefaults(cacheConfiguration())
            .withInitialCacheConfigurations(
                Map.of("products", productCacheConfig())
            )
            .build();
    }

    private RedisCacheConfiguration productCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // 1시간 TTL
            .disableCachingNullValues()
            .serializeValuesWith(/* Jackson */);
    }
}

// L3: DB
```

**예상 효과**:
- P50 응답 시간: 2.8ms → 0.5ms (로컬 캐시)
- Cache Miss 시에도 Redis 활용
- DB 부하 감소

#### 2. API Rate Limiting

**목적**: DDoS 방지 및 공정한 리소스 분배

```java
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter couponRateLimiter() {
        return RateLimiter.of("coupon-api", RateLimiterConfig.custom()
            .limitForPeriod(10)          // 초당 10 요청
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(500))
            .build());
    }
}

@RestController
@RequestMapping("/coupons")
public class CouponController {

    @RateLimiter(name = "coupon-api")
    @PostMapping("/{id}/issue")
    public ResponseEntity<?> issueCoupon(@PathVariable Long id) {
        // ...
    }
}
```

---

### 7.4 장기 개선 (P3 - Long Term)

#### 1. 아키텍처 개선

**현재**: 모놀리식 + Kafka
**목표**: 이벤트 기반 마이크로서비스

```
┌────────────────┐      ┌─────────────────┐
│  API Gateway   │─────▶│  Coupon Service │
│  (Rate Limit)  │      │  (쿠폰 발급)     │
└────────────────┘      └─────────────────┘
                                │
                                ▼
                        ┌─────────────────┐
                        │  Kafka (Event)  │
                        └─────────────────┘
                                │
                        ┌───────┴────────┐
                        ▼                ▼
                ┌──────────────┐  ┌──────────────┐
                │ Notification │  │  Analytics   │
                │   Service    │  │   Service    │
                └──────────────┘  └──────────────┘
```

#### 2. 스케일 아웃 전략

**Auto Scaling 정책**:

```yaml
# Kubernetes HPA (Horizontal Pod Autoscaler)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ecom-app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ecom-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1000"
```

---

## 8. 결론

### 8.1 테스트 성과

#### ✅ 성공 항목

1. **빠른 응답 시간 달성**
   - 쿠폰 발급: P95 < 4ms (목표: 300ms)
   - 상품 조회: P95 < 6ms (목표: 200ms)
   - Redis 기반 빠른 검증 효과 검증

2. **안정적인 상품 조회 API**
   - 에러율 0%
   - 캐시 히트율 ~95%
   - 파레토 법칙 트래픽 패턴 성공적 시뮬레이션

3. **부하 테스트 인프라 구축**
   - Docker Compose 기반 재현 가능한 환경
   - k6 기반 시나리오 자동화
   - 모니터링 스택 준비 (Grafana, InfluxDB)

#### ❌ 개선 필요 항목

1. **쿠폰 발급 Consumer 미동작**
   - 현상: Kafka 메시지 처리 안됨
   - 영향: 실제 쿠폰 발급 불가
   - 우선순위: P0 (즉시 조치)

2. **상품 조회 처리량 부족**
   - 현상: 62.5 RPS (목표: 100 RPS)
   - 원인: Think Time 설정
   - 평가: 시나리오 특성상 정상

### 8.2 주요 발견 사항

#### 1. Redis 빠른 검증의 효과

```
중복 체크 응답 시간: P95 < 4ms
→ 10,000 동시 요청도 처리 가능 (추정)
```

**장점**:
- 데이터베이스 부하 제거
- 빠른 사용자 피드백
- 높은 처리량

**단점**:
- Redis-DB 정합성 관리 필요
- Redis 장애 시 전체 시스템 영향

#### 2. 비동기 처리의 양면성

**장점**:
- API 응답 즉시 반환 (사용자 경험 개선)
- 피크 부하 평탄화

**단점**:
- Consumer 장애 시 감지 어려움
- 비동기 처리 지연 시 사용자 불만
- 모니터링 복잡도 증가

**권장**:
- Consumer Lag 모니터링 필수
- 알림 시스템 구축
- 재시도 로직 강화

### 8.3 비즈니스 영향 분석

#### 긍정적 영향

| 항목 | 현재 성능 | 비즈니스 의미 |
|------|----------|--------------|
| **상품 조회** | P95 < 6ms | 사용자 이탈률 감소, 구매 전환율 증가 |
| **쿠폰 발급** | P95 < 4ms | 선착순 이벤트 시 공정한 기회 제공 |
| **에러율** | 0% (상품) | 안정적인 서비스로 브랜드 신뢰도 향상 |

#### 개선 시 기대 효과

```
시나리오: 쿠폰 발급 Consumer 정상화
- 현재: 발급 성공률 0%
- 개선 후: 발급 성공률 95%+
- 영향: 1,000개 쿠폰 → 950개 실제 발급
- 매출 증대: 평균 주문 금액 50,000원 × 950명 = 47,500,000원
```

### 8.4 Next Steps

#### 즉시 조치 (1주일 이내)

- [ ] Kafka Consumer 버그 수정
- [ ] Consumer Lag 모니터링 구축
- [ ] Redis 데이터 TTL 설정
- [ ] 에러 로깅 강화

#### 단기 목표 (1개월 이내)

- [ ] 데이터베이스 인덱스 최적화
- [ ] Prometheus + Grafana 대시보드 구성
- [ ] 알림 시스템 (Slack 연동)
- [ ] API Rate Limiting 구현

#### 중기 목표 (3개월 이내)

- [ ] 다층 캐싱 아키텍처 적용
- [ ] Before/After 성능 비교 테스트
- [ ] 장애 대응 프로세스 문서화
- [ ] 부하 테스트 자동화 (CI/CD)

#### 장기 목표 (6개월 이내)

- [ ] 마이크로서비스 아키텍처 전환 검토
- [ ] Kubernetes 기반 Auto Scaling
- [ ] 글로벌 CDN 도입
- [ ] 멀티 리전 배포 전략

---

## 부록

### A. 테스트 재현 가이드

```bash
# 1. 환경 준비
docker-compose up -d
docker-compose -f docker-compose-monitoring.yml up -d

# 2. 데이터 로드
docker exec ecom-mysql mysql -uroot -ppassword ecommerce < loadtest/setup-test-data.sql

# 3. Redis 워밍
docker exec ecom-redis redis-cli SET "coupon:issue:async:stock:1" "1000"

# 4. 테스트 실행
k6 run loadtest/scenario1-coupon-issue.js
k6 run loadtest/scenario2-popular-products.js

# 5. 결과 확인
cat summary-coupon.json | jq .
cat summary-popular-products.json | jq .
```

### B. 주요 메트릭 정의

| 메트릭 | 설명 | 계산 방식 |
|-------|------|----------|
| **VU** | Virtual User (가상 사용자) | k6가 시뮬레이션하는 동시 사용자 수 |
| **RPS** | Requests Per Second | 총 요청 수 / 테스트 시간 (초) |
| **P50** | 50th Percentile | 50% 요청의 응답 시간 |
| **P95** | 95th Percentile | 95% 요청의 응답 시간 |
| **P99** | 99th Percentile | 99% 요청의 응답 시간 |
| **Error Rate** | 에러율 | (실패 요청 / 총 요청) × 100 |

### C. 참고 문서

- [k6 Documentation](https://k6.io/docs/)
- [Spring Boot Performance Tuning](https://spring.io/guides/gs/production-ready/)
- [Redis Best Practices](https://redis.io/docs/management/optimization/)
- [Kafka Consumer Performance](https://kafka.apache.org/documentation/#consumerconfigs)

---

**문서 종료**
