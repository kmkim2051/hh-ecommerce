# Redis 기반 비동기 쿠폰 발급 구현

## 개요
DB 기반 동기 방식에서 Redis 기반 비동기 방식으로 쿠폰 발급 시스템을 리팩토링
Set + List를 활용한 FCFS(선착순) 큐 처리 방식을 사용합니다.

## 아키텍처 변경사항

### 1. Redis 키 구조

세 가지 유형의 Redis 키를 사용 (네임스페이스: `coupon:issue:async`):

1. **String 키**: `coupon:issue:async:stock:{couponId}`
   - 전체 쿠폰 잔여 수량 저장
   - 예시: coupon'1'의 잔여 수량 = `coupon:issue:async:stock:1` → "100"

2. **Set 키**: `coupon:issue:async:participants:{couponId}`
   - 중복 요청 방지 (SADD가 중복 시 0 반환)
   - 참여자 수 추적 (SCARD)
   - 예시: coupon'1'의 발급 요청자 `coupon:issue:async:participants:1` → {userId1, userId2, ...}

3. **List 키**: `coupon:issue:async:queue:{couponId}`
   - 백그라운드 처리를 위한 FIFO 큐
   - 형식: "userId:couponId"
   - 예시: `coupon:issue:async:queue:1` → ["userId1:1", "userId2:1", ...]

**네임스페이스 설계 이유:**
- `coupon:issue:async` 접두사로 비동기 발급 전용임을 명시
- 다른 쿠폰 관련 Redis 키(캐시, 락 등)와 명확히 구분
- 기존 분산락 키 `lock:*` 패턴과 일관성 유지

### 2. 신규 컴포넌트

#### RedisCouponService
- **주요 메서드**:
  - `enqueueUserIfEligible()`: 메인 비동기 플로우 - 자격 검증 및 큐 등록
  - `dequeueUserRequest()`: 워커를 위해 큐에서 요청 꺼내기
  - `initializeCouponStock()`: 시작 시 Redis 재고 초기화
  - `requeueUserRequest()`: 실패한 요청 재등록

#### CouponRedisInitializer
- 위치: `src/main/java/com/hh/ecom/coupon/infrastructure/redis/CouponRedisInitializer.java`
- `ApplicationRunner` 구현하여 시작 시 실행
- DB에서 모든 쿠폰 로드 후 Redis 재고 초기화
- 멱등성 보장 (setIfAbsent 사용)

#### CouponIssueWorker
- 위치: `src/main/java/com/hh/ecom/coupon/application/CouponIssueWorker.java`
- 100ms마다 실행되는 백그라운드 스케줄러
- 배치당 최대 50개 요청 처리 (설정 가능)
- 옵션으로 재시도 가능한 우아한 실패 처리
- 중복 체크를 통한 멱등성 보장

## 플로우 비교

### 기존 DB 기반 동기 방식
```
사용자 요청 → Controller → Service (락 사용) → DB 트랜잭션 → 응답
- DB 트랜잭션이 완료될 때까지 블로킹
- 경쟁 상태 방지를 위해 분산 락 사용
- MySQL에서 직접 재고 차감
```

### 새로운 Redis 기반 비동기 방식
```
사용자 요청 → Controller → RedisCouponService → Redis 연산 → 즉시 응답
                                                      ↓
                                                   큐 등록
                                                      ↓
백그라운드 워커 (100ms 간격) → 큐에서 Pop → DB 트랜잭션
```

#### API 플로우 단계:
1. **SADD**로 users set에 추가 → 중복이면 0 반환
2. **SCARD**로 참여자 수 조회
3. **GET**으로 Redis에서 재고 조회
4. 참여자 vs 재고 비교 → 품절이면 거부
5. **RPUSH**로 큐에 등록
6. 즉시 "QUEUED" 응답 반환

#### 워커 플로우 단계:
1. 100ms마다 큐에서 **LPOP**
2. DB에서 중복 체크 (멱등성)
3. 쿠폰 로드 및 검증
4. DB에서 재고 차감
5. CouponUser 레코드 삽입
6. 실패 시 선택적으로 재큐잉

## 장점

1. **성능**: 락 경합 없음, 사용자에게 즉각 응답
2. **확장성**: DB 락보다 높은 동시성을 Redis가 더 잘 처리
3. **사용자 경험**: 기다림 없이 즉각적인 피드백
4. **신뢰성**: 재시도 기능을 갖춘 큐 기반 처리
5. **관심사의 분리**: API 레이어와 영속성 레이어 분리

## 에러 처리

### 큐 등록 시점 (API 레이어):
- **COUPON_ALREADY_ISSUED**: 사용자가 이미 set에 존재 (SADD가 0 반환)
- **COUPON_SOLD_OUT**: 참여자 수가 재고 초과
- **COUPON_NOT_FOUND**: Redis에 재고 키가 없음

### 처리 시점 (워커):
- **중복 감지**: DB 삽입 전 체크
- **재고 검증**: 초과 발급 방지를 위한 도메인 로직 사용
- **트랜잭션 롤백**: 실패한 트랜잭션이 데이터 손상시키지 않음
- **선택적 재시도**: 실패 시 재큐잉 설정 가능


## 하위 호환성

기존 동기 방식인 `CouponCommandService.issueCoupon()` 메서드는:
- `@Deprecated`로 표시됨
- 기존 코드에서는 여전히 작동
- API 엔드포인트에서는 더 이상 사용하지 않음
- 향후 릴리스에서 제거 예정

## 향후 개선사항

1. **Redis 키 TTL**: 오래된 큐 항목 자동 만료
2. **Dead Letter Queue**: 영구 실패 요청 처리
3. **모니터링**: 큐 깊이 및 처리율 메트릭 추가
4. **Rate Limiting**: 동일 사용자의 스팸 요청 방지

## 성능 예상치

### 이전 (DB 기반):
- 응답 시간: 50-200ms (락 경합 포함)
- 처리량: ~100 요청/초
- 실패 모드: 락 타임아웃 에러

### 이후 (Redis 기반):
- 응답 시간: <10ms (Redis 연산만)
- 처리량: >1000 요청/초
- 실패 모드: 우아한 큐 처리, 사용자 대면 에러 없음

## 참고사항

- 모든 Redis 연산은 단순한 `RedisTemplate` 명령어 사용 (Lua 스크립트 없음)
- 새로운 플로우에서는 분산 락 불필요
- 워커는 `@Scheduled`로 스케줄링 (`@EnableScheduling` 필요)
- 애플리케이션 시작 시 Redis 재고 자동 초기화
- 큐 처리는 최종 일관성 (실시간 DB 업데이트 아님)
