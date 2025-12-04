package com.hh.ecom.product.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 Redis 판매 랭킹 초기화
 * - RedisSalesRankingRepository가 활성화된 경우에만 동작
 * - DB의 COMPLETED 주문 데이터를 Redis로 동기화
 * - 전체 기간 + 최근 30일 데이터 초기화
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedisSalesRankingRepository.class)
public class SalesRankingInitializer implements ApplicationRunner {
    private final RedisSalesRankingRepository redisSalesRankingRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("=== Redis 판매 랭킹 초기화 시작 ===");
            redisSalesRankingRepository.initializeFromDatabase();
            log.info("=== Redis 판매 랭킹 초기화 완료 ===");
        } catch (Exception e) {
            log.error("Redis 판매 랭킹 초기화 실패: {}", e.getMessage(), e);
            // 초기화 실패는 치명적이지 않음 (조회 시 DB 폴백 가능)
            log.warn("Redis 초기화 실패했지만 애플리케이션은 정상 동작합니다. 조회 시 DB 폴백이 사용됩니다.");
        }
    }
}
