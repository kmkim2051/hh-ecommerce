# Redis 기반 상품 조회수 집계 캐싱 전략 성능 개선 보고서

## 1. 개요

### 1.1 배경 및 목적
- 상품 조회수 기반 인기 상품 랭킹 조회의 성능 문제 식별
- 대량 트래픽 환경에서 DB 기반 집계 쿼리의 한계점 분석
- Redis 기반 캐싱 전략 도입을 통한 성능 개선 및 확장성 확보

### 1.2 핵심 성과 요약
- **응답 시간**: 최대 981.6ms → 10~20ms (약 98% 개선)
- **처리량**: Redis 부하 약 93% 감소 (메모리 버퍼링)
- **확장성**: 데이터 증가에도 O(log N) 복잡도로 일정한 성능 유지

---

## 2. 기존 시스템 분석

### 2.1 기존 아키텍처
- **구조**: RDBMS 기반 `product_views` 테이블에 조회 이력 저장
- **조회 방식**: 매 요청마다 GROUP BY 집계 쿼리 실행
- **문제점 식별**:
  - 조회 이력 row 무한 증가 (일별 10만~1000만 건)
  - 데이터 증가에 비례한 선형 성능 저하

### 2.2 성능 측정 결과 (기존 시스템)

| 조건 | 총 조회수 | 평균 응답 시간 | 비고 |
|------|----------|---------------|------|
| 상품 1,000개 × 조회수 100건 | 100,000건 | 67.8ms | 기준 |
| 상품 1,000개 × 조회수 1,000건 | 1,000,000건 | 166.2ms | 2.4배 증가 |
| 상품 1,000개 × 조회수 10,000건 | 10,000,000건 | 981.6ms | 14.4배 증가 |

### 2.3 쿼리 분석
```sql
-- 기존 쿼리 (예시)
SELECT product_id, COUNT(*) as view_count
FROM product_views
WHERE viewed_at >= NOW() - INTERVAL 7 DAY
GROUP BY product_id
ORDER BY view_count DESC
LIMIT 10;
```

**문제점**:
- WHERE 조건으로 수백만 row 스캔
- GROUP BY로 메모리 집계
- ORDER BY로 전체 정렬

---

## 3. Redis 캐싱 전략 설계

### 3.1 캐싱 대상 선정 근거
- **조회 빈도**: 높음 (메인 페이지, 카테고리 페이지 노출 등 가정)
- **데이터 특성**: 실시간 정확도보다 근사치 허용 가능
- **변경 빈도**: 초단위 변경이지만 분 단위 반영으로 충분
- **데이터 크기**: 고정 (상품 수에 비례, 조회수와 무관)

### 3.2 캐시 전략 비교 및 선택

#### 3.2.1 주요 캐시 패턴 분석

| 패턴 | 설명 | 장점 | 단점 | 적합성 |
|------|------|------|------|--------|
| **Look-Aside (Cache-Aside)** | 읽기 시 캐시 확인 → Miss 시 DB 조회 → 캐시 저장 | 캐시 장애 시에도 동작, 필요한 데이터만 캐싱 | 초기 조회 느림, 캐시/DB 불일치 가능 | ❌ 쓰기 많음 |
| **Read-Through** | 캐시가 DB 읽기 책임, 자동 로딩 | 일관된 인터페이스 | 초기 로딩 느림 | ❌ 집계 로직 부적합 |
| **Write-Through** | 쓰기 시 캐시 + DB 동시 업데이트 | 강한 일관성 | 쓰기 지연, 불필요한 캐시 | ❌ 쓰기 부하 높음 |
| **Write-Behind (Write-Back)** | 캐시에만 쓰고 비동기 DB 반영 | 쓰기 성능 우수, 배치 처리 | 데이터 유실 위험, 복잡도 | ✅ **선택** |
| **Refresh-Ahead** | TTL 전 선제적 갱신 | 항상 최신 | 불필요한 갱신 가능 | ❌ 변경 빈도 높음 |

#### 3.2.2 Write-Behind 전략 선택 근거

