요청하신 내용을 `.md` 파일에 바로 복사하여 사용하실 수 있도록 깔끔한 마크다운 포맷으로 변환해 드립니다.

---

# 📌 개선된 쿠폰시스템 패턴 정의

### 아키텍처 흐름

**Controller → Kafka Producer (직접 호출)** ↓

**Kafka Topic 발행** ↓

**Kafka Consumer → 실제 처리**

### 핵심 원칙

* **Spring Event 없이 Kafka 직접 사용**: 내부 이벤트 전파 단계를 줄여 직관성 향상
* **@Async 제거**: 비동기 처리의 불투명성 제거 및 Kafka를 통한 신뢰성 확보
* **Producer/Consumer 명확한 분리**: 송신자와 수신자의 책임 분리

---

# 🔄 주문 시스템 적용 시 변경 범위

## 1. 추가할 클래스

### ✅ OrderCompletedKafkaProducer.java

**위치**: `src/main/java/com/hh/ecom/order/infrastructure/kafka/OrderCompletedKafkaProducer.java`

```java
package com.hh.ecom.order.infrastructure.kafka;

public class OrderCompletedKafkaProducer {
    private final MessagePublisher messagePublisher;

    public void publishOrderCompletedEvent(Order order) {
        OrderCompletedEvent event = OrderCompletedEvent.from(order);

        messagePublisher.publish(
            KafkaTopics.ORDER_COMPLETED,
            order.getId().toString(),
            event
        );

        log.info("주문 완료 이벤트 Kafka 발행: orderId={}", order.getId());
    }
}

```

### 📝 선택사항: OrderCompletedKafkaConsumer.java (외부 시스템용)

**위치**: `src/main/java/com/hh/ecom/order/infrastructure/kafka/OrderCompletedKafkaConsumer.java`

```java

/**
 * 주문 완료 이벤트 Consumer (선택사항)
 * - 외부 시스템 알림, 이메일 발송 등
 * - 이 서비스 내에서 처리하지 않는다면 불필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedKafkaConsumer {

    @KafkaListener(
        topics = KafkaTopics.ORDER_COMPLETED,
        groupId = "order-notification-group"
    )
    public void consumeOrderCompletedEvent(OrderCompletedEvent event) {
        log.info("주문 완료 이벤트 수신: orderId={}", event.orderId());

        // 외부 시스템 알림 처리 예시
        // - 이메일 발송 / SMS 발송
        // - 배송 시스템 연동 / 재고 시스템 연동 등
    }
}

```

---

## 2. 삭제할 클래스 (DELETE)

### OutboxEventListener.java

**위치**: `src/main/java/com/hh/ecom/outbox/application/listener/OutboxEventListener.java`

* **이유**:
* Kafka 발행을 `OrderCompletedKafkaProducer`가 직접 담당함
* `@Async` 방식의 신뢰성 및 추적 문제 해결
* 중간 이벤트 리스너 제거를 통한 코드 단순화

---

## 3. 수정할 클래스 (MODIFY)

### ✏️ OrderCommandService.java

**위치**: `src/main/java/com/hh/ecom/order/application/OrderCommandService.java`

#### 변경 전

```java
@Transactional
private Order executeOrderCreation(...) {
    // ... 주문 처리 ...
    Order updatedOrder = orderRepository.save(paidOrder);
    cartService.completeOrderCheckout(userId, productIds);

    // 기존: Spring Event 발행만 (OutboxEventListener가 잡아서 Kafka로 전달했음)
    eventPublisher.publishEvent(OrderCompletedEvent.from(updatedOrder));

    return updatedOrder.setOrderItems(savedOrderItems);
}

```

#### 변경 후

```java
@Service
@RequiredArgsConstructor
public class OrderCommandService {
    private final ApplicationEventPublisher eventPublisher;
    private final OrderCompletedKafkaProducer orderCompletedKafkaProducer; // ✅ 주입 추가

    @Transactional
    private Order executeOrderCreation(...) {
        // ... 주문 처리 ...
        Order updatedOrder = orderRepository.save(paidOrder);
        cartService.completeOrderCheckout(userId, productIds);

        // ===== 변경: 목적에 따른 두 가지 경로 분리 =====

        // 1. Kafka 발행 (외부 시스템 알림 및 연동용)
        orderCompletedKafkaProducer.publishOrderCompletedEvent(updatedOrder); // ✅ 직접 호출

        // 2. Spring Event 발행 (내부 도직: SalesRanking 갱신 등)
        eventPublisher.publishEvent(OrderCompletedEvent.from(updatedOrder)); // ✅ 트랜잭션 내 처리를 위해 유지

        return updatedOrder.setOrderItems(savedOrderItems);
    }
}

```

---

## 4. 유지할 클래스 (NO CHANGE)

| 클래스명 | 위치 | 비고 |
| --- | --- | --- |
| **SalesRankingEventListener** | `.../product/application/event/` | 내부 로직이므로 Spring Event로 계속 작동 |
| **OrderCompletedEvent** | `.../order/domain/event/` | 동일한 이벤트 객체 재사용 |
| **MessagePublisher & KafkaMessagePublisher** | `.../outbox/domain/` & `.../infrastructure/kafka/` | 인프라 레이어 재사용 |
| **KafkaTopics** | `.../outbox/infrastructure/kafka/` | `ORDER_COMPLETED` 토픽 정의 유지 |

---

## 5. 의존성 및 설정 (INFRA)

* **build.gradle**: 변경 없음 (이미 `spring-kafka` 의존성 보유)
* **application.yml**: 변경 없음 (기존 Producer/Consumer 설정 사용)

---

## 변경 범위 요약표

| 구분 | 파일명 | 작업 | 위치 |
| --- | --- | --- | --- |
| **추가** | OrderCompletedKafkaProducer | **CREATE** | order/infrastructure/kafka/ |
| **추가** | OrderCompletedKafkaConsumer | **CREATE (선택)** | order/infrastructure/kafka/ |
| **삭제** | OutboxEventListener | **DELETE** | outbox/application/listener/ |
| **수정** | OrderCommandService | **MODIFY** | order/application/ |
| **유지** | SalesRankingEventListener | **NO CHANGE** | product/application/event/ |
| **유지** | OrderCompletedEvent | **NO CHANGE** | order/domain/event/ |

---

## 시스템 비교 (쿠폰 vs 주문)

1. **쿠폰 시스템 (현재)**
* `CouponController` → `Producer` → `Kafka` → `Consumer`
* *특징*: Consumer가 **핵심 비즈니스 로직(DB 발급)** 을 수행.


2. **주문 시스템 (TO-BE)**
* `OrderCommandService` → `Producer` → `Kafka` → `[Consumer]`
* *특징*: Consumer는 **부가 기능(알림, 외부 연동)** 을 수행. (도메인 로직은 Service에서 완결)

---

## 마이그레이션 단계

1. **Step 1**: `OrderCompletedKafkaProducer` 생성 및 검증
2. **Step 2**: `OrderCommandService`에 Producer 주입 및 호출 코드 추가
3. **Step 3**: 통합 테스트를 통한 Kafka 발행 여부 확인 (기존 Listener와 병행 운영 가능)
4. **Step 4**: 불필요해진 `OutboxEventListener` 삭제
5. **Step 5**: [선택] 외부 연동이 필요할 경우 `OrderCompletedKafkaConsumer` 구현
