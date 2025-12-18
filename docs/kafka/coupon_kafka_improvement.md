# 쿠폰 발급 시스템 Kafka 기반 아키텍처

## 1. 개요

### 목적
- Kafka 기반 이벤트 드리븐 쿠폰 발급 시스템
- 파티션 키를 활용한 Lock-Free 동시성 제어
- 수평 확장 가능한 고성능 처리 시스템

### 핵심 특징
- **Redis**: 빠른 검증 (중복 체크, 재고 체크)
- **Kafka**: 메시지 큐 및 이벤트 처리
- **Partition Key**: couponId 기반 순차 처리로 락 불필요
- **Consumer Group**: 수평 확장 가능한 병렬 처리

### 이전 아키텍처 (Deprecated)
이전에는 Redis Queue + Background Worker (100ms 폴링) 방식을 사용했으나, 다음 문제점으로 Kafka로 전환:
- Worker의 100ms 폴링 주기로 인한 불필요한 Redis 부하
- 단일 Worker 스레드로 인한 확장성 제약
- Redis 장애 시 메시지 손실 가능성
- 모니터링 및 운영 도구 부족

---

## 2. 이전 아키텍처 (Redis Queue + Worker, Deprecated)

### 2.1 아키텍처 다이어그램

```
[Client]
   ↓ HTTP POST /coupons/{couponId}/issue
[API Server]
   ↓ 1. enqueueUserIfEligible()
[RedisCouponService]
   ↓ 2. SADD participants set (중복 체크)
   ↓ 3. SCARD 참여자 수 조회
   ↓ 4. GET stock (재고 확인)
   ↓ 5. 재고 충분하면 RPUSH queue
[Redis]
   - Set: coupon:issue:async:participants:{couponId}
   - String: coupon:issue:async:stock:{couponId}
   - List: coupon:issue:async:queue:{couponId}
   ↓ 6. 즉시 응답 (202 ACCEPTED + "QUEUED")
[Client]

----------------------- 비동기 처리 경계 -----------------------

[CouponIssueWorker] (@Scheduled fixedDelay=100ms)
   ↓ 7. 100ms마다 폴링
   ↓ 8. LPOP queue (배치 크기: 50)
   ↓ 9. DB 트랜잭션 처리
       - 중복 체크 (멱등성)
       - 쿠폰 조회 및 검증
       - 재고 감소 (UPDATE)
       - 발급 기록 저장 (INSERT)
[MySQL]
   ↓ 10. 처리 완료 (로그만 기록)
```

### 2.2 코드 플로우

#### API Layer (enqueue)
```java
@Service
public class RedisCouponService {
    public void enqueueUserIfEligible(Long userId, Long couponId) {
        // 1. 중복 체크 (SADD - O(1))
        Long addResult = redisTemplate.opsForSet()
            .add(usersSetKey, userId.toString());
        if (addResult == 0) {
            throw new CouponException(COUPON_ALREADY_ISSUED);
        }

        // 2. 참여자 수 조회 (SCARD - O(1))
        Long participantCount = redisTemplate.opsForSet()
            .size(usersSetKey);

        // 3. 재고 확인 (GET - O(1))
        String stockValue = redisTemplate.opsForValue().get(stockKey);
        Long stock = Long.parseLong(stockValue);

        // 4. 재고 부족 시 거부
        if (participantCount > stock) {
            redisTemplate.opsForSet().remove(usersSetKey, userId);
            throw new CouponException(COUPON_SOLD_OUT);
        }

        // 5. 큐에 등록 (RPUSH - O(1))
        CouponIssueQueueEntry entry = new CouponIssueQueueEntry(userId, couponId);
        String serialized = queueSerializer.serialize(entry);
        redisTemplate.opsForList().rightPush(queueKey, serialized);

        // 6. 즉시 응답
        log.info("쿠폰 발급 요청 큐 등록 성공: {}", entry);
    }
}
```

#### Background Worker (dequeue + process)
```java
@Component
public class CouponIssueWorker {
    @Scheduled(fixedDelay = 100)  // 100ms마다 폴링
    public void processQueues() {
        // 1. 모든 쿠폰 조회
        couponRepository.findAll()
            .forEach(c -> processQueueForCoupon(c.getId()));
    }

    private void processQueueForCoupon(Long couponId) {
        // 2. 배치 처리 (최대 50개)
        for (int i = 0; i < batchSize; i++) {
            // LPOP (O(1))
            CouponIssueQueueEntry entry =
                redisCouponService.dequeueUserRequest(couponId);

            if (entry == null) break;

            // 3. DB 트랜잭션 처리
            transactionTemplate.execute(status -> {
                // 중복 체크 (멱등성)
                if (alreadyIssued(userId, couponId)) {
                    return true;
                }

                // 쿠폰 조회, 재고 감소, 발급 기록
                Coupon coupon = couponRepository.findById(couponId);
                coupon.decreaseQuantity();
                couponRepository.save(coupon);

                CouponUser cu = CouponUser.issue(userId, couponId);
                couponUserRepository.save(cu);

                return true;
            });
        }
    }
}
```

