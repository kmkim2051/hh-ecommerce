package com.hh.ecom.outbox.infrastructure.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정
 * - 범용 메시지 발행을 위한 KafkaTemplate 구성
 * - 모든 메시지 타입을 Object로 처리하여 확장성 확보
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // JSON 직렬화 시 타입 정보 포함 (Consumer에서 역직렬화에 필요)
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        // 신뢰성 설정 (쿠폰 발급 등 중요한 비즈니스 메시지 유실 방지)
        config.put(ProducerConfig.ACKS_CONFIG, "all");                      // 모든 replica 확인 (Leader + Follower)
        config.put(ProducerConfig.RETRIES_CONFIG, 3);                       // 실패 시 최대 3회 재시도
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);         // 중복 메시지 방지 (정확히 1번 전송 보장)

        // 순서 보장 (idempotence 사용 시 필수)
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // 동시 요청 수 제한 (순서 보장)

        // 타임아웃 설정
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);      // 전송 타임아웃 2분
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);        // 요청 타임아웃 30초
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60000);              // send() 블로킹 최대 1분

        // 메모리 관리
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);          // 32MB 버퍼
        config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576);        // 최대 요청 1MB

        // 성능 최적화 (배치 처리)
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);                // 16KB 배치 사이즈
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);                    // 10ms 대기 후 전송 (배치 효율)
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");       // 압축으로 네트워크 대역폭 절약

        // 모니터링
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "ecom-producer");       // 클라이언트 식별자

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
