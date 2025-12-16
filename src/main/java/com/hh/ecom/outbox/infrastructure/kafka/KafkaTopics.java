package com.hh.ecom.outbox.infrastructure.kafka;

/**
 * Kafka Topic 중앙 관리
 * - 모든 Kafka Topic 이름을 상수로 관리
 * - Producer와 Consumer에서 동일한 Topic 이름 사용 보장
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDER_COMPLETED = "order-completed";
}
