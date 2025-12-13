package com.hh.ecom.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Outbox 이벤트 발행 전용 Executor
     * 동작 순서:
     * 1. core 스레드로 처리
     * 2. core 스레드가 모두 사용 중이면 큐에 대기
     * 3. 큐가 가득 차면 max까지 스레드 증가
     * 4. max 스레드도 모두 사용 중이고 큐도 가득 차면 호출 스레드에서 직접 실행 (백프레셔)
     */
    @Bean(name = "outboxEventExecutor")
    public Executor outboxEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        //  대기 큐 크기 (core 스레드가 모두 사용 중일 때 대기)
        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("outbox-event-");

        // 유휴 스레드 종료 시간 (초) - core 크기를 초과한 스레드만 해당
        executor.setKeepAliveSeconds(60);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Outbox Event Executor 초기화 완료: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
