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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("RedisLockExecutor Reentrant 기능 테스트")
class RedisLockExecutorReentrantTest extends TestContainersConfig {

    @Autowired
    private RedisLockExecutor redisLockExecutor;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("같은 스레드에서 동일한 락을 재진입할 수 있다")
    void shouldAllowReentrantLockOnSameThread() {
        // given
        String lockKey = "lock:test:reentrant:1";
        AtomicInteger executionCount = new AtomicInteger(0);

        // when
        String result = redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
            executionCount.incrementAndGet();

            // 같은 스레드에서 동일한 락을 다시 획득 시도 (재진입)
            return redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                executionCount.incrementAndGet();
                return "reentrant-success";
            });
        });

        // then
        assertThat(result).isEqualTo("reentrant-success");
        assertThat(executionCount.get()).isEqualTo(2); // 두 번 모두 실행됨
    }

    @Test
    @DisplayName("Reentrant Lock의 holdCount가 올바르게 관리된다")
    void shouldManageHoldCountCorrectly() {
        // given
        String lockKey = "lock:test:holdcount:1";
        RLock lock = redissonClient.getLock(lockKey);

        // when & then
        redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
            // 첫 번째 획득
            assertThat(lock.isHeldByCurrentThread()).isTrue();
            assertThat(lock.getHoldCount()).isEqualTo(1);

            // 재진입
            redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                assertThat(lock.getHoldCount()).isEqualTo(2); // count 증가

                // 또 재진입
                redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                    assertThat(lock.getHoldCount()).isEqualTo(3); // count 계속 증가
                    return null;
                });

                assertThat(lock.getHoldCount()).isEqualTo(2); // unlock 후 감소
                return null;
            });

            assertThat(lock.getHoldCount()).isEqualTo(1); // unlock 후 감소
            return null;
        });

        // 모든 unlock 후 완전히 해제됨
        assertThat(lock.isHeldByCurrentThread()).isFalse();
    }

    @Test
    @DisplayName("여러 락을 재진입할 수 있다")
    void shouldAllowReentrantWithMultipleLocks() {
        // given
        List<String> lockKeys = List.of("lock:test:multi:1", "lock:test:multi:2");

        // when
        String result = redisLockExecutor.executeWithLock(lockKeys, () -> {
            // 동일한 락 키들로 재진입
            return redisLockExecutor.executeWithLock(lockKeys, () -> {
                return "multi-reentrant-success";
            });
        });

        // then
        assertThat(result).isEqualTo("multi-reentrant-success");
    }

    @Test
    @DisplayName("다른 스레드는 락을 획득할 수 없다")
    void shouldNotAllowLockFromDifferentThread() throws InterruptedException {
        // given
        String lockKey = "lock:test:different-thread:1";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger thread2ExecutionCount = new AtomicInteger(0);

        // when
        Thread thread1 = new Thread(() -> {
            redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                latch.countDown(); // Thread2에게 시작 신호
                try {
                    Thread.sleep(1000); // 락 보유 중
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        Thread thread2 = new Thread(() -> {
            try {
                latch.await(); // Thread1이 락을 획득할 때까지 대기

                // Thread1이 락을 보유 중이므로 실패해야 함
                assertThatThrownBy(() ->
                    redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                        thread2ExecutionCount.incrementAndGet();
                        return null;
                    }, 500, 5000) // waitTime을 짧게 설정
                ).isInstanceOf(LockAcquisitionException.class);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // then
        assertThat(thread2ExecutionCount.get()).isEqualTo(0); // Thread2는 실행되지 않음
    }

    @Test
    @DisplayName("OrderService 시나리오: 전체 락 획득 후 PointService가 재진입한다")
    void shouldSimulateOrderServiceScenario() {
        // given
        List<String> orderLockKeys = List.of(
            "lock:point:user:1",
            "lock:product:100",
            "lock:coupon:user:50"
        );
        String pointLockKey = "lock:point:user:1";

        AtomicInteger orderExecutionCount = new AtomicInteger(0);
        AtomicInteger pointExecutionCount = new AtomicInteger(0);

        // when: OrderService가 모든 락 획득
        String result = redisLockExecutor.executeWithLock(orderLockKeys, () -> {
            orderExecutionCount.incrementAndGet();

            // PointService가 독립적으로 포인트 락 획득 시도 (재진입)
            return redisLockExecutor.executeWithLock(List.of(pointLockKey), () -> {
                pointExecutionCount.incrementAndGet();
                return "order-with-point-success";
            });
        });

        // then
        assertThat(result).isEqualTo("order-with-point-success");
        assertThat(orderExecutionCount.get()).isEqualTo(1);
        assertThat(pointExecutionCount.get()).isEqualTo(1); // 재진입 성공
    }

    @Test
    @DisplayName("락 해제 후 다른 스레드가 획득할 수 있다")
    void shouldAllowLockAfterRelease() throws InterruptedException {
        // given
        String lockKey = "lock:test:release:1";
        AtomicInteger executionCount = new AtomicInteger(0);

        // when: Thread1이 락 획득 후 해제
        Thread thread1 = new Thread(() ->
            redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                executionCount.incrementAndGet();
                return null;
            })
        );

        thread1.start();
        thread1.join(); // Thread1 완료 대기

        // Thread2가 락 획득 (이제 가능해야 함)
        Thread thread2 = new Thread(() ->
            redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                executionCount.incrementAndGet();
                return null;
            })
        );

        thread2.start();
        thread2.join();

        // then
        assertThat(executionCount.get()).isEqualTo(2); // 두 스레드 모두 실행됨
    }

    @Test
    @DisplayName("예외 발생 시에도 락이 올바르게 해제된다")
    void shouldReleaseLockOnException() {
        // given
        String lockKey = "lock:test:exception:1";
        RLock lock = redissonClient.getLock(lockKey);

        // when
        assertThatThrownBy(() ->
            redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
                throw new RuntimeException("Test exception");
            })
        ).isInstanceOf(RuntimeException.class)
         .hasMessage("Test exception");

        // then
        assertThat(lock.isHeldByCurrentThread()).isFalse(); // 락이 해제됨
    }

    @Test
    @DisplayName("Reentrant Lock의 TTL이 재진입 시 갱신된다")
    void shouldRenewTTLOnReentrant() {
        // given
        String lockKey = "lock:test:ttl:1";
        long leaseTime = 3000; // 3초

        // when
        redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
            RLock lock = redissonClient.getLock(lockKey);
            long ttl1 = lock.remainTimeToLive();

            try {
                Thread.sleep(1000); // 1초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 재진입
            redisLockExecutor.executeWithLock(List.of(lockKey), () -> {
                long ttl2 = lock.remainTimeToLive();

                // TTL이 갱신되어야 함 (ttl2 > ttl1이어야 함)
                assertThat(ttl2).isGreaterThan(ttl1 - 1000); // 대략적인 체크

                return null;
            }, 3000, leaseTime);

            return null;
        }, 3000, leaseTime);

        // then: 테스트 완료
    }
}
