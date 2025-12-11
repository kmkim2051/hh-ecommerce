package com.hh.ecom.product.infrastructure.redis;

import com.hh.ecom.common.lock.RedisLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 애플리케이션 시작 시 Redis 판매 랭킹 초기화
 * - RedisSalesRankingRepository가 활성화된 경우에만 동작
 * - 분산 락을 사용하여 다중 인스턴스 환경에서도 단 한 번만 초기화
 * - 초기화 플래그로 중복 초기화 방지
 * - DB의 주문 데이터를 Redis로 동기화 (SET 방식, 멱등성 보장)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedisSalesRankingRepository.class)
public class SalesRankingInitializer implements ApplicationRunner {
    private final RedisSalesRankingRepository redisSalesRankingRepository;
    private final SalesRankingRedisRepository salesRankingRedisRepository;
    private final RedisLockExecutor redisLockExecutor;

    private static final String INITIALIZATION_LOCK_KEY = "lock:ranking:sales:initialization";
    private static final int LOCK_WAIT_TIME_MS = 30_000;  // 30초 대기
    private static final int LOCK_LEASE_TIME_MS = 60_000; // 60초 임대

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("=== Redis 판매 랭킹 초기화 프로세스 시작 ===");

            // 분산 락으로 초기화는 단 하나의 인스턴스만 실행
            redisLockExecutor.executeWithLock(
                    List.of(INITIALIZATION_LOCK_KEY),
                    this::initializeWithDuplicationCheck,
                    LOCK_WAIT_TIME_MS,
                    LOCK_LEASE_TIME_MS
            );

            log.info("=== Redis 판매 랭킹 초기화 프로세스 완료 ===");
        } catch (Exception e) {
            log.error("Redis 판매 랭킹 초기화 실패: {}", e.getMessage(), e);
            // 초기화 실패는 치명적이지 않음 (조회 시 DB 폴백 가능)
            log.warn("Redis 초기화 실패했지만 애플리케이션은 정상 동작합니다. 조회 시 DB 폴백이 사용됩니다.");
        }
    }

    /**
     * 중복 초기화 체크를 포함한 초기화 로직
     * - 이미 초기화되었으면 스킵
     * - 초기화 후 플래그 설정
     */
    private Void initializeWithDuplicationCheck() {
        // 초기화 완료 여부 확인
        if (salesRankingRedisRepository.isInitialized()) {
            log.info("이미 초기화됨, 스킵합니다.");
            return null;
        }

        log.info("초기화 시작 (SET 방식, 멱등성 보장)");
        redisSalesRankingRepository.initializeFromDatabase();

        // 초기화 완료 마킹
        salesRankingRedisRepository.markInitialized();
        log.info("초기화 완료 플래그 설정 완료");

        return null;
    }
}
