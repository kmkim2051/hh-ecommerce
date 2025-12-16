package com.hh.ecom.outbox.infrastructure.kafka;

import com.hh.ecom.outbox.domain.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka를 사용한 메시지 발행 구현체
 * - 범용 메시지 발행을 Kafka Topic으로 전송
 * - 특정 도메인에 의존하지 않는 범용 Publisher
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(String topic, String key, Object message) {
        try {
            log.info("Kafka 메시지 발행 시작: topic={}, key={}, messageType={}",
                    topic, key, message.getClass().getSimpleName());

            kafkaTemplate.send(topic, key, message);

            log.info("Kafka 메시지 발행 완료: topic={}, key={}", topic, key);
        } catch (Exception e) {
            // 메시지 발행 실패는 로그만 남김 (트랜잭션은 이미 커밋된 상태)
            // 필요시 재시도 큐에 넣거나 별도 모니터링 가능
            log.error("Kafka 메시지 발행 실패: topic={}, key={}, error={}",
                    topic, key, e.getMessage(), e);
        }
    }
}
