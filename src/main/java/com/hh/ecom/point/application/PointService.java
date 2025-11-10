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

    private final PointRepository pointRepository;
    private final PointTransactionRepository transactionRepository;

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

    public Point getPoint(Long userId) {
        return pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));
    }

    public Point getPointById(Long pointId) {
        return pointRepository.findById(pointId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "pointId: " + pointId));
    }

    public List<PointTransaction> getTransactionHistory(Long userId) {
        // 1. 포인트 계좌 조회
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));

        // 2. 거래 이력 조회
        return transactionRepository.findByPointId(point.getId());
    }

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

    public boolean hasPointAccount(Long userId) {
        return pointRepository.findByUserId(userId).isPresent();
    }
}
