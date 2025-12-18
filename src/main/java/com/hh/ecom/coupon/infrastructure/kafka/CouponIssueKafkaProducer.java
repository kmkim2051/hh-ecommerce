package com.hh.ecom.coupon.infrastructure.kafka;

import com.hh.ecom.coupon.domain.event.CouponIssueRequestEvent;
import com.hh.ecom.coupon.domain.exception.CouponErrorCode;
import com.hh.ecom.coupon.domain.exception.CouponException;
import com.hh.ecom.coupon.infrastructure.redis.RedisCouponKeyGenerator;
import com.hh.ecom.outbox.domain.MessagePublisher;
import com.hh.ecom.outbox.infrastructure.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 쿠폰 발급 요청 Kafka Producer
 * - Redis 빠른 검증 (중복, 재고) 후 Kafka 발행
 * - couponId를 Partition Key로 사용 → 동일 쿠폰은 순차 처리
 */
@Slf4j
@Component
public class CouponIssueKafkaProducer {

    private final MessagePublisher messagePublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCouponKeyGenerator redisCouponKeyGenerator;

    public CouponIssueKafkaProducer(
            MessagePublisher messagePublisher,
            @Qualifier("customStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            RedisCouponKeyGenerator redisCouponKeyGenerator
    ) {
        this.messagePublisher = messagePublisher;
        this.redisTemplate = redisTemplate;
        this.redisCouponKeyGenerator = redisCouponKeyGenerator;
    }

    /**
     * 쿠폰 발급 요청을 Kafka로 발행
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return requestId (UUID) - 멱등성 추적용
     */
    public String publishCouponIssueRequest(Long userId, Long couponId) {
        // 1. Redis 빠른 검증 (Fast Pre-check)
        validateWithRedis(userId, couponId);

        // 2. 이벤트 생성 (UUID requestId 생성)
        String requestId = UUID.randomUUID().toString();
        CouponIssueRequestEvent event = CouponIssueRequestEvent.of(requestId, userId, couponId);

        // 3. Kafka 발행 (couponId를 Partition Key로 사용)
        messagePublisher.publish(
            KafkaTopics.COUPON_ISSUE,
            couponId.toString(),  // Partition Key: 동일 쿠폰은 동일 파티션에서 순차 처리
            event
        );

        log.info("쿠폰 발급 요청 Kafka 발행 완료: requestId={}, userId={}, couponId={}",
            requestId, userId, couponId);

        return requestId;
    }

    /**
     * Redis를 이용한 빠른 검증
     * - 중복 발급 체크
     * - 재고 소진 체크
     */
    private void validateWithRedis(Long userId, Long couponId) {
        final String usersSetKey = redisCouponKeyGenerator.generateUsersSetKey(couponId);
        final String stockKey = redisCouponKeyGenerator.generateStockKey(couponId);

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
    }
}