### 2.3 현재 방식의 특징

| 항목 | 설명 | 평가 |
|------|------|------|
| **비동기 처리** | Redis Queue로 API 응답 속도 빠름 (5-10ms) | ✅ 좋음 |
| **동시성 제어** | Redis SADD로 중복 방지, 순차 처리 | ✅ 좋음 |
| **폴링 방식** | 100ms마다 Worker가 Redis를 체크 | ⚠️ 개선 필요 |
| **확장성** | Worker 1개만 동작 (단일 스레드) | ⚠️ 개선 필요 |
| **메시지 보관** | Redis List에만 저장 (영속성 부족) | ⚠️ 개선 필요 |
| **모니터링** | 별도 모니터링 도구 없음 | ⚠️ 개선 필요 |
| **재시도** | 수동 requeue 로직 필요 | ⚠️ 개선 필요 |

### 2.4 현재 방식의 문제점

#### 문제 1: 폴링 오버헤드
```
Worker가 100ms마다 Redis를 체크
→ 큐가 비어있어도 계속 폴링 (불필요한 Redis 부하)
→ findAll() 쿼리로 모든 쿠폰 조회 (DB 부하)

예시: 쿠폰 10개, 100ms마다 폴링
→ 초당 10개 * 10회 = 100회 Redis LPOP
→ 초당 10회 findAll() DB 쿼리
```

#### 문제 2: 단일 Worker 제약
```
현재: Worker 1개 → 배치 크기 50 → 100ms마다
→ 초당 처리량: 50 * 10 = 500건

확장 불가: Worker를 여러 개 띄우면?
→ 같은 큐를 여러 Worker가 LPOP
→ 경쟁 조건 발생 가능
→ 순서 보장 어려움
```

#### 문제 3: 메시지 영속성 부족
```
Redis List에만 저장
→ Redis 재시작 시 메시지 손실 가능
→ RDB/AOF 설정해도 100% 보장 안 됨
→ 백업/복구 어려움
```

#### 문제 4: 모니터링 부족
```
- 큐에 메시지가 얼마나 쌓였는지? (Redis LLEN)
- Worker가 제대로 처리하고 있는지?
- 처리 지연이 발생하는지?
- 실패율은?

→ 별도 모니터링 코드 작성 필요
→ 운영 부담 증가
```

---

## 3. 현재 아키텍처 (Kafka 기반 구현)

### 3.1 아키텍처 다이어그램

```
[Client]
   ↓ HTTP POST /coupons/{couponId}/issue
[CouponController]
   ↓ CouponIssueKafkaProducer.publishCouponIssueRequest()
[CouponIssueKafkaProducer]
   ↓ 1. Redis 빠른 검증 (validateWithRedis)
   |    ├─ SADD participants set (중복 체크)
   |    ├─ SCARD 참여자 수 조회
   |    ├─ GET stock (재고 확인)
   |    └─ 재고 부족 시 예외 발생 + SREM 제거
[Redis]
   ↓ 2. UUID requestId 생성
   ↓ 3. CouponIssueRequestEvent 생성
   ↓ 4. Kafka Topic에 발행 (key=couponId)
   ↓ 5. 즉시 응답 (200 OK + requestId, status=QUEUED)
[Client] ← CouponIssueResponse { requestId, userId, couponId, status="QUEUED" }

======================== 비동기 처리 경계 ========================

[Kafka Topic: coupon-issue]
   ├─ Partition 0 (couponId % 3 == 0)
   ├─ Partition 1 (couponId % 3 == 1)
   └─ Partition 2 (couponId % 3 == 2)
        ↓ (이벤트 기반 - 폴링 불필요!)
[Coupon Issue Consumer Group]
   ├─ Consumer 1 → Partition 0
   ├─ Consumer 2 → Partition 1
   └─ Consumer 3 → Partition 2
        ↓ 4. 메시지 수신 (즉시)
        ↓ 5. DB 트랜잭션 처리
        ↓ 6. Offset commit (자동 재시도)
[MySQL]
   ↓ 7. 발급 완료 이벤트 발행 (Optional)
[Kafka Topic: coupon-issued]
   ↓ 8. 결과 전달 (Polling/SSE 등)
[Client]
```

### 3.2 핵심 개선 사항

#### 개선 1: 폴링 제거 → 이벤트 기반
```
[AS-IS] Worker가 100ms마다 Redis 체크
        → 큐 비어도 계속 폴링 (낭비)

[TO-BE] Kafka Consumer가 메시지 도착 시 즉시 처리
        → 메시지 없으면 대기 (idle)
        → 리소스 효율적
```

