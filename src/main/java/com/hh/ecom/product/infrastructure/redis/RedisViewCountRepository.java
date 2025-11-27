package com.hh.ecom.product.infrastructure.redis;

import com.hh.ecom.product.domain.ViewCountRepository;
import com.hh.ecom.product.domain.exception.ViewCountFlushException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RedisViewCountRepository implements ViewCountRepository {

    private static final String VIEW_DELTA_KEY_PREFIX = "product:view:delta:";
    private static final String VIEW_RECENT_1D_KEY = "product:view:recent1d";
    private static final String VIEW_RECENT_3D_KEY = "product:view:recent3d";
    private static final String VIEW_RECENT_7D_KEY = "product:view:recent7d";

    private static final long TTL_1_DAY_SECONDS = 86400L;
    private static final long TTL_3_DAYS_SECONDS = TTL_1_DAY_SECONDS * 3;
    private static final long TTL_7_DAYS_SECONDS = TTL_1_DAY_SECONDS * 7;

    private final RedisTemplate<String, Long> redisTemplate;
    private final RedisTemplate<String, String> viewHistoryRedisTemplate;

    public RedisViewCountRepository(
            RedisTemplate<String, Long> redisTemplate,
            @Qualifier("viewHistoryRedisTemplate") RedisTemplate<String, String> viewHistoryRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.viewHistoryRedisTemplate = viewHistoryRedisTemplate;
    }

    @Override
    public void incrementViewCount(Long productId) {
        // 1. 델타 증가
        String deltaKey = VIEW_DELTA_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(deltaKey);

        // 2. 각 기간별 Sorted Set의 score 증가 (member는 productId, score는 조회수)
        String member = productId.toString();
        incrementScoreWithTTL(VIEW_RECENT_1D_KEY, member, TTL_1_DAY_SECONDS);
        incrementScoreWithTTL(VIEW_RECENT_3D_KEY, member, TTL_3_DAYS_SECONDS);
        incrementScoreWithTTL(VIEW_RECENT_7D_KEY, member, TTL_7_DAYS_SECONDS);

        log.debug("Incremented view count delta for product: {}", productId);
    }

    private void incrementScoreWithTTL(String key, String member, long ttlSeconds) {
        viewHistoryRedisTemplate.opsForZSet().incrementScore(key, member, 1.0);
        viewHistoryRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Map<Long, Long> getAndClearAllDeltas() {
        Map<Long, Long> deltas = new HashMap<>();

        Set<String> keys = redisTemplate.keys(VIEW_DELTA_KEY_PREFIX + "*");
        if (keys.isEmpty()) {
            return deltas;
        }

        try {
            for (String key : keys) {
                Long productId = extractProductId(key);
                Long delta = redisTemplate.opsForValue().get(key);

                if (delta != null && delta > 0) {
                    deltas.put(productId, delta);
                }
            }
        } catch (Exception e) {
            // 모든 델타 읽기 (실패 시 예외 전파)
            String errorMsg = String.format("Failed to read view count deltas from Redis. Total keys: %d", keys.size());
            log.warn(errorMsg, e);
            throw new ViewCountFlushException(errorMsg, e);
        }

        if (!deltas.isEmpty()) {
            try {
                redisTemplate.delete(keys);
                log.info("Successfully cleared {} view count deltas from Redis", deltas.size());
            } catch (Exception e) {
                String errorMsg = String.format("Failed to delete view count deltas from Redis. Keys count: %d", keys.size());
                log.error(errorMsg, e);
                throw new ViewCountFlushException(errorMsg, e);
            }
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
        Set<String> topMembers = viewHistoryRedisTemplate.opsForZSet()
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
        if (days == null) {
            return null;
        }
        return switch (days) {
            case 1 -> VIEW_RECENT_1D_KEY;
            case 3 -> VIEW_RECENT_3D_KEY;
            case 7 -> VIEW_RECENT_7D_KEY;
            default -> null;
        };
    }

    private Long extractProductId(String key) {
        String idStr = key.substring(VIEW_DELTA_KEY_PREFIX.length());
        return Long.parseLong(idStr);
    }
}
