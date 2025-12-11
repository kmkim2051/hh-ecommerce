# E-Commerce 시스템 MSA 전환 설계

**작성일:** 2025-12-12 \
**작성자:** 김경민 (with Claude Code)

## 1. 현재 아키텍처 분석

- 기존 이커머스 시스템은 전형적인 모놀리식 구조로 구성 
- 단일 Spring Boot 애플리케이션에 모든 도메인 기능이 포함
- 단일 DB(MySQL) 인스턴스와 Redis로 데이터 관리
- 도메인은 패키지 레벨에서만 논리적으로 분리

### 현재 구조
```
[단일 서버]
├── User (회원 관리)
├── Product (상품 관리)
├── Order (주문 처리)
├── Coupon (쿠폰 발급)
├── Point (포인트 관리)
└── Cart (장바구니)
        ↓
   [단일 MySQL DB]
```

트랜잭션은 Spring의 `@Transactional`로 관리되며, 주문 생성 시 Order, Point, Coupon, Product 테이블을 단일 트랜잭션 내에서 원자적으로 처리할 수 있다. 이는 개발 생산성 측면에서 유리하며, 장애 발생 시 자동 롤백을 통한 데이터 정합성 보장이 가능하다.

- 그러나 트래픽 증가에 따른 확장성 문제가 발생할 수 있음
- 선착순 쿠폰 발급 이벤트와 같은 순간적 트래픽 급증 시 전체 서버 성능이 저하 가능
- 주문 처리는 여러 도메인을 거치는 큰 트랜잭션으로 구성
- 이러한 설계는 시스템 규모가 커지고, 장애 가능성 및 전파 영향이 증가할수록 MSA 전환의 필요성 존재

## 2. MSA 전환 설계

### 도메인별 서비스 분리 전략

비즈니스 특성 및 ERD 구조를 분석한 결과, 다음과 같이 5개의 독립 서비스로 분리하는 것이 적절하다고 판단되었다:

**1. User Service** (회원/포인트)
- 회원 가입, 로그인, 프로필 관리
- 포인트 계좌 관리 (User:Point = 1:1 관계)
- 포인트 충전, 사용, 환불 처리
- 포인트 트랜잭션 이력 관리
- 타 서비스는 userId를 통한 논리적 참조만 수행

**2. Product Service** (상품/카탈로그)
- 상품 조회, 검색, 재고 관리
- 읽기 위주 트래픽 특성으로 캐싱 및 Read Replica 구성 용이
- 판매 랭킹 시스템 포함 (Redis 기반)

**3. Order Service** (주문/결제)
- 주문 생성 및 조회
- 가장 높은 트랜잭션 복잡도
- User, Product, Coupon 서비스와 이벤트 기반 연동
- Saga Orchestrator 역할 수행 가능

**4. Coupon Service** (쿠폰)
- 쿠폰 발급 및 사용 처리
- 이벤트성 트래픽 급증 발생 (선착순 쿠폰 등)
- 독립적 스케일아웃 필요성 높음
- In-memory 저장소 (Redis) 기반 수량 관리 및 Queue 처리

### DB 분리 전략

"Database per Service" 를 적용하여 각 서비스가 독립적으로 데이터를 관리하도록 설계한다.

```
[사용자 도메인]
User Service → User DB (users, points, point_transactions)

[상품 도메인]
Product Service → Product DB (products, cart_items, sales_ranking)

[주문 도메인]
Order Service → Order DB (orders, order_items, outbox_events)

[쿠폰 도메인]
Coupon Service → Coupon DB (coupons, coupon_users)
```

**주요 변경 사항:**
- Point 관련 테이블 → User Service DB로 이동 (User와 1:1 관계 유지)
- Cart 관련 테이블 → Product Service DB로 이동 (상품 도메인과 응집도 높음)
- Outbox Event → Order Service DB에 유지 (트랜잭션 원자성 보장)