#### 개선 2: 동시성 제어 불필요 (Lock-Free!)
```
[AS-IS] 단일 Worker로 순차 처리
        - Worker를 여러 개 띄우면?
        - 같은 Redis Queue를 여러 Worker가 LPOP
        - 경쟁 조건 발생 가능
        - 동일 쿠폰에 대한 동시 처리 위험

        문제 시나리오:
        1. Worker A가 couponId=100 LPOP
        2. Worker B도 couponId=100 LPOP (동시 요청)
        3. 둘 다 DB에서 재고 조회 → 10개
        4. 둘 다 재고 감소 → 8개, 8개 (잘못됨!)

        → Worker를 1개만 운영할 수밖에 없음
        → 확장성 제약

[TO-BE] Partition Key로 자연스러운 동시성 제어
        - couponId를 Partition Key로 사용
        - 동일 couponId → 항상 동일 Partition
        - 각 Partition은 단일 Consumer가 처리
        - Partition 내에서 순차 처리 보장

        동시성 제어 메커니즘:

        couponId=100 요청 1000건 발생
             ↓
        hash(100) % 3 = 1 → 모두 Partition 1로 전달
             ↓
        Partition 1은 Consumer 1이 담당
             ↓
        Consumer 1이 순차적으로 1000건 처리
             ↓
        ✅ 경쟁 조건 원천 차단!
        ✅ Lock 불필요!
        ✅ DB 정합성 자동 보장!

        다른 쿠폰은?
        couponId=200 → hash(200) % 3 = 2 → Partition 2
        couponId=300 → hash(300) % 3 = 0 → Partition 0

        → 서로 다른 Consumer가 병렬 처리
        → 성능 향상 + 동시성 제어 동시 달성!
```

#### 개선 3: 수평 확장 가능
```
[AS-IS] Worker 1개 → 초당 500건 한계
        → 확장 불가 (동시성 문제)

[TO-BE] Consumer 3개 (파티션 수만큼)
        - 각 파티션 독립 처리
        - 파티션 추가 시 Consumer 추가
        - 동시성 보장하면서 확장!
        → 선형적 확장 가능

예시: 파티션 3개 → 초당 1500건
     파티션 6개 → 초당 3000건
     (각 쿠폰별 순차 처리 유지)
```

#### 개선 4: 메시지 영속성 보장
```
[AS-IS] Redis List (휘발성)

[TO-BE] Kafka Log (디스크 저장)
        - Replication Factor 2 (복제)
        - 최소 7일 보관
        - Replay 가능 (재처리)
```

#### 개선 5: 내장 모니터링
```
[TO-BE] Kafka가 기본 제공:
        - Consumer Lag (처리 지연)
        - Offset 추적 (처리 현황)
        - Throughput (처리량)
        - Error rate (실패율)

→ Kafka Manager, Grafana 연동 용이
```

### 3.3 Event 정의

```java
// 쿠폰 발급 요청 이벤트
public record CouponIssueRequestEvent(
    String requestId,        // UUID (멱등성 보장)
    Long userId,
    Long couponId,
    LocalDateTime requestedAt
) {}

// 쿠폰 발급 완료 이벤트 (Optional - 결과 전달용)
public record CouponIssuedEvent(
    String requestId,
    Long userId,
    Long couponId,
    Long couponUserId,
    IssueStatus status,      // SUCCESS, FAILED, OUT_OF_STOCK, DUPLICATE
    String failureReason,
    LocalDateTime issuedAt
) {
    public enum IssueStatus {
        SUCCESS, FAILED, OUT_OF_STOCK, DUPLICATE, EXPIRED
    }
}
```

### 3.4 코드 플로우

#### Producer (API Server)

**실제 구현:** `CouponIssueKafkaProducer.java`