**1. 성능 최적화**:
- 쓰기: 메모리 → Redis (5~10건 배치) → DB (1분 배치)
- 읽기: Redis Sorted Set 직접 조회 (O(log N))
- 네트워크 왕복 최소화

**2. 도메인 특성 부합**:
- Eventual Consistency 허용 (1분 지연 수용 가능)
- 쓰기 빈도 높음 (최대 초당 수천 건)
- 읽기 빈도 높음 (특정 기간 랭킹)

**3. 확장성**:
- DB 부하 분산 (배치 처리)
- 메모리 버퍼로 Redis 부하도 감소

#### 3.2.3 구현 구조
```
[조회수 발생] → [메모리 버퍼] → [Redis 캐시] → [DB 영구 저장]
    ↓              ↓ 5~10건       ↓ 1분 배치     ↓
  즉시            배치 쓰기       비동기 쓰기    정합성
```

**Write-Behind 세부 흐름**:
1. **조회수 발생**: 애플리케이션 메모리 버퍼에 누적 (ConcurrentHashMap)
2. **임계값 도달**: Redis에 배치 flush (5~10건 단위, 랜덤 임계값)
3. **주기적 동기화**: 1분마다 Redis delta → DB 반영
4. **데이터 보호**: GETDEL 원자적 연산으로 Race Condition 방지

### 3.3 Redis 자료구조 선택

#### 3.3.1 Delta 저장 (String)
```
Key: product:view:delta:{productId}
Value: 누적 조회수 (Long)
TTL: 없음 (스케줄러가 삭제)
```

#### 3.3.2 기간별 랭킹 (Sorted Set)
```
Key: product:view:recent{1d|3d|7d}
Member: productId (String)
Score: 조회수 (Double)
TTL: 각각 1일/3일/7일
```

**Sorted Set 선택 이유**:
- 자동 정렬 유지 (Skip List)
- O(log N + M) 조회 성능
- ZINCRBY로 원자적 증가
- TTL로 자동 데이터 만료

---

## 4. 구현 상세

### 4.1 계층 구조
```
[Controller] → [ProductService] → [ViewCountRepository Interface]
                                          ↓
                                   [RedisViewCountRepository]
                                          ↓
                                   [Redis (Docker)]

[Scheduler] → [ViewCountRepository] → [JdbcTemplate] → [MySQL]
```

### 4.2 주요 컴포넌트

#### 4.2.1 메모리 버퍼링 (Application Layer)
**목적**: Redis 네트워크 오버헤드 최소화 및 처리량 극대화

- `ConcurrentHashMap<Long, AtomicInteger>` 사용
- 상품별 독립 카운터, 스레드 안전성 보장
- 랜덤 임계값 (5~10) 도달 시 Redis flush
- Redis 호출 횟수 약 93% 감소 효과

#### 4.2.2 Redis 저장 (RedisViewCountRepository)
**델타 방식 선택 이유**: 절대값 대신 증분만 저장하여 동시성 문제 해결 및 스케줄러 동기화 단순화

- 델타 증가: `INCRBY product:view:delta:{id} {count}`
- 랭킹 업데이트: `ZINCRBY product:view:recent{period} {count} {productId}`
- TTL 갱신: 매 업데이트마다 EXPIRE 실행
- **원자성 보장**: `GETDEL` 명령어로 Race Condition 방지 (Redis 6.2+)

#### 4.2.3 DB 동기화 (ProductViewFlushScheduler)
- `@Scheduled(fixedRate = 60000)` 1분 주기
- Redis deltas 읽기 → DB 업데이트 → Redis deltas 삭제
- Fail-Fast 예외 처리 (데이터 유실 방지)

### 4.3 데이터 일관성 전략

#### 4.3.1 다층 버퍼 구조
```
[Memory Buffer] --5~10건--> [Redis] --1분--> [MySQL]
     ↓ 초단위              ↓ 분단위        ↓ 영구 저장
  일시적 불일치          근사치          정합성
```

