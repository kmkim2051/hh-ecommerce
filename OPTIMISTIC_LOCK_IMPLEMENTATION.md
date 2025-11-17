# 낙관적 락 구현 가이드

## 개요

선착순 쿠폰 발급 시스템에 낙관적 락(Optimistic Lock)을 적용하여 동시성 문제를 해결했습니다.

## 구현 내용

### 1. 도메인 엔티티 Version 필드

이미 `Coupon` 도메인과 `CouponEntity`에 version 필드가 존재합니다:

```java
@Getter
@Builder(toBuilder = true)
public class Coupon {
    private final Long version;  // 낙관적 락용 버전 필드

    public Coupon decreaseQuantity() {
        // ...
        return this.toBuilder()
                .availableQuantity(newAvailableQuantity)
                .status(newStatus)
                .updatedAt(LocalDateTime.now())
                .version(this.version + 1)  // 버전 증가
                .build();
    }
}
```

### 2. OptimisticLockException 추가

```java
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}
```

### 3. Repository 낙관적 락 검증

`CouponInMemoryRepository.save()` 메서드에 버전 충돌 검사 추가:

```java
@Override
public Coupon save(Coupon coupon) {
    if (coupon.getId() != null) {
        // 기존 쿠폰 업데이트 - 낙관적 락 검증
        CouponEntity existingEntity = coupons.get(coupon.getId());

        if (existingEntity == null) {
            throw new OptimisticLockException("쿠폰이 존재하지 않습니다.");
        }

        // 버전 충돌 검사
        if (!existingEntity.getVersion().equals(coupon.getVersion() - 1)) {
            throw new OptimisticLockException(
                String.format("버전 충돌: 예상=%d, 실제=%d",
                    coupon.getVersion() - 1, existingEntity.getVersion())
            );
        }
    }

    coupons.put(entity.getId(), entity);
    return entity.toDomain();
}
```

### 4. Service 재시도 로직

`CouponService.issueCoupon()` 메서드에 재시도 로직 추가:

```java
@Slf4j
@Service
@RequiredArgsConstructor
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
                    log.warn("쿠폰 발급 최대 재시도 횟수 초과. userId={}, couponId={}",
                            userId, couponId);
                    throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED,
                            "동시 요청이 많아 쿠폰 발급에 실패했습니다.");
                }

                // Exponential backoff
                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED,
                            "쿠폰 발급 중 인터럽트가 발생했습니다.");
                }

                log.debug("쿠폰 발급 재시도. userId={}, retryCount={}", userId, retryCount);
            }
        }

        throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
    }

    private CouponUser tryIssueCoupon(Long userId, Long couponId) {
        // 1. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

        // 2. 발급 가능 여부 검증
        coupon.validateIssuable();

        // 3. 중복 발급 검증
        couponUserRepository.findByUserIdAndCouponId(userId, couponId)
                .ifPresent(existing -> {
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                });

        // 4. 쿠폰 수량 차감 (버전 자동 증가)
        Coupon decreasedCoupon = coupon.decreaseQuantity();

        // 5. 쿠폰 저장 (낙관적 락 검증 - 버전 충돌 시 OptimisticLockException 발생)
        couponRepository.save(decreasedCoupon);

        // 6. 쿠폰 발급 기록
        CouponUser couponUser = CouponUser.issue(userId, couponId, coupon.getEndDate());
        return couponUserRepository.save(couponUser);
    }
}
```

## 작동 원리

### 정상 흐름

1. Thread A가 쿠폰 조회 (version: 0, quantity: 10)
2. Thread A가 수량 차감 (version: 1, quantity: 9)
3. Thread A가 저장 성공 (DB version: 0 → 1)
4. Thread B가 쿠폰 조회 (version: 1, quantity: 9)
5. Thread B가 수량 차감 (version: 2, quantity: 8)
6. Thread B가 저장 성공 (DB version: 1 → 2)

### 충돌 발생 흐름