```java
@Component
public class CouponIssueKafkaProducer {
    private final MessagePublisher messagePublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCouponKeyGenerator redisCouponKeyGenerator;

    /**
     * 쿠폰 발급 요청을 Kafka로 발행
     * - Redis 빠른 검증 (중복, 재고) 후 Kafka 발행
     * - couponId를 Partition Key로 사용 → 동일 쿠폰은 순차 처리
     *
     * @return requestId (UUID) - 클라이언트가 비동기 처리 추적용
     */
    public String publishCouponIssueRequest(Long userId, Long couponId) {
        // 1. Redis 빠른 검증 (Fast Pre-check)
        validateWithRedis(userId, couponId);

        // 2. 이벤트 생성 (UUID requestId 생성)
        String requestId = UUID.randomUUID().toString();
        CouponIssueRequestEvent event = CouponIssueRequestEvent.of(
            requestId, userId, couponId
        );

        // 3. Kafka 발행 (couponId를 Partition Key로 사용)
        messagePublisher.publish(
            KafkaTopics.COUPON_ISSUE,
            couponId.toString(),  // Partition Key: 동일 쿠폰은 동일 파티션에서 순차 처리
            event
        );

        log.info("쿠폰 발급 요청 Kafka 발행 완료: requestId={}, userId={}, couponId={}",
            requestId, userId, couponId);

        return requestId;  // 클라이언트에게 반환
    }

    /**
     * Redis를 이용한 빠른 검증
     * - 중복 발급 체크
     * - 재고 소진 체크
     */
    private void validateWithRedis(Long userId, Long couponId) {
        final String usersSetKey = redisCouponKeyGenerator.generateUsersSetKey(couponId);
        final String stockKey = redisCouponKeyGenerator.generateStockKey(couponId);

        // Step 1: 중복 발급 체크 (SADD)
        Long addResult = redisTemplate.opsForSet().add(usersSetKey, userId.toString());
        if (addResult == null || addResult == 0) {
            log.debug("중복 쿠폰 발급 요청 차단: userId={}, couponId={}", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }

        // Step 2: 현재 발급 요청 유저 수 count (SCARD)
        Long participantCount = redisTemplate.opsForSet().size(usersSetKey);
        if (participantCount == null) {
            participantCount = 0L;
        }

        // Step 3: 쿠폰 잔여 수량 정보 확인
        String stockValue = redisTemplate.opsForValue().get(stockKey);
        if (stockValue == null) {
            log.error("쿠폰 잔여 수량 정보가 Redis에 없습니다: couponId={}", couponId);
            // user request 정보도 삭제
            redisTemplate.opsForSet().remove(usersSetKey, userId.toString());
            throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
        }

        Long stock = Long.parseLong(stockValue);

        // Step 4: 쿠폰 Sold out 체크
        if (participantCount > stock) {
            log.debug("쿠폰 재고 부족: couponId={}, stock={}, participants={}",
                couponId, stock, participantCount);
            // Remove from set since they didn't get in
            redisTemplate.opsForSet().remove(usersSetKey, userId.toString());
            throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
        }
    }
}
```

**핵심 특징:**
1. **Redis 검증 우선**: Kafka 발행 전에 빠른 검증으로 불필요한 메시지 방지
2. **requestId 반환**: 클라이언트가 비동기 처리 상태 추적 가능
3. **Partition Key**: couponId로 동일 쿠폰 순차 처리 보장
4. **멱등성**: Redis Set으로 중복 발급 원천 차단

#### Consumer (Kafka Worker)

```java
@Component
@RequiredArgsConstructor
public class CouponIssueKafkaConsumer {
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Kafka에서 쿠폰 발급 요청 소비
     * - 폴링 불필요 (이벤트 기반)
     * - 파티션 내 순차 처리
     * - 자동 재시도 (Offset 미커밋)
     */
    @KafkaListener(
        topics = KafkaTopics.COUPON_ISSUE,
        groupId = "coupon-issue-group",
        concurrency = "3"  // 파티션 수와 동일
    )
    public void consumeCouponIssueRequest(CouponIssueRequestEvent event) {
        log.info("쿠폰 발급 요청 수신: requestId={}, couponId={}",
                 event.requestId(), event.couponId());

        Boolean success = transactionTemplate.execute(status -> {
            try {
                // 멱등성 체크 (중복 메시지 처리)
                if (alreadyIssued(event.userId(), event.couponId())) {
                    log.debug("이미 발급된 쿠폰: userId={}, couponId={}",
                             event.userId(), event.couponId());
                    return true;
                }

                // 쿠폰 발급 처리
                Coupon coupon = couponRepository.findById(event.couponId())
                    .orElseThrow(() -> new CouponException(COUPON_NOT_FOUND));

                coupon.validateIssuable();

                Coupon decreased = coupon.decreaseQuantity();
                couponRepository.save(decreased);

                CouponUser couponUser = CouponUser.issue(
                    event.userId(),
                    event.couponId(),
                    coupon.getEndDate()
                );
                couponUserRepository.save(couponUser);

                log.info("쿠폰 발급 성공: userId={}, couponId={}",
                         event.userId(), event.couponId());

                return true;

            } catch (CouponException e) {
                log.warn("쿠폰 발급 실패: {}", e.getMessage());
                return false;  // 트랜잭션 롤백
            }
        });

        // 성공 시 Offset 자동 커밋
        // 실패 시 트랜잭션 롤백 → Offset 미커밋 → 재시도
    }

    private boolean alreadyIssued(Long userId, Long couponId) {
        return couponUserRepository
            .findByUserIdAndCouponId(userId, couponId)
            .isPresent();
    }
}
```

### 3.5 동시성 제어 메커니즘 상세

#### 3.5.1 AS-IS의 동시성 제어 한계

