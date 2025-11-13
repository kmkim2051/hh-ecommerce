package com.hh.ecom.point.application;

import com.hh.ecom.config.TestContainersConfig;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("PointService 동시성 테스트")
class PointServiceConcurrencyTest extends TestContainersConfig {

    @Autowired
    private PointService pointService;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        pointRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 사용자의 동시 포인트 충전 - 잔액 정합성 검증")
    void concurrentChargePoint_SameUser() throws InterruptedException {
        // given
        Long userId = 1L;
        int concurrentCount = 50;
        BigDecimal chargeAmount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50개 스레드가 동시에 1000원씩 충전
        IntStream.range(0, concurrentCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    // 데드락 발생 시 재시도 (최대 3회)
                    int retryCount = 0;
                    int maxRetries = 3;
                    while (retryCount < maxRetries) {
                        try {
                            pointService.chargePoint(userId, chargeAmount);
                            successCount.incrementAndGet();
                            break;
                        } catch (Exception e) {
                            retryCount++;
                            if (retryCount >= maxRetries) {
                                failCount.incrementAndGet();
                                System.err.println("충전 실패 (재시도 " + maxRetries + "회): " + e.getMessage());
                            } else {
                                // 짧은 대기 후 재시도
                                Thread.sleep(10);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then
        System.out.println("=== 포인트 충전 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Point finalPoint = pointService.getPoint(userId);
        BigDecimal expectedBalance = chargeAmount.multiply(BigDecimal.valueOf(successCount.get()));

        assertThat(successCount.get()).isEqualTo(concurrentCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    @DisplayName("동일 사용자의 동시 포인트 사용 - 잔액 정합성 검증")
    void concurrentUsePoint_SameUser() throws InterruptedException {
        // given
        Long userId = 1L;
        BigDecimal initialBalance = BigDecimal.valueOf(100000);
        pointService.chargePoint(userId, initialBalance);

        int concurrentCount = 50;
        BigDecimal useAmount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50개 스레드가 동시에 1000원씩 사용
        IntStream.range(0, concurrentCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount, (long) i);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("사용 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then
        System.out.println("=== 포인트 사용 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Point finalPoint = pointService.getPoint(userId);
        BigDecimal expectedBalance = initialBalance.subtract(useAmount.multiply(BigDecimal.valueOf(successCount.get())));

        assertThat(successCount.get()).isEqualTo(concurrentCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    @DisplayName("동일 사용자의 동시 충전/사용 혼합 - 잔액 정합성 검증")
    void concurrentChargeAndUsePoint_SameUser() throws InterruptedException {
        // given
        Long userId = 1L;
        BigDecimal initialBalance = BigDecimal.valueOf(50000);
        pointService.chargePoint(userId, initialBalance);

        int chargeCount = 25;
        int useCount = 25;
        BigDecimal amount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(chargeCount + useCount);

        AtomicInteger chargeSuccessCount = new AtomicInteger(0);
        AtomicInteger useSuccessCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 25개 충전, 25개 사용 동시 실행
        IntStream.range(0, chargeCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, amount);
                    chargeSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        IntStream.range(0, useCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, amount, (long) i);
                    useSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then
        System.out.println("=== 충전/사용 혼합 테스트 결과 ===");
        System.out.println("충전 성공: " + chargeSuccessCount.get());
        System.out.println("사용 성공: " + useSuccessCount.get());
        System.out.println("실패: " + failCount.get());

        Point finalPoint = pointService.getPoint(userId);
        BigDecimal expectedBalance = initialBalance
                .add(amount.multiply(BigDecimal.valueOf(chargeSuccessCount.get())))
                .subtract(amount.multiply(BigDecimal.valueOf(useSuccessCount.get())));

        assertThat(chargeSuccessCount.get()).isEqualTo(chargeCount);
        assertThat(useSuccessCount.get()).isEqualTo(useCount);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    @DisplayName("잔액 부족 시 동시 사용 시도 - 일부만 성공")
    void concurrentUsePoint_InsufficientBalance() throws InterruptedException {
        // given
        Long userId = 1L;
        BigDecimal initialBalance = BigDecimal.valueOf(10000);
        pointService.chargePoint(userId, initialBalance);

        int concurrentCount = 20;
        BigDecimal useAmount = BigDecimal.valueOf(1000);
        // 초기 잔액: 10000원, 사용 금액: 1000원 * 20회 시도 = 20000원 필요
        // 예상: 10회 성공, 10회 실패

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 20개 스레드가 동시에 1000원씩 사용 (잔액은 10000원)
        IntStream.range(0, concurrentCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount, (long) i);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // then
        System.out.println("=== 잔액 부족 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());

        Point finalPoint = pointService.getPoint(userId);

        assertThat(successCount.get()).isEqualTo(10); // 10000원 / 1000원 = 10회
        assertThat(failCount.get()).isEqualTo(10);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