- DB 분리로 인해 **기존 JOIN 기반 조회가 불가능**
  - 예를 들어, 주문 테이블에서 상품 테이블로의 직접 JOIN은 서로 다른 DB에 위치하게 되어 실행할 수 없다. 
  - 이는 테이블 구조 변경 및 데이터 역정규화를 통해 해결하며, 구체적인 대응 방안은 이후 섹션에서 다룬다.

## 3. 설계 전후 변경점
단순화된 서비스 로직 예시

### Before: 모놀리식 
```java
@Transactional
public Order createOrder(Long userId, CreateOrderCommand command) {
    // 단일 트랜잭션 내 모든 테이블 접근
    Order order = orderRepository.save(newOrder);
    productService.decreaseStock(productId, quantity);  // 동일 DB
    pointService.usePoint(userId, amount);              // 동일 DB
    couponService.useCoupon(couponId);                  // 동일 DB
    return order;  // 원자성 보장 (All-or-Nothing)
}
```

### After: MSA
```java
public Order createOrder(Long userId, CreateOrderCommand command) {
    // 1. Order Service: 주문 생성 (Order DB)
    Order order = orderRepository.save(newOrder);

    // 2. 이벤트 발행
    eventPublisher.publishEvent(OrderCompletedEvent.from(order));

    // 3. 각 서비스가 독립적으로 처리
    // - Product Service: 재고 감소 (Product DB)
    // - Coupon Service: 쿠폰/포인트 사용 (Coupon DB)

    return order;  // ⚠️ 주문 생성 완료, 후속 처리는 비동기 진행
}
```

핵심 변경사항은 *단일 트랜잭션의 원자성 보장이 불가능* 해진다는 점이다.

### 주요 변경점

**1. 서버 분리**
- AS-IS: 단일 애플리케이션
- TO-BE: 4개 독립 서비스 (독립 배포 및 스케일링)

**2. DB 테이블 분리 및 역정규화**
```sql
-- AS-IS: JOIN 기반 조회
SELECT o.*, p.name, p.price
FROM orders o
JOIN products p ON o.product_id = p.id;

-- TO-BE: 역정규화된 order_items 테이블
- product_name, price 컬럼을 직접 포함
- 조회 성능 향상, 단 데이터 불일치 가능성(정합성 이슈) 존재
```

**3. 외래키 제약조건 제거**
- Order 테이블의 `product_id`는 논리적 참조로만 존재
- DB 레벨 무결성 보장 불가 → 애플리케이션 레벨 검증 필요 (성능 이슈 발생 가능성)

## 4. 트랜잭션 처리의 한계와 대응 방안

### 문제 상황: 분산 트랜잭션의 한계

모놀리식 환경에서 주문 생성은 다음과 같이 단일 트랜잭션으로 처리되었다:
```
================ 단일 트랜잭션 시작 ================
1. 주문 생성 (orders)
2. 재고 감소 (products)
3. 포인트 차감 (points)
4. 쿠폰 사용 (coupon_users)
========== 단일 트랜잭션 종료 (커밋 또는 롤백) ========
→ 원자성 보장 (All-or-Nothing)
```

MSA 환경에서는 다음과 같은 불일치 문제가 발생할 수 있다:
```
[Order Service] 주문 생성 → 성공
[Product Service] 재고 감소 → 실패 (네트워크 오류)
[Coupon Service] 쿠폰 사용 → 성공
→ 데이터 불일치 발생 (주문 생성, 쿠폰 사용됐으나 재고는 그대로)
```

### 대응 방안 1: Saga 패턴

Saga 패턴은 분산 트랜잭션을 여러 개의 로컬 트랜잭션으로 분해하고, 실패 시 보상 트랜잭션으로 롤백하는 패턴이다.

#### 1-1. Choreography vs Orchestration

**Choreography 방식**
- 각 서비스가 이벤트를 발행하고 구독하여 자율적으로 동작
- 장점: 서비스 간 느슨한 결합, 확장 용이
- 단점: 전체 플로우 파악 어려움, 순환 의존성(event loop) 발생 가능