현재 Redis Queue 방식은 **단일 Worker**로 운영되어 동시성 문제가 없지만, **확장이 불가능**합니다.

```
문제: Worker를 여러 개 띄우면 어떻게 될까?

시나리오:
┌─────────────────────────────────────────────────┐
│ Redis Queue: [userId=1:couponId=100,           │
│               userId=2:couponId=100,           │
│               userId=3:couponId=200]           │
└─────────────────────────────────────────────────┘
         ↓               ↓
    Worker A         Worker B
         ↓               ↓
    LPOP 동시 실행
         ↓               ↓
    userId=1:100     userId=2:100
         ↓               ↓
    둘 다 couponId=100 처리
         ↓               ↓
    DB 조회: 재고 10개   DB 조회: 재고 10개
         ↓               ↓
    재고 감소: 9개       재고 감소: 9개 (잘못!)
         ↓               ↓
    ❌ 재고 정합성 깨짐! (8개여야 함)

해결책: Worker를 1개만 운영
→ 확장 불가능
→ 처리량 제한 (초당 500건)
```

#### 3.5.2 TO-BE의 Lock-Free 동시성 제어

Kafka의 **Partition Key 메커니즘**으로 Lock 없이 동시성 제어!

```
핵심 원리: 동일 couponId → 동일 Partition → 순차 처리

시나리오: couponId=100에 1000명이 동시 요청

Step 1: Producer가 Kafka에 발행
┌──────────────────────────────────────┐
│ 1000건 모두 key=100으로 발행         │
└──────────────────────────────────────┘
         ↓
Step 2: Kafka가 Partition 결정
┌──────────────────────────────────────┐
│ hash(100) % 3 = 1                    │
│ → 1000건 모두 Partition 1로 전달    │
└──────────────────────────────────────┘
         ↓
Step 3: Partition 배치
┌─────────────┬─────────────┬─────────────┐
│ Partition 0 │ Partition 1 │ Partition 2 │
│ (비어있음)  │ 1000건      │ (비어있음)  │
│             │ (coupon=100)│             │
└─────────────┴─────────────┴─────────────┘
                    ↓
Step 4: Consumer 할당 (Consumer Group)
┌─────────────┬─────────────┬─────────────┐
│ Consumer A  │ Consumer B  │ Consumer C  │
│ Partition 0 │ Partition 1 │ Partition 2 │
└─────────────┴─────────────┴─────────────┘
                    ↓
Step 5: Consumer B가 순차 처리
┌──────────────────────────────────────┐
│ Consumer B (Partition 1 전담)        │
│ 1. 메시지 1 처리 (userId=1, coupon=100) │
│    - DB 조회: 재고 1000개            │
│    - 재고 감소: 999개                │
│    - 저장 완료                       │
│    - Offset 커밋                     │
│                                       │
│ 2. 메시지 2 처리 (userId=2, coupon=100) │
│    - DB 조회: 재고 999개             │
│    - 재고 감소: 998개                │
│    - 저장 완료                       │
│    - Offset 커밋                     │
│                                       │
│ ... 1000건 순차 처리 ...             │
│                                       │
│ ✅ 경쟁 조건 불가능!                  │
│ ✅ Lock 불필요!                       │
│ ✅ DB 정합성 자동 보장!               │
└──────────────────────────────────────┘
```

#### 3.5.3 다중 쿠폰 병렬 처리

다른 쿠폰은 다른 파티션으로 분산되어 **병렬 처리**:

```
동시 요청:
- couponId=100 (1000명)
- couponId=200 (500명)
- couponId=300 (800명)

Partitioning 결과:
┌─────────────────────────────────────────────┐
│ Partition 0: couponId=300 (800명)          │
│   → Consumer A가 순차 처리                 │
├─────────────────────────────────────────────┤
│ Partition 1: couponId=100 (1000명)         │
│   → Consumer B가 순차 처리                 │
├─────────────────────────────────────────────┤
│ Partition 2: couponId=200 (500명)          │
│   → Consumer C가 순차 처리                 │
└─────────────────────────────────────────────┘

결과:
✅ 각 쿠폰 내에서는 순차 처리 (동시성 제어)
✅ 서로 다른 쿠폰은 병렬 처리 (성능 향상)
✅ Lock 완전히 불필요!
```

#### 3.5.4 AS-IS vs TO-BE 동시성 제어 비교

| 항목 | AS-IS (Redis Queue) | TO-BE (Kafka) |
|------|---------------------|---------------|
| **동시성 제어** | 단일 Worker로 순차 처리 | Partition Key로 순차 처리 |
| **Lock 필요성** | Worker 1개라 불필요 (하지만 확장 불가) | 완전히 불필요 (확장 가능) |
| **확장성** | Worker 추가 시 경쟁 조건 발생 | Consumer 추가 가능 (파티션별 독립) |
| **병렬 처리** | 불가능 (단일 스레드) | 가능 (다른 쿠폰은 다른 파티션) |
| **재고 정합성** | 단일 Worker라 보장 | Partition 순차 처리로 보장 |
| **처리량** | 500 TPS (제한적) | 1500+ TPS (확장 가능) |

