# Redis SortedSet 기반 판매 랭킹 시스템 구현 완료



# 7주차 전체 회고
```
- 7주차 주요 내용
기존 DB 의존 로직 중, 비동기 또는 캐싱 처리가 가능한 로직을 Redis로 전환하여 성능 향상

- 성능 측면
확실히 DB 보다 빠른 것이 느껴짐. API 호출 기준 수백ms 걸리던 로직도 100ms 이상을 넘어가지 않는다.

- 신뢰성 측면
조회 시 정합성, 쿠폰 발급 시 영속성 등 신경써야할 부분이 더 늘었다. 성능<->편리함의 trade-off라고 생각

- 코드 설계 측면
  - DB 강결합 로직을 interface화 해서 Redis 확장 도입하고, 여러 Util 클래스 도입하며 객체지향 설계의 중요성과 어려움을 다시 느낌.
  - 기능이 다양해질수록 새로운 책임(새로운 클래스)이 생겨나고, 확장 가능성을 열어둘지 의도적으로 분리할지 등 책만 봤을때는 고민하기 어려운 부분이 많다.  

- 그 외
  - 요구사항 분석 및 작성 -> AI 구현 -> 분석 -> 리팩터링 또는 커스텀 방식으로 주로 개발하는데,
  - 기반이 되는 기술 지식이 부족하면(이번 주는 레디스) AI 생산 코드에 끌려가는 느낌
  - 작은 규모라도 직접 공부하고 고민해본 후, AI 협업하는 과정의 필요성을 느낌 
```

## 개요

Redis SortedSet을 활용한 실시간 주문 판매 랭킹 시스템을 구현

## 구현된 컴포넌트

### 1. 도메인 계층

#### SalesRanking (domain object)
  - `productId`: 상품 ID
  - `salesCount`: 판매 수량

### 2. 인프라 계층

#### SalesRankingKeyGenerator
- **역할**: Redis Key 생성 전략 구현
- **주요 메서드**:
  - `generateAllTimeKey()`: 전체 기간 랭킹 키 (`product:ranking:sales:all`)
  - `generateDailyKey(LocalDate)`: 일별 랭킹 키 (`product:ranking:sales:daily:20251203`)
  - `generateDailyKeysForRange()`: 날짜 범위 내 모든 일별 키 생성
  - `generateRecordedOrderKey()`: 중복 기록 방지용 키

#### SalesRankingRedisRepository
- **역할**: Redis SortedSet 연산 캡슐화
- **주요 메서드**:
  - `incrementSalesCount()`: 판매량 증가 (ZINCRBY 사용, 원자적 연산)
  - `getTopProducts()`: Top N 상품 조회 (ZREVRANGE)
  - `getTopProductsInRecentDays()`: 최근 n일 Top N 조회 (ZUNIONSTORE)
  - `markOrderRecorded()`: 주문 기록 마킹 (중복 방지)

#### SalesRankingInitializer
- **역할**: 애플리케이션 시작 시 Redis 초기화
- **동작**: ApplicationRunner 구현체로 DB → Redis 데이터 동기화

### 4. 기존 컴포넌트 수정

#### OrderCommandService
- **수정 내용**: `updateOrderStatus()` 메서드에 판매량 메트릭 수집 로직 추가
- **트리거 조건**: 주문 상태가 `COMPLETED`로 전환될 때
- **동작**: 해당 주문의 모든 OrderItem을 Redis에 기록

```java
// COMPLETED 상태 전환 시 판매량 메트릭 수집
if (newStatus == OrderStatus.COMPLETED) {
    List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
    redisSalesRankingRepository.recordBatchSales(orderId, orderItems);
}
```
## Redis Key 구조

| Key Pattern | 설명 | 자료구조 | TTL |
|------------|------|---------|-----|
| `product:ranking:sales:all` | 전체 기간 판매량 랭킹 | SortedSet | 없음 |
| `product:ranking:sales:daily:{YYYYMMDD}` | 일별 판매량 랭킹 | SortedSet | 30일 |
| `product:ranking:sales:recorded:{orderId}` | 주문 기록 여부 (중복 방지) | String | 30일 |
| `product:ranking:sales:temp:{UUID}` | 임시 집계용 키 (ZUNIONSTORE 결과) | SortedSet | 즉시 삭제 |

## 주요 기능

### 1. 실시간 판매량 기록
- 주문이 `COMPLETED` 상태로 전환되면 자동으로 Redis에 판매량 기록
- ZINCRBY를 사용한 원자적 증가로 동시성 보장
- 중복 기록 방지 로직 포함

### 2. 고성능 랭킹 조회
- Redis SortedSet의 O(log N) 성능 활용
- 전체 기간 랭킹: `ZREVRANGE`로 즉시 조회
- 최근 n일 랭킹: `ZUNIONSTORE`로 일별 데이터 합산 후 조회

### 3. 자동 초기화
- 애플리케이션 시작 시 DB의 COMPLETED 주문 데이터를 Redis로 동기화
- 전체 기간 + 최근 30일 데이터 초기화


## 사용 예시

### 1. 판매 랭킹 조회

```java
// Redis 기반 전체 기간 Top 10 조회
List<Product> topProducts = productService.getProductsBySalesCountFromRedis(10);

// Redis 기반 최근 7일 Top 10 조회
List<Product> recentTopProducts = productService.getProductsBySalesCountInRecentDaysFromRedis(7, 10);
```

### 2. 주문 완료 처리 (자동으로 판매량 기록)

```java
// 주문 상태를 COMPLETED로 변경하면 자동으로 Redis에 판매량 기록됨
orderCommandService.updateOrderStatus(orderId, OrderStatus.COMPLETED);
```

## 설계 원칙

1. **이벤트 기반 메트릭 수집**: 주문 상태 변경 시점에 Redis 업데이트
2. **SortedSet 활용**: 자동 정렬 + O(log N) 성능
3. **TTL 기반 최근 n일**: 일별 SortedSet + 30일 만료 정책
4. **Graceful Degradation**: Redis 장애 시 DB 폴백
5. **중복 방지**: Redis Set을 활용한 멱등성 보장