**Orchestration 방식**
- 중앙 Orchestrator가 각 서비스를 순차적으로 호출하며 트랜잭션 조율
- 장점: 전체 플로우 명확, 중앙 집중식 관리
- 단점: Orchestrator가 단일 장애점(SPOF) 또는 중앙 병목 지점, 서비스 간 강한 결합, 거대한 단일 클래스화 위험

본 프로젝트는 **Choreography 방식**을 적용 (이벤트 기반 아키텍처)

#### 1-2. Outbox 패턴과의 결합

Saga 패턴의 신뢰성을 높이기 위해 Outbox 패턴을 함께 사용한다:

```java
// Order Service
@Transactional
public Order createOrder(...) {
    Order order = orderRepository.save(newOrder);
    // Outbox 테이블에 이벤트 기록 (동일 트랜잭션)
    outboxRepository.save(OutboxEvent.of(order));
    return order;
}

// 스케줄러가 Outbox 폴링 후 메시지 발행
@Scheduled
public void publishEvents() {
    List<OutboxEvent> events = outboxRepository.findUnpublished();
    events.forEach(event -> {
        messageQueue.publish(event);  // Kafka, RabbitMQ
        event.markPublished();
    });
}
```

이를 통해 주문 저장과 이벤트 발행의 원자성을 보장하며, 메시지 큐 발행 실패 시 재시도가 가능하다.

#### 1-3. 보상 트랜잭션 (Saga의 핵심 메커니즘)

중간 단계 실패 시 이전 단계들을 롤백하는 보상 트랜잭션을 수행한다:

```
[정상 플로우]
Order → Point 차감 → Coupon 사용 → Product 재고 감소 → 완료

[실패 시 보상 트랜잭션]
Order → Point 차감 → Coupon 사용 → Product 재고 감소 실패 😞
                                    ↓
      Point 환불 ← Coupon 복구 ← Order 취소
```

현재는 이벤트 리스너에서 실패 로깅만 수행하고 있으나, 프로덕션 환경에서는 보상 이벤트 발행 로직이 필요하다.

### 대응 방안 2: TCC (Try-Confirm-Cancel) 패턴

TCC는 2단계 커밋(2PC)의 단점을 보완한 패턴으로, 각 서비스가 Try-Confirm-Cancel 3단계를 구현한다.
- 분산 락·전역 트랜잭션을 사용하는 2PC의 단점을 피하면서, 애플리케이션 레벨에서 보상 가능한 2단계 커밋을 구현한 방식이다.


**처리 흐름:**
```
[Try 단계] - 리소스 예약
- Product Service: 재고 10개 임시 예약 (재고는 감소하지 않음, 예약만)
- Point Service: 포인트 1000원 임시 홀드
- Coupon Service: 쿠폰 임시 점유

[Confirm 단계] - 모든 Try 성공 시 실제 커밋 (멱등성 요구)
- Product Service: 예약된 재고 10개 실제 차감
- Point Service: 홀드된 포인트 1000원 실제 차감
- Coupon Service: 쿠폰 실제 사용 처리

[Cancel 단계] - 하나라도 실패 시 예약 해제. (명시적인 롤백 기능 필요)
- Product Service: 재고 예약 해제
- Point Service: 포인트 홀드 해제
- Coupon Service: 쿠폰 점유 해제
```

**장단점:**
- 장점: Saga보다 강한 일관성 보장, 명시적 리소스 예약, 재시도/중복 호출에도 안전 (멱등성 전제)
- 단점: 구현 복잡도 높음 (각 서비스마다 3개 API 필요, 타임아웃 및 재시도 관리 복잡), 리소스 잠금으로 인한 성능 저하 또는 자원 고갈 가능

**적용 시나리오:** 금융 거래, 결제 처리 등 강한 일관성이 필수인 경우

### 대응 방안 3: 최종 일관성 (Eventual Consistency)