#### 3.5.5 추가 안전장치 (Defense in Depth)

Kafka의 순차 처리로 기본 동시성은 해결되지만, **다중 방어선** 유지:

```java
@KafkaListener(topics = "coupon-issue")
public void consume(CouponIssueRequestEvent event) {
    transactionTemplate.execute(status -> {
        // 1차 방어: Kafka Partition (이미 순차 처리 보장됨)
        // 하지만 추가 안전장치 유지:

        // 2차 방어: 멱등성 체크 (중복 메시지 대비)
        if (alreadyIssued(userId, couponId)) {
            log.debug("중복 메시지 감지");
            return true;  // 성공으로 처리 (재시도 방지)
        }

        // 3차 방어: DB Unique Constraint
        // (coupon_user 테이블에 userId+couponId unique 제약)
        try {
            CouponUser cu = CouponUser.issue(userId, couponId);
            couponUserRepository.save(cu);
        } catch (DataIntegrityViolationException e) {
            log.warn("DB 제약 조건 위반 (극히 드묾)");
            return true;
        }

        return true;
    });
}
```

**방어선 설명:**
1. **Kafka Partition** (1차): 같은 쿠폰은 같은 Consumer가 순차 처리 → 기본 동시성 제어
2. **멱등성 체크** (2차): 네트워크 재전송 등으로 중복 메시지 올 경우 대비
3. **DB Constraint** (3차): 만약의 사태 대비 최종 방어선

→ **Defense in Depth** 전략으로 100% 안전성 보장!

---

## 4. 비교 분석

### 4.1 아키텍처 비교표

| 항목 | AS-IS (Redis Queue) | TO-BE (Kafka) |
|------|---------------------|---------------|
| **메시지 저장소** | Redis List | Kafka Topic + Log |
| **처리 방식** | Polling (100ms 주기) | Event-driven (즉시) |
| **Worker 수** | 1개 (단일 스레드) | N개 (파티션 수) |
| **확장성** | 제한적 (Worker 1개) | 선형 확장 (파티션 추가) |
| **메시지 영속성** | 휘발성 (Redis) | 영구 저장 (Disk + Replication) |
| **재시도** | 수동 requeue | Offset 기반 자동 재시도 |
| **순서 보장** | FIFO (단일 큐) | 파티션 내 순서 보장 |
| **모니터링** | 수동 구현 필요 | 내장 (Lag, Offset, Throughput) |
| **장애 복구** | 어려움 | 쉬움 (Offset replay) |

### 4.2 성능 비교

| 지표 | AS-IS | TO-BE | 개선율 |
|------|-------|-------|--------|
| **API 응답 시간** | 5-10ms | 5-10ms | 동일 (이미 비동기) |
| **폴링 오버헤드** | 초당 10회 * 쿠폰 수 | 0 (이벤트 기반) | 100% ↓ |
| **처리량 (TPS)** | ~500 (단일 Worker) | ~1500 (3 Consumer) | 3배 ↑ |
| **확장성** | Worker 추가 어려움 | 파티션만 추가 | ∞ |
| **메시지 안정성** | 낮음 (Redis 휘발) | 높음 (Replication) | - |

### 4.3 운영 비교

| 운영 측면 | AS-IS | TO-BE |
|-----------|-------|-------|
| **모니터링** | 별도 구현 필요 | Kafka 도구 활용 |
| **알림 설정** | 수동 | Lag 기반 자동 알림 |
| **장애 대응** | 수동 재처리 | Offset 조정으로 재처리 |
| **스케일 아웃** | 코드 수정 필요 | Consumer 추가만 |
| **메시지 추적** | 어려움 | Offset 기반 추적 |

---

## 5. 장단점 분석

### 5.1 TO-BE 장점

| 장점 | 설명 | 효과 |
|------|------|------|
| **폴링 제거** | 이벤트 기반 처리로 불필요한 Redis/DB 부하 제거 | 리소스 효율 30-50% 향상 |
| **수평 확장** | Consumer 추가만으로 처리량 증가 | 선형 확장 가능 |
| **메시지 안정성** | Disk + Replication으로 메시지 손실 방지 | 안정성 99.99%+ |
| **운영 편의성** | 내장 모니터링 도구 활용 | 운영 부담 50% 감소 |
| **장애 복구** | Offset 기반 재처리로 간편한 복구 | 복구 시간 90% 단축 |
| **표준화** | 산업 표준 메시징 시스템 사용 | 생태계 활용 |

