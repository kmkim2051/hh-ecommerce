package com.hh.ecom.config;

import com.hh.ecom.outbox.domain.MessagePublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 Kafka 설정
 * - Kafka 서버 없이 테스트를 실행하기 위한 NoOp 구현체 제공
 */
@TestConfiguration
public class TestKafkaConfig {

    @Bean
    @Primary
    public MessagePublisher noOpMessagePublisher() {
        return (topic, key, message) -> {
            // 테스트 환경에서는 실제로 Kafka에 전송하지 않음 (NoOp)
        };
    }
}
