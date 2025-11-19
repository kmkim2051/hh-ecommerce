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
            Point point = pointRepository.findByUserIdForUpdate(userId)
                    .orElseGet(() -> {
                        // 계좌가 없으면 새로 생성
                        Point newPoint = Point.createWithUserId(userId);
                        return pointRepository.save(newPoint);
                    });

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
        } catch (DataIntegrityViolationException e) {
            // 동시에 계좌 생성 시도 -> unique constraint 위반 발생
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

    @Transactional(readOnly = true)
    public Point getPoint(Long userId) {
        return findPointByUserId(userId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId) {
        return pointRepository.findByUserId(userId)
                .map(Point::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public Point getPointById(Long pointId) {
        return pointRepository.findById(pointId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "pointId: " + pointId));
    }

    @Transactional(readOnly = true)
    public List<PointTransaction> getTransactionHistory(Long userId) {
        Point point = findPointByUserId(userId);

        return transactionRepository.findByPointId(point.getId());
    }

    @Transactional
    public Point usePoint(Long userId, BigDecimal amount, Long orderId) {
        Point point = findPointByUserIdWithLock(userId);

        Point usedPoint = point.use(amount);
        Point savedPoint = pointRepository.save(usedPoint);

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
        Point point = findPointByUserIdWithLock(userId);

        Point refundedPoint = point.refund(amount);
        Point savedPoint = pointRepository.save(refundedPoint);

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

    private Point findPointByUserId(Long userId) {
        return pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));
    }

    private Point findPointByUserIdWithLock(Long userId) {
        return pointRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));
    }
}