강한 일관성 대신 최종 일관성을 수용하는 접근 방식이다.

**적용 예시: 판매 랭킹 시스템**
- 주문 완료 → 이벤트 발행
- SalesRankingEventListener가 비동기로 Redis 기록
- 실패 시에도 주문은 유지, 랭킹은 후속 재처리

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompletedEvent(OrderCompletedEvent event) {
    try {
        salesRankingRepository.recordSales(event.getOrderId());
    } catch (Exception e) {
        // 주문은 완료 상태 유지, 재시도 큐에 적재
        log.error("랭킹 기록 실패, 재시도 큐 적재", e);
    }
}
```

**적용 시나리오:** 랭킹, 통계, 로그 등 즉시 일관성이 필수가 아닌 경우

## 5. 비동기 처리와 사용자 경험

MSA 환경에서 가장 중요한 과제는 비동기 처리 중인 작업의 상태를 사용자에게 적절히 전달하는 것이다.

### 사례 분석: 쿠폰 발급 시스템

현재 시스템의 쿠폰 발급은 비동기 방식으로 구현되어 있음

**처리 흐름:**
```
1. 사용자 요청 → API 서버
2. Redis Queue 등록 (즉시 응답)
3. 백그라운드 Worker의 Queue 처리 및 DB 저장
4. 쿠폰 발급 완료
```

Worker 처리에 수 초가 소요될 수 있으나, 사용자 응답은 즉시 반환되어야 한다.

**구현된 응답 구조:**

```java
// API 응답
{
  "status": "QUEUED",
  "message": "쿠폰 발급 요청이 접수되었습니다. 곧 처리됩니다.",
  "userId": 123,
  "couponId": 456
}
```

사용자에게 제공되는 상태 정보:
- **QUEUED**: 발급 대기 중 (큐 등록 완료)
- **PROCESSING**: 처리 진행 중 (Worker 작업 중)
- **COMPLETED**: 발급 완료 (쿠폰함 확인 가능)

### UI/UX 구현 전략

**방법 1: 폴링 (Polling)**
- 클라이언트가 주기적으로 서버에 상태 조회 요청
- 장점: 구현 간단, 모든 브라우저 호환
- 단점: 불필요한 요청 발생, 서버 부하 증가, 실시간성 부족

```javascript
// 클라이언트의 주기적 상태 확인
setInterval(() => {
  fetch(`/coupons/status/${requestId}`)
    .then(res => {
      if (res.status === 'COMPLETED') {
        showSuccess('쿠폰 발급 완료!');
      }
    });
}, 1000);
```

**방법 2: 낙관적 UI (Optimistic UI)**
- 성공을 가정하고 즉시 UI 반영, 실패 시 롤백
- 장점: 즉각적인 사용자 피드백, UX 향상
- 단점: 실패 시 혼란 가능, 보상 로직 복잡도 증가

```javascript
// 성공 가정 하의 즉시 UI 반영
showCouponInMyList(newCoupon);  // 즉시 표시
// 백그라운드 실제 완료 확인
// 실패 시 알림 및 UI 롤백
```

**방법 3: SSE/WebSocket (권장)**
- 서버에서 클라이언트로 실시간 푸시
- 장점: 실시간성 보장, 불필요한 요청 제거, 서버 리소스 효율적
- 단점: 비교적 구현 복잡도 높음, 연결 관리 필요

```javascript
// 서버 푸시 기반 실시간 업데이트
const eventSource = new EventSource('/coupons/stream');
eventSource.onmessage = (event) => {
  if (event.data.status === 'COMPLETED') {
    showSuccess('쿠폰 발급 완료!');
  }
};
```

초기에는 폴링 방식으로 구현하고, 트래픽 추이를 모니터링한 후 SSE로 전환할 계획이다.

### 주문 처리 시나리오

주문 프로세스 역시 동일한 패턴을 적용한다:

```
1. 사용자 "주문하기" 클릭
2. Order Service: 주문 생성 (PENDING 상태)
3. 즉시 응답: "주문이 접수되었습니다"
4. 백그라운드 처리:
   - Point Service: 포인트 차감
   - Coupon Service: 쿠폰 사용
   - Product Service: 재고 감소
