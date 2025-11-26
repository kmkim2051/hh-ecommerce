package com.hh.ecom.common.lock;

import com.hh.ecom.common.lock.exception.LockAcquisitionException;
import com.hh.ecom.config.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("RedisLockExecutor 통합 테스트")
class RedisLockExecutorTest extends TestContainersConfig {

    @Autowired
    private RedisLockExecutor redisLockExecutor;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("단일 락 키로 작업 실행 - 성공")
    void executeWithLock_SingleKey_Success() {
        // given
        String lockKey = "lock:test:single";
        String expectedResult = "success";

        // when
        String result = redisLockExecutor.executeWithLock(
            List.of(lockKey),
            () -> expectedResult
        );

        // then
        assertThat(result).isEqualTo(expectedResult);

        // 락이 해제되었는지 확인
        RLock lock = redissonClient.getLock(lockKey);
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    @DisplayName("여러 락 키로 작업 실행 - 성공")
    void executeWithLock_MultipleKeys_Success() {
        // given
        List<String> lockKeys = List.of(
            "lock:test:multi:1",
            "lock:test:multi:2",
            "lock:test:multi:3"
        );
        int expectedResult = 42;

        // when
        Integer result = redisLockExecutor.executeWithLock(
            lockKeys,
            () -> expectedResult
        );

        // then
        assertThat(result).isEqualTo(expectedResult);

        // 모든 락이 해제되었는지 확인
        lockKeys.forEach(key -> {
            RLock lock = redissonClient.getLock(key);
            assertThat(lock.isLocked()).isFalse();
        });
    }

    @Test
    @DisplayName("동일 락 키에 대한 동시 접근 - 순차 실행 보장")
    void executeWithLock_ConcurrentAccess_SerialExecution() throws InterruptedException {
        // given
        String lockKey = "lock:test:concurrent";
        int concurrentCount = 10;
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        // when - 10개 스레드가 동시에 같은 락 키로 작업 실행
        IntStream.range(0, concurrentCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));

                        // 작업 수행 (100ms 대기로 시뮬레이션)
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        counter.incrementAndGet();

                        currentConcurrent.decrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then - 모든 작업이 완료되고, 동시에 실행된 작업은 최대 1개
        assertThat(counter.get()).isEqualTo(concurrentCount);
        assertThat(maxConcurrent.get()).isEqualTo(1); // 분산락으로 순차 실행 보장
    }

    @Test
    @DisplayName("빈 락 키 리스트 - 락 없이 실행")
    void executeWithLock_EmptyKeys_ExecuteWithoutLock() {
        // given
        List<String> emptyKeys = List.of();
        String expectedResult = "no lock";

        // when
        String result = redisLockExecutor.executeWithLock(
            emptyKeys,
            () -> expectedResult
        );

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("null 락 키 리스트 - 락 없이 실행")
    void executeWithLock_NullKeys_ExecuteWithoutLock() {
        // given
        String expectedResult = "no lock";

        // when
        String result = redisLockExecutor.executeWithLock(
            null,
            () -> expectedResult
        );

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("작업 실행 중 예외 발생 - 락 정상 해제")
    void executeWithLock_ExceptionInAction_ReleaseLock() {
        // given
        String lockKey = "lock:test:exception";
        String errorMessage = "Test exception";

        // when & then
        assertThatThrownBy(() ->
            redisLockExecutor.executeWithLock(
                List.of(lockKey),
                () -> {
                    throw new RuntimeException(errorMessage);
                }
            )
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining(errorMessage);

        // 락이 해제되었는지 확인
        RLock lock = redissonClient.getLock(lockKey);
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    @DisplayName("여러 락 키 순차 획득 - 데드락 방지 검증")
    void executeWithLock_MultipleKeys_DeadlockPrevention() throws InterruptedException {
        // given
        List<String> lockKeys1 = List.of("lock:a", "lock:b", "lock:c");
        List<String> lockKeys2 = List.of("lock:a", "lock:b", "lock:c"); // 동일한 순서

        int concurrentCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 여러 스레드가 동일한 락 키 조합으로 작업 실행
        IntStream.range(0, concurrentCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    List<String> keys = i % 2 == 0 ? lockKeys1 : lockKeys2;
                    redisLockExecutor.executeWithLock(keys, () -> {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        successCount.incrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then - 모든 작업이 데드락 없이 완료
        assertThat(successCount.get()).isEqualTo(concurrentCount);

        // 모든 락이 해제되었는지 확인
        lockKeys1.forEach(key -> {
            RLock lock = redissonClient.getLock(key);
            assertThat(lock.isLocked()).isFalse();
        });
    }

    @Test
    @DisplayName("커스텀 타임아웃 설정 - 정상 동작")
    void executeWithLock_CustomTimeout_Success() {
        // given
        String lockKey = "lock:test:custom-timeout";
        long waitTime = 5000; // 5초
        long leaseTime = 10000; // 10초
        String expectedResult = "custom timeout";

        // when
        String result = redisLockExecutor.executeWithLock(
            List.of(lockKey),
            () -> expectedResult,
            waitTime,
            leaseTime
        );

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("락 획득 실패 - LockAcquisitionException 발생")
    void executeWithLock_LockAcquisitionFailure_ThrowsException() throws InterruptedException {
        // given
        String lockKey = "lock:test:acquisition-failure";
        long shortWaitTime = 50;

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch testComplete = new CountDownLatch(1);

        // 별도의 스레드에서 락을 획득하고 유지
        Thread lockHolder = new Thread(() -> {
            RLock preLock = redissonClient.getLock(lockKey);
            preLock.lock(30, TimeUnit.SECONDS);
            lockAcquired.countDown(); // 락 획득 완료 신호

            try {
                testComplete.await(); // 테스트 완료까지 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (preLock.isHeldByCurrentThread()) {
                    preLock.unlock();
                }
            }
        });

        lockHolder.start();
        lockAcquired.await(); // 락이 획득될 때까지 대기
        Thread.sleep(100); // 확실하게 락이 잡힌 후 진행

        try {
            // when & then - 다른 스레드가 락을 잡고 있는 상태에서 짧은 타임아웃으로 획득 시도
            assertThatThrownBy(() ->
                redisLockExecutor.executeWithLock(
                    List.of(lockKey),
                    () -> "should not execute",
                    shortWaitTime,
                    5000
                )
            ).isInstanceOf(LockAcquisitionException.class)
             .hasMessageContaining("락 획득 실패");

        } finally {
            testComplete.countDown(); // 락 해제 신호
            lockHolder.join(5000); // 스레드 종료 대기
        }
    }

    @Test
    @DisplayName("동시에 다른 락 키 작업 - 병렬 실행 가능")
    void executeWithLock_DifferentKeys_ParallelExecution() throws InterruptedException {
        // given
        int taskCount = 5;
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger currentConcurrent = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch latch = new CountDownLatch(taskCount);

        // when - 각각 다른 락 키로 동시 작업 실행
        IntStream.range(0, taskCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    String lockKey = "lock:test:parallel:" + i;
                    redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                        int current = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        currentConcurrent.decrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then - 여러 작업이 동시에 실행될 수 있음 (다른 락 키이므로)
        assertThat(maxConcurrent.get()).isGreaterThan(1);
    }
}
