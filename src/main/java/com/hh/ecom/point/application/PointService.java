package com.hh.ecom.point.application;

import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.domain.TransactionType;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {
    private final PointRepository pointRepository;
    private final PointTransactionRepository transactionRepository;

    @Transactional
    public Point chargePoint(Long userId, BigDecimal amount) {
        return chargePointInternal(userId, amount);
    }

    private Point chargePointInternal(Long userId, BigDecimal amount) {
        try {
            // 1. 포인트 계좌 조회 (비관적 락 적용)
            Point point = pointRepository.findByUserIdForUpdate(userId)
                    .orElseGet(() -> {
                        // 계좌가 없으면 새로 생성
                        Point newPoint = Point.createWithUserId(userId);
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
        } catch (DataIntegrityViolationException e) {
            // 동시에 계좌 생성 시도로 unique constraint 위반 발생
            // 다시 조회하여 락을 획득하고 충전 재시도
            log.debug("포인트 계좌 동시 생성 감지, 재조회 후 처리. userId={}", userId);

            // 이미 다른 트랜잭션이 생성한 계좌를 락과 함께 조회
            Point point = pointRepository.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));

            Point chargedPoint = point.charge(amount);
            Point savedPoint = pointRepository.save(chargedPoint);

            PointTransaction transaction = PointTransaction.create(
                    savedPoint.getId(),
                    amount,
                    TransactionType.CHARGE,
                    null,
                    savedPoint.getBalance()
            );
            transactionRepository.save(transaction);

            return savedPoint;
        }
    }

    public Point getPoint(Long userId) {
        return pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));
    }

    public BigDecimal getBalance(Long userId) {
        return Optional.ofNullable(getPoint(userId))
                .map(Point::getBalance)
                .orElse(BigDecimal.ZERO);
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
        // 1. 포인트 계좌 조회 (비관적 락 적용)
        Point point = pointRepository.findByUserIdForUpdate(userId)
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
    }

    @Transactional
    public Point refundPoint(Long userId, BigDecimal amount, Long orderId) {
        // 1. 포인트 계좌 조회 (비관적 락 적용)
        Point point = pointRepository.findByUserIdForUpdate(userId)
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
    }

    public boolean hasPointAccount(Long userId) {
        return pointRepository.findByUserId(userId).isPresent();
    }
}