5. 모두 성공 → 주문 상태 변경 (PAID)
6. 사용자 알림: "결제 완료!"
```

일반적으로 1-2초 내에 완료되지만, 5초 이상 소요될 경우 단순 로딩 화면만으로는 사용자 경험이 저하된다.

**개선된 UX 설계:**
```
[즉시] "주문 접수됨! 결제 처리 중입니다..."
      프로그레스 바: 포인트 차감 중 → 쿠폰 적용 중 → 재고 확인 중
[완료 후] "결제 완료! 주문 상세 보기"
```

핵심은 상태를 세분화하여 진행 상황을 투명하게 제공하는 것이다.

## 6. 결론 및 전환 전략

### 단계적 전환 로드맵

일괄 전환의 리스크를 최소화하기 위해 다음과 같은 단계적 접근을 계획할 수 있다.

**Phase 1: Coupon Service 분리** (우선순위 높음)
- 쿠폰 도메인의 독립 서비스화
- 현재 Redis Queue + Worker 구조로 비동기 처리 중이므로 분리 용이
- 이벤트성 트래픽 급증에 대한 독립적 스케일아웃 가능
- 실패 시 타 서비스에 대한 영향도 최소화

**Phase 2: Product Service 분리**
- 상품 조회 기능의 독립화
- 장바구니(Cart) 포함 (상품 도메인과 응집도 높음)
- 재고 관리는 Order Service와 이벤트 기반 연동
- 랭킹, 상품 조회 등 읽기 비율이 압도적으로 높아 Read Replica 구성을 성능 최적화 시 효율적

**Phase 3: User Service 분리**
- 회원 관리 및 포인트 계좌 독립화
- Point는 User와 1:1 관계이므로 함께 분리
- 인증/인가 체계 재설계
- API Gateway에서 JWT 검증 수행

**Phase 4: Order Service 정리** (최종 안정화)
- 핵심 비즈니스 로직 보유
- Saga Orchestrator 역할 수행
- 모든 서비스와 이벤트 기반 통신 안정화

### 기대 효과 및 트레이드오프

**장점:**
- 선택적 스케일아웃 (쿠폰 이벤트 시 Coupon Service만 확장)
- 배포 독립성 (주문 로직 변경이 상품 서비스에 무영향)
- 장애 격리 (개별 서비스 장애 시 타 서비스 정상 운영)

**단점:**
- 개발 복잡도 증가 (분산 트랜잭션, 모니터링)
- 네트워크 레이턴시 추가 (서비스 간 통신)
- 운영 난이도 상승 (분산 로깅, 디버깅)

### 주요 고려사항

**1. 점진적 접근**
- 모놀리식 구조도 대부분의 중소규모 서비스에서는 타당한 선택지임
- 트래픽 병목이 실제로 발생하는 서비스부터 우선 분리

**2. 분산 트레이싱 필수**
- 다중 서비스 호출 시 로그 추적 복잡도 증가
- Zipkin, Jaeger 등의 추적 도구 도입 필요

**3. 모니터링 체계 강화**
- 보상 트랜잭션 실패, 이벤트 유실 감지
- Outbox 테이블 적체 모니터링

---

**결론:** 
- MSA는 만능 해결책이 아니라 트래픽과 조직 규모 증가에 따른 자연스러운 진화 과정이다.
- 본 프로젝트는 이미 이벤트 기반 아키텍처와 Outbox 패턴을 적용하여 MSA 전환의 기반을 마련
- 향후 서비스의 발전 상황에 따라 물리적 분리 단계를 진행 가능
- 핵심은 분산 시스템의 복잡도를 효과적으로 관리하며 점진적으로 전환하는 것


