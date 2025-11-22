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
        int concurrentCount = 5;  // 현실적인 동시 요청 수준 (중복 클릭, 여러 디바이스)
        BigDecimal chargeAmount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 5개 스레드가 동시에 1000원씩 충전
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
        BigDecimal initialBalance = BigDecimal.valueOf(10000);  // 5개 요청에 맞춤
        pointService.chargePoint(userId, initialBalance);

        int concurrentCount = 5;  // 현실적인 동시 요청 수준 (중복 클릭, 여러 디바이스)
        BigDecimal useAmount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 5개 스레드가 동시에 1000원씩 사용
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
        BigDecimal initialBalance = BigDecimal.valueOf(10000);
        pointService.chargePoint(userId, initialBalance);

        // 가벼운 동시 접근 수 (2회씩) 가정
        int chargeCountConcurrency = 2;
        int useCountConcurrency = 2;
        BigDecimal amount = BigDecimal.valueOf(1000);

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(chargeCountConcurrency + useCountConcurrency);

        AtomicInteger chargeSuccessCount = new AtomicInteger(0);
        AtomicInteger useSuccessCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 5개 충전, 5개 사용 동시 실행
        IntStream.range(0, chargeCountConcurrency).forEach(i -> {
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

        IntStream.range(0, useCountConcurrency).forEach(i -> {
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

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // then
        System.out.println("=== 충전/사용 혼합 테스트 결과 ===");
        System.out.println("충전 성공: " + chargeSuccessCount.get());
        System.out.println("사용 성공: " + useSuccessCount.get());
        System.out.println("실패: " + failCount.get());

        Point finalPoint = pointService.getPoint(userId);
        BigDecimal expectedBalance = initialBalance
                .add(amount.multiply(BigDecimal.valueOf(chargeSuccessCount.get())))
                .subtract(amount.multiply(BigDecimal.valueOf(useSuccessCount.get())));

        assertThat(chargeSuccessCount.get()).isEqualTo(chargeCountConcurrency);
        assertThat(useSuccessCount.get()).isEqualTo(useCountConcurrency);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    @DisplayName("잔액 부족 시 동시 사용 시도 - 일부만 성공")
    void concurrentUsePoint_InsufficientBalance() throws InterruptedException {
        // given
        Long userId = 1L;
        BigDecimal initialBalance = BigDecimal.valueOf(3000);
        pointService.chargePoint(userId, initialBalance);

        int concurrentCount = 5;  // 현실적인 동시 요청 수준
        BigDecimal useAmount = BigDecimal.valueOf(1000);
        // 초기 잔액: 3000원, 사용 금액: 1000원 * 5회 시도 = 5000원 필요
        // 예상: 3회 성공, 2회 실패

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 5개 스레드가 동시에 1000원씩 사용 (잔액은 3000원)
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

        assertThat(successCount.get()).isEqualTo(3); // 3000원 / 1000원 = 3회
        assertThat(failCount.get()).isEqualTo(2);
        assertThat(finalPoint.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