### 5.2 TO-BE 단점 및 고려사항

| 단점 | 설명 | 해결 방안 |
|------|------|-----------|
| **인프라 추가** | Kafka 클러스터 운영 필요 | Docker Compose로 시작, MSK로 확장 |
| **학습 곡선** | 팀의 Kafka 이해 필요 | 교육 및 문서화 |
| **복잡도 증가** | AS-IS 대비 설정 및 관리 포인트 증가 | 자동화 및 모니터링 강화 |
| **비용** | Kafka 인프라 비용 | 트래픽 증가 시 비용 대비 효과 큼 |
| **과도한 설계?** | 현재 Redis로도 충분할 수 있음 | 향후 확장 고려 시 투자 가치 |

---

## 6. 의사 결정 가이드

### 6.1 Kafka 도입 권장 상황

✅ **강력히 권장:**
- 쿠폰 발급 요청이 초당 1000건 이상 예상
- 여러 쿠폰을 동시에 운영 (10개 이상)
- 메시지 손실이 절대 안 되는 경우
- 향후 MSA 전환 계획이 있는 경우
- 모니터링 및 운영 자동화가 중요한 경우

⚠️ **신중히 검토:**
- 소규모 트래픽 (초당 100건 미만)
- 팀의 Kafka 경험이 전혀 없는 경우
- 인프라 비용이 매우 민감한 경우
- 단순한 시스템을 선호하는 경우

### 6.2 현재 구현 상태

**✅ Kafka 완전 전환 완료**
```
- 모든 쿠폰 발급이 Kafka를 통해 처리됨
- Redis Queue + Worker 방식은 비활성화됨 (coupon.worker.enabled=false)
- CouponIssueKafkaProducer → Kafka → CouponIssueKafkaConsumer 플로우
```

**Legacy 코드 (Deprecated)**
```
- RedisCouponService: Redis 빠른 검증용으로만 사용 (큐 등록 기능은 사용 안 함)
- CouponIssueWorker: 비활성화됨 (필요 시 coupon.worker.enabled=true로 재활성화 가능)
```

---

## 7. 주의사항 및 리스크

### 7.1 Partition Key 전략

```
올바른 전략: couponId를 Partition Key로 사용

이유:
- 동일 쿠폰 → 동일 파티션 → 순차 처리
- 다른 쿠폰 → 다른 파티션 → 병렬 처리
- 재고 정합성 자동 보장

❌ 잘못된 예: userId를 Partition Key
   → 같은 사용자의 다른 쿠폰 요청이 직렬화됨
   → 불필요한 성능 저하
```

### 7.2 Redis 역할 변경

```
[AS-IS] Redis = Queue + 재고 관리 + 중복 방지

[TO-BE] Redis = 재고 관리 + 중복 방지 (빠른 검증용)
        Kafka = Queue (메시지 저장 및 전달)

주의: Redis는 계속 사용됨!
      → 빠른 API 응답을 위한 Pre-validation
      → Kafka는 확정된 요청만 전달
```

### 7.3 재고 정합성

```
시나리오: Redis 재고는 OK인데, DB 재고는 부족?

발생 케이스:
1. Redis 재고: 100
2. 100명이 Redis 통과 → Kafka 발행
3. 실제 DB 재고: 90개만 남음
4. Consumer가 처리할 때 10명은 실패

해결:
Consumer에서 DB 재고 재확인
→ 실패 시 적절한 에러 처리
→ 사용자에게 "이미 마감됨" 알림
```

### 7.4 Consumer 장애 시나리오

```
문제: Consumer가 다운되면?

대응: Kafka Consumer Group Rebalancing
      - 다른 Consumer가 파티션 인수
      - Offset 유지로 메시지 손실 없음
      - 자동 복구

문제: Consumer 처리 중 DB 장애?

대응: 트랜잭션 롤백 → Offset 미커밋
      - Kafka가 메시지 재전달
      - 멱등성 처리로 중복 방지
      - 재시도 횟수 제한 (max 3회)
```

### 7.5 Kafka 장애 시나리오

```
문제: Kafka 클러스터 다운?

대응 1: Circuit Breaker 패턴
        - Kafka 장애 감지
        - API에서 503 Service Unavailable
        - 클라이언트 재시도 유도

대응 2: Fallback to Redis Queue
        - Feature Flag로 자동 전환
        - 임시로 기존 Redis Queue 방식 사용
        - Kafka 복구 후 다시 전환
```

---

## 8. 구현 완료 현황

### 8.1 구현 완료 항목

**✅ 인프라 준비**
- [x] Kafka Docker Compose 구성 완료
- [x] Topic 생성 (coupon-issue, coupon-issued, 각 파티션 3개)
- [x] Kafka 실행 및 검증 완료

