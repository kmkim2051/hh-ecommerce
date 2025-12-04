package com.hh.ecom.product.infrastructure.redis;

import com.hh.ecom.product.domain.SalesRanking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class SalesRankingRedisRepository {
    private final RedisTemplate<String, String> redisTemplate;
    private final SalesRankingKeyGenerator keyGenerator;

    public SalesRankingRedisRepository(
            @Qualifier("customStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            SalesRankingKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    private static final int DAILY_KEY_TTL_DAYS = 30;
    private static final Duration DAILY_KEY_TIMEOUT = Duration.ofDays(DAILY_KEY_TTL_DAYS);

    /**
     * 판매량 증가 (원자적 연산)
     * - 전체 기간 랭킹과 일별 랭킹 동시 업데이트
     * - ZINCRBY 사용으로 동시성 안전 보장
     *
     * @param productId 상품 ID
     * @param quantity 판매 수량
     * @param date 판매 날짜
     */
    public void incrementSalesCount(Long productId, Integer quantity, LocalDate date) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId는 양수여야 합니다. productId=" + productId);
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity는 양수여야 합니다. quantity=" + quantity);
        }
        if (date == null) {
            throw new IllegalArgumentException("date는 null일 수 없습니다.");
        }

        String allTimeKey = keyGenerator.generateAllTimeKey();
        String dailyKey = keyGenerator.generateDailyKey(date);
        String productIdStr = productId.toString();

        try {
            // 전체 기간 랭킹 업데이트
            redisTemplate.opsForZSet().incrementScore(allTimeKey, productIdStr, quantity);

            // 일별 랭킹 업데이트
            redisTemplate.opsForZSet().incrementScore(dailyKey, productIdStr, quantity);

            // 일별 키는 TTL 설정 (30일 후 자동 삭제)
            redisTemplate.expire(dailyKey, DAILY_KEY_TIMEOUT);

            log.debug("판매량 증가 완료: productId={}, quantity={}, date={}", productId, quantity, date);
        } catch (Exception e) {
            log.error("판매량 증가 실패: productId={}, quantity={}, error={}", productId, quantity, e.getMessage(), e);
            throw new RuntimeException("판매량 증가 실패", e);
        }
    }

    /**
     * Top N 상품 조회
     * - SortedSet에서 상위 N개 조회 (score 내림차순)
     *
     * @param key Redis Key
     * @param limit 조회할 개수
     * @return 판매 랭킹 리스트
     */
    public List<SalesRanking> getTopProducts(String key, int limit) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key는 비어있을 수 없습니다.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit는 양수여야 합니다. limit=" + limit);
        }

        try {
            Set<ZSetOperations.TypedTuple<String>> results =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

            if (results == null || results.isEmpty()) {
                log.debug("랭킹 데이터 없음: key={}", key);
                return Collections.emptyList();
            }

            List<SalesRanking> rankings = results.stream()
                    .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
                    .map(tuple -> SalesRanking.of(
                            Long.parseLong(tuple.getValue()),
                            tuple.getScore().longValue()
                    ))
                    .collect(Collectors.toList());

            log.debug("랭킹 조회 완료: key={}, count={}", key, rankings.size());
            return rankings;
        } catch (Exception e) {
            log.error("랭킹 조회 실패: key={}, error={}", key, e.getMessage(), e);
            throw new RuntimeException("랭킹 조회 실패", e);
        }
    }

    /**
     * 최근 n일 Top N 상품 조회
     * - ZUNIONSTORE로 여러 일별 SortedSet 합산
     * - 임시 키 사용 후 즉시 삭제
     *
     * @param days 최근 일수
     * @param limit 조회할 개수
     * @return 판매 랭킹 리스트
     */
    public List<SalesRanking> getTopProductsInRecentDays(int days, int limit) {
        if (days <= 0) {
            throw new IllegalArgumentException("days는 양수여야 합니다. days=" + days);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit는 양수여야 합니다. limit=" + limit);
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<String> dailyKeys = keyGenerator.generateDailyKeysForRange(startDate, endDate);

        if (dailyKeys.isEmpty()) {
            log.warn("일별 키가 없습니다: days={}", days);
            return Collections.emptyList();
        }

        // 단일 키인 경우 ZUNIONSTORE 불필요
        if (dailyKeys.size() == 1) {
            return getTopProducts(dailyKeys.get(0), limit);
        }

        String tempKey = "product:ranking:sales:temp:" + UUID.randomUUID();

        try {
            // ZUNIONSTORE: 여러 SortedSet 합치기 (score 합산)
            String firstKey = dailyKeys.get(0);
            Collection<String> otherKeys = dailyKeys.subList(1, dailyKeys.size());

            redisTemplate.opsForZSet().unionAndStore(firstKey, otherKeys, tempKey);

            List<SalesRanking> result = getTopProducts(tempKey, limit);

            log.debug("최근 {}일 랭킹 조회 완료: count={}", days, result.size());
            return result;
        } catch (Exception e) {
            log.error("최근 {}일 랭킹 조회 실패: error={}", days, e.getMessage(), e);
            throw new RuntimeException("최근 " + days + "일 랭킹 조회 실패", e);
        } finally {
            // 임시 키 삭제
            try {
                redisTemplate.delete(tempKey);
                log.debug("임시 키 삭제 완료: tempKey={}", tempKey);
            } catch (Exception e) {
                log.warn("임시 키 삭제 실패: tempKey={}, error={}", tempKey, e.getMessage());
            }
        }
    }

    public boolean isOrderRecorded(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId는 양수여야 합니다. orderId=" + orderId);
        }

        String recordedKey = keyGenerator.generateRecordedOrderKey(orderId);
        Boolean exists = redisTemplate.hasKey(recordedKey);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 주문 기록 마킹
     * - 중복 방지를 위해 Set에 추가
     * - TTL 30일 설정
     *
     * @param orderId 주문 ID
     * @return 새로 추가되었으면 true, 이미 존재하면 false
     */
    public boolean markOrderRecorded(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId는 양수여야 합니다. orderId=" + orderId);
        }

        String recordedKey = keyGenerator.generateRecordedOrderKey(orderId);

        try {
            Boolean added = redisTemplate.opsForValue()
                    .setIfAbsent(recordedKey, "1", DAILY_KEY_TIMEOUT);
            boolean isNew = Boolean.TRUE.equals(added);

            if (isNew) {
                log.debug("주문 기록 마킹 완료: orderId={}", orderId);
            } else {
                log.warn("이미 기록된 주문: orderId={}", orderId);
            }

            return isNew;
        } catch (Exception e) {
            log.error("주문 기록 마킹 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new RuntimeException("주문 기록 마킹 실패", e);
        }
    }
}
