# E-Commerce Platform

Spring Boot 기반 이커머스 플랫폼 프로젝트

## 목차

- [아키텍처 설계](#아키텍처-설계)
- [동시성 제어 분석](#동시성-제어-분석)

---

## 아키텍처 설계

### 레이어드 아키텍처 (4계층)

```
src/main/java/com/hh/ecom/
├── presentation/          # (Controller Layer)
│   └── {domain}/presentation/
│       ├── {Domain}Controller.java
│       └── dto/
│
├── application/           # (Service Layer)
│   └── {domain}/application/
│       └── {Domain}Service.java
│
├── domain/               # (Domain Layer)
│   └── {domain}/domain/
│       ├── {Domain}.java           # Rich Domain Model
│       ├── {Domain}Repository.java # Repository Interface
│       └── exception/
│
└── infrastructure/       # (Infrastructure Layer)
    └── {domain}/infrastructure/
        └── persistence/
            ├── {Domain}InMemoryRepository.java
            └── entity/
                └── {Domain}Entity.java
```

### 의존성 방향
```
Presentation → Application → Domain ← Infrastructure
```

- **Domain Layer**는 다른 계층에 의존하지 않음 (순수 비즈니스 로직)
- **Infrastructure Layer**는 Domain의 Repository 인터페이스를 구현
- **Application Layer**는 Domain의 Repository 인터페이스에만 의존

---

## 동시성 제어 분석

### 1. 동시성 문제가 발생하는 시나리오

이커머스 시스템에서는 다음과 같은 동시성 문제가 발생할 수 있습니다:

#### 시나리오 1: 선착순 쿠폰 발급
```
- 100명이 10개 쿠폰에 동시 접근
- Race Condition 발생 시: 10개 이상 발급 가능
- 예상 결과: 정확히 10명만 발급 성공
```

#### 시나리오 2: 포인트 충전/사용
```
- 동일 사용자가 동시에 여러 번 포인트 충전
- Race Condition 발생 시: 잔액 불일치
- 예상 결과: 모든 충전 금액이 정확히 반영
```

### 2. 선택한 동시성 제어 방식: 낙관적 락 (Optimistic Lock)

#### 2-1. 낙관적 락 개념

**낙관적 락(Optimistic Lock)**은 데이터 충돌이 드물게 발생한다고 가정하고, 실제 업데이트 시점에 충돌을 검증하는 방식입니다.

#### 핵심 메커니즘
```java
// 1. 데이터 조회 시 버전 정보 함께 조회
Coupon coupon = repository.findById(id);  // version: 0

// 2. 비즈니스 로직 수행
Coupon updated = coupon.decreaseQuantity();  // version: 1

// 3. 저장 시 버전 검증
repository.save(updated);  // 예상 버전(0)과 실제 버전 비교
```

#### 버전 충돌 감지
```java
// Repository에서 버전 검증
if (!existingEntity.getVersion().equals(domain.getVersion() - 1)) {
    throw new OptimisticLockException("버전 충돌 발생");
}
```

### 3. 쿠폰 발급 시스템의 동시성 제어

#### 3-1. CouponService 구현 전략

```java
@Service
public class CouponService {
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 50;

    @Transactional
    public CouponUser issueCoupon(Long userId, Long couponId) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                return tryIssueCoupon(userId, couponId);
            } catch (OptimisticLockException e) {
                retryCount++;

                if (retryCount >= MAX_RETRY_COUNT) {
                    throw new CouponException(COUPON_ISSUE_FAILED,
                        "동시 요청이 많아 쿠폰 발급에 실패했습니다.");
                }

                // Exponential Backoff (50ms, 100ms, 150ms)
                Thread.sleep(RETRY_DELAY_MS * retryCount);
            }
        }
    }
}
```

#### 3-2. 재시도 전략: Exponential Backoff

| 재시도 횟수 | 대기 시간 | 설명 |
|----------|---------|------|
| 1회 | 50ms | 짧은 충돌 대기 |
| 2회 | 100ms | 충돌 빈도 완화 |
| 3회 | 150ms | 최종 재시도 |
| 실패 | - | 사용자에게 에러 반환 |

**Exponential Backoff의 장점:**
- 동시 요청 시 충돌 재발 가능성 감소
- 시스템 부하 분산
- 공정한 기회 제공

#### 3-3. CouponInMemoryRepository의 버전 검증

```java
@Override
public Coupon save(Coupon coupon) {
    if (coupon.getId() != null) {
        CouponEntity existingEntity = coupons.get(coupon.getId());

        // 낙관적 락: 버전 충돌 검사
        if (!existingEntity.getVersion().equals(coupon.getVersion() - 1)) {
            throw new OptimisticLockException(
                String.format("버전 충돌: 예상=%d, 실제=%d",
                    coupon.getVersion() - 1, existingEntity.getVersion())
            );
        }
    }

    // 저장 및 버전 증가
    coupons.put(entity.getId(), entity);
    return entity.toDomain();
}
```

#### 3-4. 동시성 테스트 결과

##### 동시성 제어 적용 전
```
테스트: 50명이 10개 쿠폰 발급 시도
결과: 34명 발급 성공 (❌ 수량 초과 발급)
문제: Race Condition으로 인한 수량 불일치
```

##### 동시성 제어 적용 후
```
테스트: 50명이 10개 쿠폰 발급 시도
결과: 정확히 10명 발급 성공 (✅ 정상)
성공 이유: 낙관적 락으로 버전 충돌 감지 및 재시도
```

### 4. 포인트 시스템의 동시성 제어

#### 4-1. PointService 구현 전략

```java
@Service
public class PointService {
    private static final int POINT_CHARGE_MAX_RETRY_COUNT = 3;

    @Transactional
    public Point chargePoint(Long userId, BigDecimal amount) {
        int retryCount = 0;

        while (retryCount < POINT_CHARGE_MAX_RETRY_COUNT) {
            try {
                // 1. 포인트 조회
                Point point = pointRepository.findByUserId(userId)
                    .orElseGet(() -> Point.create(userId));

                // 2. 포인트 충전 (버전 증가)
                Point chargedPoint = point.charge(amount);

                // 3. 저장 (버전 검증)
                return pointRepository.save(chargedPoint);

            } catch (PointException e) {
                if (e.getErrorCode() == OPTIMISTIC_LOCK_FAILURE) {
                    retryCount++;
                    if (retryCount >= POINT_CHARGE_MAX_RETRY_COUNT) {
                        throw e;
                    }
                    Thread.sleep(50L * retryCount);
                } else {
                    throw e;
                }
            }
        }
    }
}
```

#### 4-2. PointInMemoryRepository의 버전 검증

```java
@Override
public Point save(Point point) {
    if (point.getId() != null) {
        PointEntity existingEntity = points.get(point.getId());

        // 낙관적 락: 버전 체크
        if (!existingEntity.getVersion().equals(point.getVersion() - 1)) {
            throw new PointException(OPTIMISTIC_LOCK_FAILURE);
        }
    }

    points.put(entity.getId(), entity);
    return entity.toDomain();
}
```

#### 4-3. 포인트 연산의 원자성 보장

```java
// Point 도메인에서 불변성과 버전 관리
public class Point {
    private final Long version;
    private final BigDecimal balance;

    public Point charge(BigDecimal amount) {
        // 새로운 객체 생성 (불변성 유지)
        return this.toBuilder()
            .balance(this.balance.add(amount))
            .version(this.version + 1)  // 버전 증가
            .build();
    }
}
```

### 5. 동시성 제어 방식 비교

| 방식 | 낙관적 락 (선택) | 비관적 락 | Mutex/Lock | Queue 기반 |
|-----|-------|----|----|------|
| **성능** |  우수 |  보통 |  낮음 |  좋음 |
| **충돌 시 처리** | 재시도 필요 | 대기 후 획득 | 대기 후 획득 | 순차 처리 |
| **구현 복잡도** | 보통 | 간단 | 복잡 | 매우 복잡 |
| **데드락 위험** | 없음 | 있음 | 있음 | 없음 |
| **읽기 성능** | 영향 없음 | 락 대기 | 락 대기 | 영향 없음 |
| **분산 환경** | 가능 | DB 종속 | 어려움 | 가능 |

### 6. 낙관적 락을 선택한 이유

#### 6-1. 장점

1. **높은 처리량 (High Throughput)**
   - 락을 획득하지 않아 읽기 작업에 영향 없음 (SELECT ... FOR UPDATE 같은 물리적 락을 걸지 않기 때문)
   - 동시 읽기 요청 처리 성능 우수

2. **데드락 불가능**
   - 락을 걸지 않으므로 데드락 발생 가능성 없음
   - 비관적 락은 실제 DB 락을 사용하므로 락 순서가 꼬이면 데드락이 발생 가능
   - 안정적인 시스템 운영

3. **분산 환경 적합**
   - DB 버전 필드만으로 동작
   - 별도의 분산 락 시스템 불필요 (Redis나 Zookeeper 등)

4. **확장성**
   - 서버 인스턴스 증가에도 동일한 동작
   - 수평 확장 용이

#### 6-2. 단점 및 해결책

| 단점 | 해결책 |
|-----|-------|
| 충돌 시 재시도 필요 | Exponential Backoff로 충돌 완화 |
| 높은 경쟁에서 재시도 증가 | 최대 재시도 3회로 제한 + 사용자 피드백 |
| 재시도 로직 복잡도 | 공통 재시도 로직 템플릿화 |

#### 6-3. 적합한 사용 케이스

**낙관적 락이 적합한 경우:**
- 읽기 작업이 많고 쓰기 작업이 상대적으로 적음
- 충돌 빈도가 낮음 (예: 쿠폰 10개, 사용자 50명)
- 높은 처리량이 중요함
- 분산 환경에서 동작해야 함

- **낙관적 락이 부적합한 경우:**
- 충돌 빈도가 매우 높음 (예: 쿠폰 1개, 사용자 10,000명)
- 재시도가 허용되지 않는 크리티컬한 작업
- 순차 처리가 필수인 경우

### 7. 동시성 테스트 전략

#### 7-1. 테스트 시나리오

| 테스트 | 목적 | 결과 |
|-------|------|------|
| **50명이 10개 쿠폰 발급** | 수량 제한 검증 | ✅ 정확히 10명 성공 |
| **동일 사용자 10번 중복 발급** | 중복 방지 검증 | ✅ 1개만 발급 |
| **100명이 1개 쿠폰 발급** | 극한 경쟁 상황 | ✅ 1명만 성공 |
| **순차 발급 후 동시 조회** | 데이터 일관성 | ✅ 모든 조회 결과 동일 |

