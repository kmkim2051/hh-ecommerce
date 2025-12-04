package com.hh.ecom.product.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis SortedSet 기반 판매 랭킹 Key 생성 전략
 * - 일관된 Key 네이밍 제공
 * - 날짜 기반 Key 자동 생성
 * - Key 유효성 검증
 */
@Slf4j
@Component
public class SalesRankingKeyGenerator {
    private static final String BASE_PREFIX = "product:ranking:sales";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 전체 기간 판매량 랭킹 Key 생성
     * @return "product:ranking:sales:all"
     */
    public String generateAllTimeKey() {
        return BASE_PREFIX + ":all";
    }

    /**
     * 특정 일자 판매량 Key 생성
     * @param date 날짜
     * @return "product:ranking:sales:daily:20251203"
     */
    public String generateDailyKey(LocalDate date) {
        validateDate(date);
        String formattedDate = date.format(DATE_FORMATTER);
        return BASE_PREFIX + ":daily:" + formattedDate;
    }

    /**
     * 최근 n일 랭킹 Key 생성 (집계용)
     * @param days 일수
     * @return "product:ranking:sales:recent:7"
     */
    public String generateRecentDaysKey(int days) {
        validateDays(days);
        return BASE_PREFIX + ":recent:" + days;
    }

    /**
     * 날짜 범위 내 모든 일별 Key 목록 생성
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 일별 Key 리스트
     */
    public List<String> generateDailyKeysForRange(LocalDate startDate, LocalDate endDate) {
        validateDate(startDate);
        validateDate(endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다. startDate=" + startDate + ", endDate=" + endDate);
        }

        List<String> keys = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            keys.add(generateDailyKey(currentDate));
            currentDate = currentDate.plusDays(1);
        }

        log.debug("날짜 범위 Key 생성 완료: startDate={}, endDate={}, keyCount={}", startDate, endDate, keys.size());
        return keys;
    }

    /**
     * 주문 기록용 Set Key 생성 (중복 방지)
     * @param orderId 주문 ID
     * @return "product:ranking:recorded:{orderId}"
     */
    public String generateRecordedOrderKey(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId는 양수여야 합니다. orderId=" + orderId);
        }
        return BASE_PREFIX + ":recorded:" + orderId;
    }

    private void validateDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("날짜는 null일 수 없습니다.");
        }
    }

    private void validateDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("일수는 양수여야 합니다. days=" + days);
        }
        if (days > 365) {
            throw new IllegalArgumentException("일수는 365일을 초과할 수 없습니다. days=" + days);
        }
    }
}
