package com.hh.ecom.common.lock;

import com.hh.ecom.common.lock.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 기반 분산 락 실행기 (Reentrant + Spin Lock 방식)
 * 여러 개의 락 키를 동시에 획득하고, 비즈니스 로직 실행 후 자동으로 해제합니다.
 *
 * <p>특징:
 * <ul>
 *   <li>Reentrant 지원: 같은 스레드에서 동일 락을 여러 번 획득 가능</li>
 *   <li>데드락 방지: 정렬된 락 키 순서로 획득</li>
 *   <li>자동 해제: 예외 발생 시에도 finally 블록에서 락 해제</li>
 * </ul>
 *
 * 사용 예시:
 * <pre>
 * List<String> lockKeys = List.of("lock:product:1", "lock:product:2");
 * redisLockExecutor.executeWithLock(lockKeys, () -> {
 *     // 비즈니스 로직 구현
 *     return result;
 * });
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockExecutor {
    private static final long DEFAULT_WAIT_TIME_MS = 3000;
    private static final long DEFAULT_LEASE_TIME_MS = 5000;
    private static final long SPIN_LOCK_RETRY_INTERVAL_MS = 50;

    private final RedissonClient redissonClient;

    /**
     * 여러 락 키에 대해 분산 락을 획득하고 작업을 실행합니다.
     *
     * @param lockKeys 락 키 리스트 (정렬된 상태로 전달 권장)
     * @param action 실행할 작업
     * @return 작업 결과
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public <T> T executeWithLock(List<String> lockKeys, Supplier<T> action) {
        return executeWithLock(lockKeys, action, DEFAULT_WAIT_TIME_MS, DEFAULT_LEASE_TIME_MS);
    }

    /**
     * 여러 락 키에 대해 분산 락을 획득하고 작업을 실행합니다. (타임아웃 커스터마이징)
     *
     * @param lockKeys 락 키 리스트
     * @param action 실행할 작업
     * @param waitTime 락 획득 대기 시간 (밀리초)
     * @param leaseTime 락 자동 해제 시간 (밀리초)
     * @return 작업 결과
     */
    public <T> T executeWithLock(List<String> lockKeys, Supplier<T> action,
                                   long waitTime, long leaseTime) {
        if (lockKeys == null || lockKeys.isEmpty()) {
            log.warn("락 키가 비어있습니다. 락 없이 작업을 실행합니다.");
            return action.get();
        }

        List<RLock> acquiredLocks = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            for (String lockKey : lockKeys) {
                RLock lock = redissonClient.getLock(lockKey);

                boolean acquired = acquireLockWithSpinning(
                    lock,
                    lockKey,
                    waitTime,
                    leaseTime,
                    startTime
                );

                if (!acquired) {
                    log.error("락 획득 실패: key={}, timeout={}ms", lockKey, waitTime);
                    throw new LockAcquisitionException(
                        String.format("락 획득 실패: %s (timeout: %dms)", lockKey, waitTime)
                    );
                }

                acquiredLocks.add(lock);
                log.debug("락 획득 성공: key={}, acquired={}/{}",
                    lockKey, acquiredLocks.size(), lockKeys.size());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("모든 락 획득 완료: keys={}, elapsed={}ms", lockKeys.size(), elapsed);

            // 비즈니스 로직 실행
            return action.get();

        } catch (RuntimeException e) {
            log.debug("작업 실행 중 예외 발생: {}", e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("작업 실행 중 예외 발생: {}", e.getMessage(), e);
            throw new RuntimeException("분산 락 작업 실패", e);

        } finally {
            releaseLocks(acquiredLocks);
        }
    }

    /**
     * Reentrant + Spin Lock 방식으로 락 획득 시도
     *
     * <p>동작 방식:
     * 1. 현재 스레드가 이미 락을 보유한 경우 → 즉시 재진입 (Reentrant)
     * 2. 그렇지 않으면 Spin Lock으로 반복 시도
     *
     * <p>Reentrant 세부 사항:
     * - Redisson은 Thread ID 기반으로 재진입 판단 (UUID:threadId)
     * - 재진입 횟수를 Redis Hash에 count로 관리
     * - 재진입 시마다 TTL 갱신 (lease time 연장)
     */
    private boolean acquireLockWithSpinning(RLock lock, String lockKey,
                                            long waitTime, long leaseTime,
                                            long startTime) {
        // 1. Reentrant 체크: 이미 현재 스레드가 락을 보유한 경우
        if (lock.isHeldByCurrentThread()) {
            try {
                // 재진입 허용: count 증가 + TTL 갱신
                lock.lock(leaseTime, TimeUnit.MILLISECONDS);
                log.debug("락 재진입 성공 (Reentrant): key={}, holdCount={}",
                    lockKey, lock.getHoldCount());
                return true;
            } catch (Exception e) {
                log.error("락 재진입 실패: key={}, error={}", lockKey, e.getMessage());
                return false;
            }
        }

        // 2. Spin Lock: 새로운 락 획득 시도
        long deadline = startTime + waitTime;

        while (System.currentTimeMillis() < deadline) {
            try {
                boolean acquired = lock.tryLock(0, leaseTime, TimeUnit.MILLISECONDS);
                if (acquired) {
                    log.debug("락 획득 성공: key={}, holdCount={}", lockKey, lock.getHoldCount());
                    return true;
                }
                Thread.sleep(SPIN_LOCK_RETRY_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("락 획득 중 인터럽트 발생: key={}", lockKey);
                return false;
            }
        }

        return false;
    }

    private void releaseLocks(List<RLock> locks) {
        if (locks.isEmpty()) {
            return;
        }

        // 락을 획득한 역순으로 해제
        for (int i = locks.size() - 1; i >= 0; i--) {
            RLock lock = locks.get(i);
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    log.debug("락 해제 완료: {}", lock.getName());
                }
            } catch (Exception e) {
                log.error("락 해제 실패: {}, error={}", lock.getName(), e.getMessage());
            }
        }

        log.debug("모든 락 해제 완료: total={}", locks.size());
    }
}
