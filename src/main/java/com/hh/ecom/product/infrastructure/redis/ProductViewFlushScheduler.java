package com.hh.ecom.product.infrastructure.redis;

import com.hh.ecom.product.domain.ViewCountRepository;
import com.hh.ecom.product.domain.exception.ViewCountFlushException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewFlushScheduler {
    private static final long FLUSH_RATE_MS = 60 * 1000;

    private final ViewCountRepository viewCountRepository;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRate = FLUSH_RATE_MS)
    @Transactional
    public void flushViewCountsToDatabase() {
        try {
            Map<Long, Long> deltas = viewCountRepository.getAndClearAllDeltas();

            if (deltas.isEmpty()) {
                log.debug("No view count deltas to flush");
                return;
            }

            flushDeltasToDatabase(deltas);

        } catch (ViewCountFlushException e) {
            log.error("Failed to flush view counts - Redis operation failed. Will retry in next schedule.", e);
            // Redis 실패 시 델타는 보존됨 - 다음 스케줄에서 재시도
        } catch (Exception e) {
            log.error("Unexpected error during view count flush. Will retry in next schedule.", e);
        }
    }

    private void flushDeltasToDatabase(Map<Long, Long> deltas) {
        int updatedCount = 0;
        int failedCount = 0;

        for (Map.Entry<Long, Long> entry : deltas.entrySet()) {
            Long productId = entry.getKey();
            Long delta = entry.getValue();

            try {
                int affected = jdbcTemplate.update(
                        "UPDATE products SET view_count = view_count + ? WHERE id = ?",
                        delta, productId
                );
                if (affected > 0) {
                    updatedCount++;
                } else {
                    log.warn("Product not found for view count update: productId={}", productId);
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to flush view count for product {}: delta={}", productId, delta, e);
                failedCount++;
            }
        }

        if (failedCount > 0) {
            log.warn("Flushed view counts to DB: {} succeeded, {} failed out of {} deltas",
                    updatedCount, failedCount, deltas.size());
        } else {
            log.info("Successfully flushed {} view counts to DB", updatedCount);
        }
    }
}