#### 4.3.2 동시성 제어 및 Race Condition 방지

**문제 상황**:
```
Thread 1 (Scheduler)              Thread 2 (User Request)
----------------------------------------------------------
1. GET delta:1 → 100
                                  2. INCR delta:1 → 150
3. DELETE delta:1
→ 결과: 50 유실!
```

**해결 방안: GETDEL 원자적 연산 (Redis 6.2+)**

```java
// 기존: GET + DELETE (Race Condition 발생)
Long delta = redisTemplate.opsForValue().get(key);
redisTemplate.delete(key);

// 개선: GETDEL (원자적 실행)
Long delta = redisTemplate.opsForValue().getAndDelete(key);
```
- 읽기와 삭제가 단일 원자적 명령으로 실행
- 동시성 환경에서 데이터 무결성 보장
- 네트워크 왕복 50% 감소 (2회 → 1회)

**검증**:
```
Thread 1 (Scheduler)              Thread 2 (User Request)
----------------------------------------------------------
1. GETDEL delta:1 → 100 반환 + 키 삭제
2. INCR delta:1 → 1 (새 키)
→ 결과: 100 반영, 1은 다음 주기 반영 (유실 없음)
```

#### 4.3.3 예외 처리
- **Redis 장애**: ViewCountFlushException 발생, 다음 주기 재시도
- **DB 장애**: 상품별 부분 성공 허용, 실패 로깅
- **데이터 복구**: Redis 장애 시 deltas 보존, 복구 후 자동 반영

---

## 5. 성능 측정 결과

### 5.1 응답 시간 비교

| 데이터 규모 | 기존 (DB) | 개선 (Redis) | 개선율 |
|------------|----------|-------------|--------|
| 10만 건 | 67.8ms | 12ms | 82.3% ↓ |
| 100만 건 | 166.2ms | 15ms | 91.0% ↓ |
| 1000만 건 | 981.6ms | 18ms | 98.2% ↓ |

### 5.2 처리량 개선

**시나리오**: 상품 100회 조회 발생 시

| 항목 | 기존 | 개선 | 감소율 |
|------|------|------|--------|
| Redis 호출 횟수 | 400회 | 28회 | 93% ↓ |
| 네트워크 요청 | 매우 빈번 | 5~10배 감소 | - |
| DB Write | 즉시 | 1분 배치 | 부하 분산 |

### 5.3 확장성 분석

**데이터 증가에 따른 성능**:
- **기존**: O(N log N) - 선형 증가
- **개선**: O(log N + M) - 거의 일정

상품 1,000개 → 10,000개 확장 시:
- 기존: 약 10배 응답 시간 증가 예상
- 개선: 2ms 미만 증가 (거의 일정)

---

## 6. 장단점 분석

### 6.1 장점
1. **극적인 성능 개선**: 최대 98.2% 응답 시간 감소
2. **확장성**: 데이터 증가에도 일정한 성능
3. **DB 부하 감소**: Write-Behind로 쓰기 부하 분산
4. **자동 데이터 관리**: TTL로 오래된 데이터 자동 삭제
5. **비용 효율**: 메모리 사용량 고정 (상품 수에 비례)

### 6.2 단점 및 제약사항
1. **Eventual Consistency**: 최대 1분 지연 (허용 가능)
2. **Redis 의존성**: Redis 장애 시 랭킹 조회 불가
3. **메모리 사용**: Redis 추가 인프라 필요
4. **복잡도 증가**: 다층 버퍼 관리 로직

### 6.3 트레이드오프 평가
- **정확도 vs 성능**: 1분 지연 허용하여 98% 성능 향상 → 합리적
- **복잡도 vs 효율**: 메모리 버퍼링으로 Redis 부하 93% 감소 → 효과적
- **인프라 비용 vs 처리량**: Redis 추가 비용 대비 10배 이상 처리량 확보 → 경제적

---

## 7. 한계점 및 개선 방안

### 7.1 현재 한계점

