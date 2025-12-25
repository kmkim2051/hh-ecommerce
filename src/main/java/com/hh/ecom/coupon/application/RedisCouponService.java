package com.hh.ecom.coupon.application;

import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.infrastructure.redis.CouponQueueSerializer;
import com.hh.ecom.coupon.infrastructure.redis.RedisCouponKeyGenerator;
import com.hh.ecom.coupon.infrastructure.redis.dto.CouponIssueQueueEntry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 비동기 쿠폰 발급 서비스
 * Redis Set + List for FCFS queue.
 */
@Slf4j
@Service
public class RedisCouponService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCouponKeyGenerator redisCouponKeyGenerator;
    private final CouponQueueSerializer queueSerializer;

    public RedisCouponService(
            @Qualifier("customStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            RedisCouponKeyGenerator redisCouponKeyGenerator,
            CouponQueueSerializer queueSerializer
    ) {
        this.redisTemplate = redisTemplate;
        this.redisCouponKeyGenerator = redisCouponKeyGenerator;
        this.queueSerializer = queueSerializer;
    }

    /**
     * @deprecated Kafka 기반 방식으로 전환되어 더 이상 사용되지 않습니다.
     */
    @Deprecated(forRemoval = true)
    public void enqueueUserIfEligible(Long userId, Long couponId) {
        final String usersSetKey = redisCouponKeyGenerator.generateUsersSetKey(couponId);
        final String stockKey = redisCouponKeyGenerator.generateStockKey(couponId);
        final String queueKey = redisCouponKeyGenerator.generateQueueKey(couponId);

        // Step 1: 중복 발급 체크 (SADD)
        Long addResult = redisTemplate.opsForSet().add(usersSetKey, userId.toString());
        if (addResult == null || addResult == 0) {
            log.debug("중복 쿠폰 발급 요청 차단: userId={}, couponId={}", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }

        // Step 2: 현재 발급 요청 유저 수 count (SCARD)
        Long participantCount = redisTemplate.opsForSet().size(usersSetKey);
        if (participantCount == null) {
            participantCount = 0L;
        }

        // Step 3: 쿠폰 잔여 수량 정보 확인
        String stockValue = redisTemplate.opsForValue().get(stockKey);
        if (stockValue == null) {
            log.error("쿠폰 잔여 수량 정보가 Redis에 없습니다: couponId={}", couponId);
            // user request 정보도 삭제
            redisTemplate.opsForSet().remove(usersSetKey, userId.toString());
            throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
        }

        Long stock = Long.parseLong(stockValue);

        // Step 4: 쿠폰 Sold out 체크
        if (participantCount > stock) {
            log.debug("쿠폰 재고 부족: couponId={}, stock={}, participants={}",
                couponId, stock, participantCount);
            // Remove from set since they didn't get in
            redisTemplate.opsForSet().remove(usersSetKey, userId.toString());
            throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
        }

        // Step 5: 발급 가능하다면 DTO 생성 및 직렬화 후 push
        CouponIssueQueueEntry queueEntry = CouponIssueQueueEntry.of(userId, couponId);
        String serialized = queueSerializer.serialize(queueEntry);
        redisTemplate.opsForList().rightPush(queueKey, serialized);

        log.info("쿠폰 발급 요청 큐 등록 성공: {}", queueEntry);
    }

    /**
     * @deprecated Kafka Consumer로 대체되어 더 이상 사용되지 않습니다.
     *  CouponIssueWorker 에서만 사용되며, Worker와 함께 제거될 예정입니다.
     */
    @Deprecated
    public CouponIssueQueueEntry dequeueUserRequest(Long couponId) {
        String queueKey = redisCouponKeyGenerator.generateQueueKey(couponId);
        String serialized = redisTemplate.opsForList().leftPop(queueKey);

        if (serialized == null) {
            return null;
        }

        return queueSerializer.deserialize(serialized);
    }

    /**
     * 실패한 요청의 retry를 위한 Re-queue
     *
     * @deprecated Kafka Consumer로 대체되어 더 이상 사용되지 않습니다.
     *             {@link CouponIssueWorker}에서만 사용되며, Worker와 함께 제거될 예정입니다.
     */
    @Deprecated
    public void requeueUserRequest(Long userId, Long couponId) {
        String queueKey = redisCouponKeyGenerator.generateQueueKey(couponId);
        CouponIssueQueueEntry queueEntry = CouponIssueQueueEntry.of(userId, couponId);
        String serialized = queueSerializer.serialize(queueEntry);
        redisTemplate.opsForList().rightPush(queueKey, serialized);

        log.warn("쿠폰 발급 요청 재큐잉: {}", queueEntry);
    }

    /**
     * Redis에 쿠폰 잔여 수량 init
     */
    public void initializeCouponStock(Long couponId, Integer stock) {
        String stockKey = redisCouponKeyGenerator.generateStockKey(couponId);

        // Only set if not exists (idempotent)
        Boolean success = redisTemplate.opsForValue().setIfAbsent(stockKey, stock.toString());

        if (Boolean.TRUE.equals(success)) {
            log.info("Redis 쿠폰 재고 초기화 완료: couponId={}, stock={}", couponId, stock);
        } else {
            log.info("Redis 쿠폰 재고 이미 존재: couponId={}", couponId);
        }
    }

    /**
     * @deprecated Kafka 방식에서는 Queue를 사용하지 않으므로 의미가 없습니다.
     *             테스트 목적으로만 유지되며, 향후 제거될 예정입니다.
     */
    @Deprecated
    public Long getQueueSize(Long couponId) {
        String queueKey = redisCouponKeyGenerator.generateQueueKey(couponId);
        Long size = redisTemplate.opsForList().size(queueKey);
        return (size != null) ? size : 0L;
    }

    public Long getParticipantCount(Long couponId) {
        String usersSetKey = redisCouponKeyGenerator.generateUsersSetKey(couponId);
        Long count = redisTemplate.opsForSet().size(usersSetKey);
        return (count != null) ? count : 0L;
    }
}
