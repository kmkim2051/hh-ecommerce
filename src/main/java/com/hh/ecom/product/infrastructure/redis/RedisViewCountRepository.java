package com.hh.ecom.product.infrastructure.redis;

import com.hh.ecom.product.domain.ViewCountRepository;
import com.hh.ecom.product.domain.exception.ViewCountFlushException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RedisViewCountRepository implements ViewCountRepository {

    private static final String VIEW_DELTA_KEY_PREFIX = "product:view:delta:";
    private static final String VIEW_DELTA_SET_KEY = "product:view:delta:set";

    private final Map<Long, AtomicInteger> viewCountBuffer = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final int MIN_BUFFER_THRESHOLD = 5;
    private static final int MAX_BUFFER_THRESHOLD = 10;

    private final RedisTemplate<String, Long> redisTemplate;
    private final RedisTemplate<String, String> customStringRedisTemplate;

    public RedisViewCountRepository(
            RedisTemplate<String, Long> redisTemplate,
            @Qualifier("customStringRedisTemplate") RedisTemplate<String, String> customStringRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.customStringRedisTemplate = customStringRedisTemplate;
    }

    @Override
    public void incrementViewCount(Long productId) {
        AtomicInteger counter = viewCountBuffer.computeIfAbsent(productId, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        int threshold = MIN_BUFFER_THRESHOLD + random.nextInt(MAX_BUFFER_THRESHOLD - MIN_BUFFER_THRESHOLD + 1);

        // 임계값 도달 시 Redis에 flush
        if (currentCount >= threshold) {
            // CAS로 안전하게 버퍼 값 추출 및 초기화
            int countToFlush = counter.getAndSet(0);
            if (countToFlush > 0) {
                flushToRedis(productId, countToFlush);
            }
        }
    }

    private void flushToRedis(Long productId, int count) {
        // 1. 델타 증가
        String deltaKey = VIEW_DELTA_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(deltaKey, count);

        // 2. productId를 Set에 추가 (중복 자동 제거)
        redisTemplate.opsForSet().add(VIEW_DELTA_SET_KEY, productId);

        // 3. 각 기간별 Sorted Set의 score 증가 (member는 productId, score는 조회수)
        String member = productId.toString();
        for (RedisViewPeriod period : RedisViewPeriod.values()) {
            incrementScoreWithTTL(period.getKey(), member, count, period.getTtlSeconds());
        }

        log.debug("Flushed {} view counts to Redis for product: {}", count, productId);
    }

    private void incrementScoreWithTTL(String key, String member, int count, long ttlSeconds) {
        customStringRedisTemplate.opsForZSet().incrementScore(key, member, count);
        customStringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Map<Long, Long> getAndClearAllDeltas() {
        Map<Long, Long> deltas = new HashMap<>();

        List<Long> productIds = redisTemplate.opsForSet().pop(VIEW_DELTA_SET_KEY, Long.MAX_VALUE);

        if (productIds == null || productIds.isEmpty()) {
            log.debug("No view count deltas to process");
            return deltas;
        }

        try {
            for (Long productId : productIds) {
                String deltaKey = VIEW_DELTA_KEY_PREFIX + productId;
                Long delta = redisTemplate.opsForValue().getAndDelete(deltaKey);

                if (delta != null && delta > 0) {
                    deltas.put(productId, delta);
                }
            }

            log.info("Successfully read and cleared {} view count deltas from Redis", deltas.size());
        } catch (Exception e) {
            String errorMsg = String.format("Failed to get and delete view count deltas from Redis. Total productIds: %d", productIds.size());
            log.error(errorMsg, e);
            throw new ViewCountFlushException(errorMsg, e);
        }

        return deltas;
    }

    @Override
    public Long getDelta(Long productId) {
        String key = VIEW_DELTA_KEY_PREFIX + productId;
        Long delta = redisTemplate.opsForValue().get(key);
        return delta != null ? delta : 0L;
    }

    @Override
    public List<Long> getTopViewedProductIds(Integer days, Integer limit) {
        if (limit == null || limit <= 0) {
            return List.of();
        }

        final String key = getViewKeyByDays(days);
        if (key == null) {
            log.warn("Unsupported days parameter: {}. Supported: 1, 3, 7", days);
            return List.of();
        }

        // Sorted Set에서 score 내림차순으로 상위 N개 조회
        Set<String> topMembers = customStringRedisTemplate.opsForZSet()
                .reverseRange(key, 0, limit - 1);

        if (topMembers == null || topMembers.isEmpty()) {
            log.debug("No view history found for recent {} days", days);
            return List.of();
        }

        return topMembers.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    private String getViewKeyByDays(Integer days) {
        RedisViewPeriod period = RedisViewPeriod.fromDays(days);
        return period != null ? period.getKey() : null;
    }

    @Override
    public void flushBuffer() {
        viewCountBuffer.forEach((productId, counter) -> {
            int count = counter.getAndSet(0);
            if (count > 0) {
                flushToRedis(productId, count);
            }
        });
        viewCountBuffer.clear();
        log.debug("Flushed all buffered view counts to Redis");
    }

    @Getter
    private enum RedisViewPeriod {
        ONE_DAY(1, "product:view:recent1d", TimeUnit.DAYS.toSeconds(1)),
        THREE_DAYS(3, "product:view:recent3d", TimeUnit.DAYS.toSeconds(3)),
        SEVEN_DAYS(7, "product:view:recent7d", TimeUnit.DAYS.toSeconds(7));

        private final int days;
        private final String key;
        private final long ttlSeconds;

        RedisViewPeriod(int days, String key, long ttlSeconds) {
            this.days = days;
            this.key = key;
            this.ttlSeconds = ttlSeconds;
        }

        public static RedisViewPeriod fromDays(Integer days) {
            if (days == null) {
                return null;
            }
            for (RedisViewPeriod period : values()) {
                if (period.days == days) {
                    return period;
                }
            }
            return null;
        }
    }
}