#### 7.1.1 데이터 유실 위험
**문제**: 애플리케이션 메모리 버퍼 또는 Redis 장애 시 최대 1분간의 데이터 유실 가능

**완화 방안**:
- (적용) GETDEL 원자적 연산으로 Race Condition 방지
- (적용) Fail-Fast 예외 처리로 Redis 장애 시 데이터 최대한 보존
- (향후) Redis Persistence 설정 (AOF/RDB)

#### 7.1.2 단일 장애점 (SPOF)
**문제**: Redis 장애 시 랭킹 조회 불가

**완화 방안**:
- Redis Sentinel (자동 장애 복구)
- Redis Cluster (고가용성)
- Fallback to DB 로직 추가

#### 7.1.3 캐시 콜드 스타트
**문제**: 서버 재시작 시 빈 캐시 상태, 초기 성능 저하

**완화 방안**:
- 향후: 캐시 워밍 전략 (DB에서 최근 데이터 사전 로드)
- 향후: Redis Persistence 활용

#### 7.1.4 Eventual Consistency
**문제**: 최대 1분 데이터 지연

**평가**:
- 비즈니스 요구사항 상 허용 가능 (인기 상품 랭킹은 실시간 정확도 불필요)
- 98% 성능 향상 대비 합리적인 트레이드오프

#### 7.1.5 캐시 스탬피드 (Cache Stampede) 분석

**현재 상태: 발생하지 않음** 


1. **Write-Behind 패턴 사용**
   - Redis는 조회수 카운팅 및 랭킹만 담당
   - 상품 정보는 항상 DB에서 직접 조회
   - Look-Aside 패턴이 아니므로 Cache Miss → DB 급증 시나리오 없음

2. **TTL 만료 시 DB 조회 없음**

3. **점진적 데이터 누적**
   - 캐시 재생성(Cache Population) 개념 없음
   - 조회수는 점진적으로 누적되므로 동시 요청 문제 없음

**향후 스탬피드 발생 가능 케이스**:

**케이스 1: 상품 정보 캐싱 추가 시 (Look-Aside 패턴)**
```java
// ⚠️ 스탬피드 위험
public Product getProduct(Long id) {
    Product cached = redisTemplate.get("product:" + id);
    if (cached != null) return cached;

    // TTL 만료 직후 대량 요청 → 모두 DB로 몰림
    Product product = findProductById(id);
    redisTemplate.set("product:" + id, product, 1, TimeUnit.HOURS);
    return product;
}
```

**완화 방안**:
- PER (Probabilistic Early Recomputation): TTL 만료 전 재계산
- 분산 락 (Redisson): 첫 번째 요청만 DB 조회, 나머지는 대기

**케이스 2: 인기 상품 목록 캐싱 추가 시**
```java
// ⚠️ 스탬피드 위험
public List<Product> getTopProducts() {
    List<Product> cached = redisTemplate.get("top:products");
    if (cached != null) return cached;

    // TTL 만료 시점에 모든 요청이 DB 집계 쿼리 실행
    List<Product> topProducts = expensiveAggregationQuery();
    redisTemplate.set("top:products", topProducts, 5, TimeUnit.MINUTES);
    return topProducts;
}
```

**완화 방안**:
- Refresh-Ahead: 백그라운드에서 선제적 갱신
- Cache Warming: 서버 시작 시 미리 캐시 적재


## 8. 결론

### 8.1 문제 해결 성과

**배경**: DB 기반 집계의 성능 한계 (1000만 건 시 981.6ms)

**해결**: Redis Write-Behind 전략 도입

**결과**: 최대 98.2% 응답 시간 감소 (18ms), 확장성 확보

### 8.2 목표 달성도
- ✅ **성능**: 대량 트래픽 환경에서 안정적인 10~20ms 응답 시간
- ✅ **확장성**: 데이터 증가에도 O(log N) 복잡도로 일정한 성능
- ✅ **효율성**: DB 부하 분산, Redis 호출 93% 감소
- ✅ **안정성**: Fail-Fast 예외 처리, 원자적 연산으로 데이터 무결성 보장