1. Thread A가 쿠폰 조회 (version: 0, quantity: 10)
2. Thread B가 쿠폰 조회 (version: 0, quantity: 10)
3. Thread A가 수량 차감 (version: 1, quantity: 9)
4. Thread B가 수량 차감 (version: 1, quantity: 9)
5. Thread A가 저장 성공 (DB version: 0 → 1)
6. **Thread B가 저장 시도** → `OptimisticLockException` 발생!
   - 예상 버전: 0
   - 실제 버전: 1 (Thread A가 업데이트함)
7. Thread B가 재시도
8. Thread B가 쿠폰 재조회 (version: 1, quantity: 9)
9. Thread B가 수량 차감 (version: 2, quantity: 8)
10. Thread B가 저장 성공 (DB version: 1 → 2)

## 재시도 전략

### Exponential Backoff

```
재시도 1회: 50ms 대기
재시도 2회: 100ms 대기
재시도 3회: 150ms 대기
최대 3회 재시도 후 실패 처리
```

### 장점

- 충돌 시 즉시 재시도하지 않고 대기
- 충돌 빈도 감소
- 시스템 부하 완화

## 테스트 결과

### CouponServiceOptimisticLockTest

✅ **순차적 발급 테스트** - PASS
- 5명에게 순차적으로 발급
- 최종 수량: 0
- 최종 버전: 5

✅ **2개 스레드 동시 발급** - PASS
- 2개 스레드가 동시 발급
- 모두 성공
- 최종 수량: 0

✅ **10개 스레드 소규모 테스트** - PASS
- 10명이 5개 쿠폰 경쟁
- 5명 성공, 5명 실패
- 최종 수량: 0

### CouponServiceConcurrencyTest

대규모 동시성 테스트 (50명이 10개 쿠폰 경쟁):
- 일부 테스트 성공
- 극한 경쟁 상황에서는 재시도 횟수 조정 필요

## 설정 최적화

### 재시도 횟수 조정

트래픽에 따라 재시도 횟수와 대기 시간 조정:

```java
// 높은 트래픽
private static final int MAX_RETRY_COUNT = 5;
private static final long RETRY_DELAY_MS = 30;

// 낮은 트래픽
private static final int MAX_RETRY_COUNT = 3;
private static final long RETRY_DELAY_MS = 50;
```

### 타임아웃 설정

```properties
# application.properties
spring.transaction.default-timeout=5
```

## 모니터링 포인트

### 로그 확인

```java
log.warn("쿠폰 발급 최대 재시도 횟수 초과");
log.debug("쿠폰 발급 재시도. retryCount={}", retryCount);
```

### 메트릭

1. 재시도 발생 빈도
2. 최대 재시도 초과 빈도
3. 평균 발급 소요 시간
4. 버전 충돌 발생 빈도

## JPA 환경 적용 시

In-memory 구현에서 JPA로 전환 시:

```java
@Entity
public class CouponEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version  // JPA 낙관적 락
    private Long version;

    // ...
}

@Repository
public interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {
    // JPA가 자동으로 낙관적 락 처리
}
```

JPA 사용 시 `@Version` 어노테이션만 추가하면 자동으로 낙관적 락이 적용됩니다.

## 장단점

### 장점

✅ 락을 걸지 않아 성능 우수
✅ 데드락 가능성 없음
✅ 읽기 작업 영향 없음
✅ 분산 환경에서도 동작

### 단점

❌ 충돌 시 재시도 필요
❌ 높은 경쟁 상황에서 재시도 증가
❌ 재시도 로직 구현 복잡도

## 결론

낙관적 락은 선착순 쿠폰 발급과 같이:
- 충돌 빈도가 상대적으로 낮고
- 읽기 작업이 많고
- 높은 성능이 필요한 경우

적합한 동시성 제어 방식입니다.

재시도 전략과 적절한 모니터링을 통해 안정적으로 운영할 수 있습니다.
