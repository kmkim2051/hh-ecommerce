📌 개선된 쿠폰시스템 패턴 정의

Controller → Kafka Producer (직접 호출)
↓
Kafka Topic 발행
↓
Kafka Consumer → 실제 처리

핵심:
- Spring Event 없이 Kafka 직접 사용
- @Async 제거
- Producer/Consumer 명확한 분리

🔄 주문 시스템 적용 시 변경 범위

1. 추가할 클래스 (CREATE)

✅ OrderCompletedKafkaProducer.java

위치: src/main/java/com/hh/ecom/order/infrastructure/kafka/OrderCompletedKafkaProducer.java

package com.hh.ecom.order.infrastructure.kafka;

import com.hh.ecom.order.domain.Order;
import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.outbox.domain.MessagePublisher;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
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

📝 선택사항: OrderCompletedKafkaConsumer.java (외부 시스템용)

위치: src/main/java/com/hh/ecom/order/infrastructure/kafka/OrderCompletedKafkaConsumer.java

package com.hh.ecom.order.infrastructure.kafka;

import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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

     // 외부 시스템 알림 처리
     // - 이메일 발송
     // - SMS 발송
     // - 배송 시스템 연동
     // - 재고 시스템 연동 등
}
}

2. 삭제할 클래스 (DELETE)

❌ OutboxEventListener.java

위치: src/main/java/com/hh/ecom/outbox/application/listener/OutboxEventListener.java

이유:
- Kafka 발행을 OrderCompletedKafkaProducer가 담당
- @Async 방식의 신뢰성 문제 해결
- 코드 단순화

3. 수정할 클래스 (MODIFY)

✏️ OrderCommandService.java

위치: src/main/java/com/hh/ecom/order/application/OrderCommandService.java

변경 전:
@Service
@RequiredArgsConstructor
public class OrderCommandService {
private final ApplicationEventPublisher eventPublisher;
// ...

      @Transactional
      private Order executeOrderCreation(...) {
          // ... 주문 처리 ...

          Order updatedOrder = orderRepository.save(paidOrder);
          cartService.completeOrderCheckout(userId, productIds);

          // 기존: Spring Event 발행만
          eventPublisher.publishEvent(OrderCompletedEvent.from(updatedOrder));

          return updatedOrder.setOrderItems(savedOrderItems);
      }
}

변경 후:
@Service
@RequiredArgsConstructor
public class OrderCommandService {
private final ApplicationEventPublisher eventPublisher;
private final OrderCompletedKafkaProducer orderCompletedKafkaProducer;  // ✅ 추가
// ...

      @Transactional
      private Order executeOrderCreation(...) {
          // ... 주문 처리 ...

          Order updatedOrder = orderRepository.save(paidOrder);
          cartService.completeOrderCheckout(userId, productIds);

          // ===== 변경: 두 가지 경로 =====

          // 1. Kafka 발행 (외부 시스템 알림)
          orderCompletedKafkaProducer.publishOrderCompletedEvent(updatedOrder);  // ✅ 추가

          // 2. Spring Event 발행 (내부 로직: SalesRanking)
          eventPublisher.publishEvent(OrderCompletedEvent.from(updatedOrder));  // ✅ 유지

          // ================================

          return updatedOrder.setOrderItems(savedOrderItems);
      }
}

4. 유지할 클래스 (NO CHANGE)

✅ SalesRankingEventListener.java

위치: src/main/java/com/hh/ecom/product/application/event/SalesRankingEventListener.java

변경 없음 - Spring Event로 계속 작동

✅ OrderCompletedEvent.java

위치: src/main/java/com/hh/ecom/order/domain/event/OrderCompletedEvent.java

변경 없음 - 동일한 이벤트 사용

✅ MessagePublisher interface & KafkaMessagePublisher

위치:
- src/main/java/com/hh/ecom/outbox/domain/MessagePublisher.java
- src/main/java/com/hh/ecom/outbox/infrastructure/kafka/KafkaMessagePublisher.java

변경 없음 - 재사용

✅ KafkaTopics.java

위치: src/main/java/com/hh/ecom/outbox/infrastructure/kafka/KafkaTopics.java

변경 없음 - ORDER_COMPLETED 토픽 이미 정의됨

5. 미들웨어 의존성 (DEPENDENCIES)

✅ build.gradle

변경 없음! - 이미 Kafka 의존성 있음

// 기존 의존성 그대로 사용
implementation 'org.springframework.kafka:spring-kafka'

✅ application.yml

변경 없음! - 기존 Kafka 설정 사용

spring:
kafka:
bootstrap-servers: localhost:9092
producer:
key-serializer: org.apache.kafka.common.serialization.StringSerializer
value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

6. 테스트 수정 (TEST)

✏️ 관련 테스트 파일

- OrderCommandServiceTest.java - Mock 추가 필요
- OrderControllerIntegrationTest.java - 검증 로직 수정 가능

📊 변경 범위 요약표

| 구분 | 파일명                      | 작업          | 위치                         |
  |------|-----------------------------|---------------|------------------------------|
| 추가 | OrderCompletedKafkaProducer | CREATE        | order/infrastructure/kafka/  |
| 추가 | OrderCompletedKafkaConsumer | CREATE (선택) | order/infrastructure/kafka/  |
| 삭제 | OutboxEventListener         | DELETE        | outbox/application/listener/ |
| 수정 | OrderCommandService         | MODIFY        | order/application/           |
| 유지 | SalesRankingEventListener   | NO CHANGE     | product/application/event/   |
| 유지 | OrderCompletedEvent         | NO CHANGE     | order/domain/event/          |
| 유지 | MessagePublisher            | NO CHANGE     | outbox/domain/               |
| 유지 | KafkaMessagePublisher       | NO CHANGE     | outbox/infrastructure/kafka/ |
| 유지 | KafkaTopics                 | NO CHANGE     | outbox/infrastructure/kafka/ |

🎯 변경 범위 정량화

클래스 추가: 1개 (Consumer는 선택)
클래스 삭제: 1개
클래스 수정: 1개
클래스 유지: 5개

의존성 변경: 0개 (기존 Kafka 사용)
설정 변경: 0개

🔍 쿠폰 vs 주문 비교

쿠폰 시스템 (현재)

CouponController
↓
CouponIssueKafkaProducer
├─ Redis 검증
└─ Kafka 발행
↓
CouponIssueKafkaConsumer
└─ DB 쿠폰 발급

주문 시스템 (TO-BE)

OrderCommandService
├─ OrderCompletedKafkaProducer → Kafka (외부)
└─ eventPublisher (내부: SalesRanking)
↓
[선택] OrderCompletedKafkaConsumer
└─ 외부 시스템 알림

차이점:
- 쿠폰: Consumer가 핵심 비즈니스 로직 (발급)
- 주문: Consumer는 부가 기능 (알림) - 선택사항

🚀 마이그레이션 단계

Step 1: OrderCompletedKafkaProducer 생성
Step 2: OrderCommandService에 Producer 추가
Step 3: 테스트 (병행 운영)
Step 4: OutboxEventListener 삭제
Step 5: [선택] Consumer 구현

총 작업 시간 예상: 1-2시간

이 정도 변경 범위라면 적용하시겠습니까?

> /usage
⎿  Status dialog dismissed

> 현재 claude pro 기준, daily term usage 55% 사용했어. 초과가능성 있나?

✻ Compacting conversation… (esc to interrupt)
