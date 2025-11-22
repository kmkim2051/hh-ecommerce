package com.hh.ecom.common.transaction;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 낙관적 락 충돌 시 재시도를 처리하는 유틸리티 클래스
 * 매 재시도마다 새로운 트랜잭션을 시작하여 트랜잭션 경계를 보장합니다.
 *
 * 사용 예시:
 * <pre>
 * public Product decreaseStock(Long productId, int quantity) {
 *     return retryExecutor.execute(() -> {
 *         Product product = productRepository.findById(productId).orElseThrow();
 *         product.decreaseStock(quantity);
 *         return productRepository.save(product);
 *     }, 5);
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptimisticLockRetryExecutor {
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final TransactionTemplate transactionTemplate;

    /**
     * 낙관적 락 충돌 시 지정된 횟수만큼 재시도합니다.
     * @param operation 실행할 작업
     * @param maxAttempts 최대 재시도 횟수
     * @return 작업 결과
     * @throws RuntimeException 최대 재시도 횟수 초과 시
     */
    public <T> T execute(Supplier<T> operation, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 매 시도마다 새로운 트랜잭션 시작. 실패 시 최대 횟수 까지 즉시 재시도
                return transactionTemplate.execute(status -> operation.get());
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                if (attempt >= maxAttempts) {
                    log.error("낙관적 락 재시도 실패 - 최대 시도 횟수 도달: attempts={}", attempt);
                    throw e;
                }
                log.debug("낙관적 락 충돌 감지 - 재시도: attempt={}/{}", attempt, maxAttempts);
            }
        }
        throw new IllegalStateException("Unreachable code");
    }

    public <T> T execute(Supplier<T> operation) {
        return execute(operation, DEFAULT_MAX_ATTEMPTS);
    }
}
