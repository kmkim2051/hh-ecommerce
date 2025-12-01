package com.hh.ecom.point.application;

import com.hh.ecom.common.lock.LockKeyGenerator;
import com.hh.ecom.common.lock.RedisLockExecutor;
import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.domain.TransactionType;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {
    private final PointRepository pointRepository;
    private final PointTransactionRepository transactionRepository;
    private final RedisLockExecutor redisLockExecutor;
    private final LockKeyGenerator lockKeyGenerator;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public Point usePointWithinTransaction(Long userId, BigDecimal amount, Long orderId) {
        return executeUsePoint(userId, amount, orderId);
    }

    private Point executeUsePoint(Long userId, BigDecimal amount, Long orderId) {
        Point point = findPointByUserId(userId);

        Point usedPoint = point.use(amount);
        Point savedPoint = pointRepository.save(usedPoint);

        savePointTransaction(PointTransactionCommand.builder()
                .pointId(savedPoint.getId())
                .amount(amount)
                .transactionType(TransactionType.USE)
                .orderId(orderId)
                .balanceAfter(savedPoint.getBalance())
                .build());

        log.debug("포인트 사용 완료: userId={}, amount={}, orderId={}, balance={}", userId, amount, orderId, savedPoint.getBalance());
        return savedPoint;
    }

    public Point refundPoint(Long userId, BigDecimal amount, Long orderId) {
        String lockKey = lockKeyGenerator.generatePointLockKey(userId);
        log.debug("포인트 환불 락 획득 시도: lockKey={}, userId={}, amount={}", lockKey, userId, amount);

        return redisLockExecutor.executeWithLock(List.of(lockKey), () ->
            transactionTemplate.execute(status -> {
                Point point = findPointByUserId(userId);

                Point refundedPoint = point.refund(amount);
                Point savedPoint = pointRepository.save(refundedPoint);

                savePointTransaction(PointTransactionCommand.builder()
                        .pointId(savedPoint.getId())
                        .amount(amount)
                        .transactionType(TransactionType.REFUND)
                        .orderId(orderId)
                        .balanceAfter(savedPoint.getBalance())
                        .build());

                log.info("포인트 환불 완료: userId={}, amount={}, orderId={}, balance={}", userId, amount, orderId, savedPoint.getBalance());
                return savedPoint;
            })
        );
    }

    public Point chargePoint(Long userId, BigDecimal amount) {
        final String lockKey = lockKeyGenerator.generatePointLockKey(userId);
        log.debug("포인트 충전 락 획득 시도: lockKey={}, userId={}, amount={}", lockKey, userId, amount);

        return redisLockExecutor.executeWithLock(List.of(lockKey), () ->
            transactionTemplate.execute(status ->
                chargePointInternal(userId, amount)
            )
        );
    }

    private Point chargePointInternal(Long userId, BigDecimal amount) {
        try {
            Point point = pointRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        // 계좌가 없으면 새로 생성
                        Point newPoint = Point.createWithUserId(userId);
                        return pointRepository.save(newPoint);
                    });

            Point chargedPoint = point.charge(amount);
            Point savedPoint = pointRepository.save(chargedPoint);

            savePointTransaction(PointTransactionCommand.builder()
                    .pointId(savedPoint.getId())
                    .amount(amount)
                    .transactionType(TransactionType.CHARGE)
                    .orderId(null)
                    .balanceAfter(savedPoint.getBalance())
                    .build());

            return savedPoint;
        } catch (DataIntegrityViolationException e) {
            log.debug("포인트 계좌 동시 생성 감지, 재조회 후 처리. userId={}", userId);

            Point point = pointRepository.findByUserId(userId)
                    .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));

            Point chargedPoint = point.charge(amount);
            Point savedPoint = pointRepository.save(chargedPoint);

            savePointTransaction(PointTransactionCommand.builder()
                    .pointId(savedPoint.getId())
                    .amount(amount)
                    .transactionType(TransactionType.CHARGE)
                    .orderId(null)
                    .balanceAfter(savedPoint.getBalance())
                    .build());

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

    public boolean hasPointAccount(Long userId) {
        return pointRepository.findByUserId(userId).isPresent();
    }

    private Point findPointByUserId(Long userId) {
        return pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND, "userId: " + userId));
    }

    private void savePointTransaction(PointTransactionCommand command) {
        PointTransaction transaction = PointTransaction.create(
                command.pointId(),
                command.amount(),
                command.transactionType(),
                command.orderId(),
                command.balanceAfter()
        );
        transactionRepository.save(transaction);
    }

    @Builder
    private record PointTransactionCommand(
            Long pointId,
            BigDecimal amount,
            TransactionType transactionType,
            Long orderId,
            BigDecimal balanceAfter
    ) {}
}
