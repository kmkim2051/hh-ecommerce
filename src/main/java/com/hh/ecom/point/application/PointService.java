package com.hh.ecom.point.application;

import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.domain.TransactionType;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {
    private static final int POINT_CHARGE_MAX_RETRY_COUNT = 3;

    /**
     * ### FR-P-001: 포인트 충전
     * - 설명: 사용자가 포인트를 충전하여 잔액을 증가시킬 수 있다
     * - 입력: 사용자 ID, 충전 금액
     * - 출력: 충전 후 잔액
     * - 비고: 충전 이력이 기록되어야 한다
     *
     * ### FR-P-002: 포인트 조회
     * - 설명: 사용자가 현재 포인트 잔액을 조회할 수 있다
     * - 입력: 사용자 ID
     * - 출력: 현재 잔액
     *
     * ### FR-P-003: 포인트 사용 이력 조회
     * - 설명: 사용자가 포인트 충전/사용/환불 이력을 조회할 수 있다
     * - 입력: 사용자 ID
     * - 출력: 거래 내역 목록 (거래 유형, 금액, 거래 후 잔액, 거래 시간)
     */

    private final PointRepository pointRepository;
    private final PointTransactionRepository transactionRepository;

    /**
     * FR-P-001: 포인트 충전
     * 낙관적 락을 적용하여 동시성 제어
     */
    @Transactional
    public Point chargePoint(Long userId, BigDecimal amount) {
        int retryCount = 0;

        while (retryCount < POINT_CHARGE_MAX_RETRY_COUNT) {
            try {
                // 1. 포인트 계좌 조회 또는 생성
                Point point = pointRepository.findByUserId(userId)
                        .orElseGet(() -> {
                            Point newPoint = Point.create(userId);
                            return pointRepository.save(newPoint);
                        });

                // 2. 포인트 충전
                Point chargedPoint = point.charge(amount);
                Point savedPoint = pointRepository.save(chargedPoint);

                // 3. 거래 이력 기록
                PointTransaction transaction = PointTransaction.create(
                        savedPoint.getId(),
                        amount,
                        TransactionType.CHARGE,
                        null, // orderId 없음
                        savedPoint.getBalance()
                );
                transactionRepository.save(transaction);

                return savedPoint;

            } catch (PointException e) {
                // 낙관적 락 실패 시 재시도
                if (e.getErrorCode() == PointErrorCode.OPTIMISTIC_LOCK_FAILURE) {
                    retryCount++;
                    if (retryCount >= POINT_CHARGE_MAX_RETRY_COUNT) {
                        throw e;
                    }
                    try {
                        Thread.sleep(50L * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new PointException(PointErrorCode.OPTIMISTIC_LOCK_FAILURE);
    }

    /**
     * FR-P-002: 포인트 조회
     */
    public Point getPoint(Long userId) {
        return pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));
    }

    /**
     * FR-P-002: 포인트 ID로 조회 (내부용)
     */
    public Point getPointById(Long pointId) {
        return pointRepository.findById(pointId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "pointId: " + pointId));
    }

    /**
     * FR-P-003: 포인트 사용 이력 조회
     */
    public List<PointTransaction> getTransactionHistory(Long userId) {
        // 1. 포인트 계좌 조회
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));

        // 2. 거래 이력 조회
        return transactionRepository.findByPointId(point.getId());
    }

    /**
     * 포인트 사용 (주문 시 사용)
     * 낙관적 락을 적용하여 동시성 제어
     */
    @Transactional
    public Point usePoint(Long userId, BigDecimal amount, Long orderId) {
        int retryCount = 0;

        while (retryCount < POINT_CHARGE_MAX_RETRY_COUNT) {
            try {
                // 1. 포인트 계좌 조회
                Point point = pointRepository.findByUserId(userId)
                        .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));

                // 2. 포인트 사용
                Point usedPoint = point.use(amount);
                Point savedPoint = pointRepository.save(usedPoint);

                // 3. 거래 이력 기록
                PointTransaction transaction = PointTransaction.create(
                        savedPoint.getId(),
                        amount,
                        TransactionType.USE,
                        orderId,
                        savedPoint.getBalance()
                );
                transactionRepository.save(transaction);

                return savedPoint;

            } catch (PointException e) {
                // 낙관적 락 실패 시 재시도
                if (e.getErrorCode() == PointErrorCode.OPTIMISTIC_LOCK_FAILURE) {
                    retryCount++;
                    if (retryCount >= POINT_CHARGE_MAX_RETRY_COUNT) {
                        // 최대 재시도 횟수 초과
                        throw e;
                    }
                    // 짧은 대기 후 재시도 (exponential backoff)
                    try {
                        Thread.sleep(50L * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }

        // 이 코드는 도달하지 않지만, 컴파일러를 위해 추가
        throw new PointException(PointErrorCode.OPTIMISTIC_LOCK_FAILURE);
    }

    /**
     * 포인트 환불 (주문 취소 시 사용)
     * 낙관적 락을 적용하여 동시성 제어
     */
    @Transactional
    public Point refundPoint(Long userId, BigDecimal amount, Long orderId) {
        int retryCount = 0;

        while (retryCount < POINT_CHARGE_MAX_RETRY_COUNT) {
            try {
                // 1. 포인트 계좌 조회
                Point point = pointRepository.findByUserId(userId)
                        .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));

                // 2. 포인트 환불
                Point refundedPoint = point.refund(amount);
                Point savedPoint = pointRepository.save(refundedPoint);

                // 3. 거래 이력 기록
                PointTransaction transaction = PointTransaction.create(
                        savedPoint.getId(),
                        amount,
                        TransactionType.REFUND,
                        orderId,
                        savedPoint.getBalance()
                );
                transactionRepository.save(transaction);

                return savedPoint;

            } catch (PointException e) {
                // 낙관적 락 실패 시 재시도
                if (e.getErrorCode() == PointErrorCode.OPTIMISTIC_LOCK_FAILURE) {
                    retryCount++;
                    if (retryCount >= POINT_CHARGE_MAX_RETRY_COUNT) {
                        throw e;
                    }
                    try {
                        Thread.sleep(50L * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new PointException(PointErrorCode.OPTIMISTIC_LOCK_FAILURE);
    }

    /**
     * 포인트 계좌 존재 여부 확인
     */
    public boolean hasPointAccount(Long userId) {
        return pointRepository.findByUserId(userId).isPresent();
    }
}