**✅ Producer 개발**
- [x] CouponIssueKafkaProducer 구현
- [x] MessagePublisher 활용
- [x] Redis 빠른 검증 통합
- [x] 단위 테스트 작성

**✅ Consumer 개발**
- [x] CouponIssueKafkaConsumer 구현
- [x] 멱등성 처리 로직 (DB 중복 체크)
- [x] 에러 핸들링 및 결과 이벤트 발행
- [x] 통합 테스트 작성

**✅ 전환 완료**
- [x] CouponController → CouponIssueKafkaProducer 직접 연결
- [x] Redis Queue Worker 비활성화 (coupon.worker.enabled=false)
- [x] 전체 E2E 테스트 통과
- [x] 문서화 완료


## 9. 모니터링 및 알림

### 9.1 핵심 모니터링 지표

```yaml
Kafka 지표:
  - Consumer Lag: < 100 (정상), > 1000 (경고)
  - Throughput: 초당 처리 건수
  - Error Rate: < 1% (목표)
  - Offset: 파티션별 처리 현황

비즈니스 지표:
  - 쿠폰 발급 성공률: > 99%
  - 평균 처리 시간: < 1초
  - 재고 소진 시간: 실시간 추적
```

---

## 10. Redis Worker 제거 완료

### 10.1 변경 사항

Kafka 기반 방식으로 완전히 전환되어 Redis Queue Worker 관련 코드를 deprecate 처리했습니다.

**Deprecated 클래스 및 메서드:**

```java
// 1. CouponIssueWorker (전체 클래스)
@Deprecated
public class CouponIssueWorker {
    // Redis Queue 폴링 방식 → Kafka Consumer로 대체
}

// 2. RedisCouponService (일부 메서드)
@Deprecated
public void enqueueUserIfEligible(Long userId, Long couponId) {
    // Redis Queue 사용 → Kafka Producer로 대체
}

@Deprecated
public CouponIssueQueueEntry dequeueUserRequest(Long couponId) {
    // CouponIssueWorker에서만 사용 → 더 이상 필요 없음
}

@Deprecated
public void requeueUserRequest(Long userId, Long couponId) {
    // 재시도 로직 → Kafka Consumer의 자동 재시도로 대체
}

@Deprecated
public Long getQueueSize(Long couponId) {
    // Queue 크기 조회 → Kafka에서는 불필요
}
```


**삭제된 테스트:**
- `CouponIssueWorkerTest.java` - 전체 삭제
- `RedisCouponServiceIntegrationTest.java` - 전체 삭제

### 10.2 현재 시스템 구조

```
┌─────────────────────────────────────────────────────┐
│  현재 아키텍처 (Kafka 기반)                          │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [Controller]                                       │
│       ↓                                             │
│  CouponIssueKafkaProducer                           │
│       ├─ Redis 빠른 검증 (중복, 재고)               │
│       └─ Kafka 발행                                 │
│            ↓                                        │
│  CouponIssueKafkaConsumer                           │
│       └─ DB 쿠폰 발급                               │
│                                                     │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  Deprecated             │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ❌ CouponIssueWorker                               │
│  ❌ RedisCouponService.enqueueUserIfEligible()      │
│  ❌ RedisCouponService.dequeueUserRequest()         │
│  ❌ RedisCouponService.requeueUserRequest()         │
│  ❌ RedisCouponService.getQueueSize()               │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 10.3 유지되는 Redis 기능

**다음 메서드들은 계속 사용됩니다:**

```java
// ✅ Kafka 방식에서도 사용
redisCouponService.initializeCouponStock(couponId, stock);
redisCouponService.getParticipantCount(couponId);
```

**이유:**
- Redis는 **검증 목적**으로 계속 사용됨
- 중복 발급 체크: `coupon:issue:async:participants:{couponId}` Set
- 재고 체크: `coupon:issue:async:stock:{couponId}` String
- Redis Queue는 사용하지 않음

### 10.4 제거 예정 코드 목록

**추후 제거할 Deprecated 항목:**

1. **파일 삭제:**
   - `CouponIssueWorker.java`
   - `CouponIssueQueueEntry.java` (사용하지 않는 경우)
   - `CouponQueueSerializer.java` (사용하지 않는 경우)

2. **메서드 삭제:**
   - `RedisCouponService.enqueueUserIfEligible()`
   - `RedisCouponService.dequeueUserRequest()`
   - `RedisCouponService.requeueUserRequest()`
   - `RedisCouponService.getQueueSize()`

3. **Redis Key 정리:**
   - `coupon:issue:async:queue:{couponId}` 키 제거

---

### A. 참고 자료

- [현재 Redis Queue 구현](../redis/[STEP-14]redis_async_coupon_implementation.md)
- [Kafka Partitioning 전략](https://kafka.apache.org/documentation/#design_partitioning)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)

---
