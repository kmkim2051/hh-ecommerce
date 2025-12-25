package com.hh.ecom.outbox.infrastructure.kafka;

import com.hh.ecom.coupon.domain.event.CouponIssueRequestEvent;
import com.hh.ecom.order.domain.event.OrderCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정
 * - 주문 완료 이벤트를 수신하기 위한 Consumer 설정
 * - ErrorHandler, Retry, Dead Letter Topic 포함
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, OrderCompletedEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "ecom-order-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON 역직렬화 설정
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCompletedEvent.class.getName());

        // Poll 설정 (처리량 최적화)
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);            // 한 번에 최대 500개 레코드
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);     // poll 간격 5분
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);               // 최소 fetch 크기
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);           // fetch 대기 시간 0.5초

        // 세션 관리 (Consumer 건강 체크)
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);        // 세션 타임아웃 10초
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);      // 하트비트 3초

        // 오프셋 관리 (데이터 유실 방지)
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);        // 수동 커밋 (명시적 ACK)
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");    // 처음부터 읽기

        // 성능 최적화
        config.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 52428800);        // 최대 fetch 50MB
        config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576); // 파티션당 1MB

        // 모니터링
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, "ecom-consumer");       // 클라이언트 식별자

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(OrderCompletedEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // 수동 커밋 (enable.auto.commit=false와 함께 사용)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ErrorHandler 설정 (재시도 + Dead Letter Topic)
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));

        return factory;
    }

    /**
     * ErrorHandler - 재시도 로직 + Dead Letter Topic
     * - 실패 시 3회 재시도 (지수 백오프: 1초, 2초, 4초)
     * - 최종 실패 시 DLT로 전송
     */
    private DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // 지수 백오프 재시도 (최대 3회, 초기 1초)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);      // 1초
        backOff.setMultiplier(2.0);             // 2배씩 증가
        backOff.setMaxInterval(10000L);         // 최대 10초

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // 최종 실패 시 Dead Letter Topic으로 전송
                    log.error("메시지 처리 최종 실패. DLT로 전송: topic={}, partition={}, offset={}, key={}, error={}",
                            consumerRecord.topic(),
                            consumerRecord.partition(),
                            consumerRecord.offset(),
                            consumerRecord.key(),
                            exception.getMessage());

                    // DLT 토픽으로 전송
                    String dltTopic = consumerRecord.topic() + ".DLT";
                    kafkaTemplate.send(dltTopic, (String) consumerRecord.key(), consumerRecord.value());
                },
                backOff
        );

        // 재시도 중 로그
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("메시지 처리 재시도 {}/3: topic={}, partition={}, offset={}, error={}",
                        deliveryAttempt,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        ex.getMessage())
        );

        return errorHandler;
    }

    // ============================================================
    // 쿠폰 발급 Consumer 설정
    // ============================================================

    @Bean
    public ConsumerFactory<String, CouponIssueRequestEvent> couponConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-issue-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON 역직렬화 설정
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponIssueRequestEvent.class.getName());

        // Poll 설정 (처리량 최적화)
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // 세션 관리
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        // 오프셋 관리
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 성능 최적화
        config.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 52428800);
        config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576);

        // 모니터링
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, "ecom-coupon-consumer");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(CouponIssueRequestEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequestEvent> couponKafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequestEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(couponConsumerFactory());

        // 수동 커밋 (메시지 처리 성공 시에만 커밋)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ErrorHandler 설정 (재시도 + DLT)
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));

        return factory;
    }
}
