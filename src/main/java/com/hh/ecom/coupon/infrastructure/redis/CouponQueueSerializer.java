package com.hh.ecom.coupon.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hh.ecom.coupon.infrastructure.redis.dto.CouponIssueQueueEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 큐 엔트리 직렬화/역직렬화 유틸리티
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponQueueSerializer {
    private final ObjectMapper objectMapper;

    public String serialize(CouponIssueQueueEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            log.error("큐 엔트리 직렬화 실패: {}", entry, e);
            throw new IllegalArgumentException("큐 엔트리 직렬화 실패", e);
        }
    }

    public CouponIssueQueueEntry deserialize(String json) {
        try {
            return objectMapper.readValue(json, CouponIssueQueueEntry.class);
        } catch (JsonProcessingException e) {
            log.error("큐 엔트리 역직렬화 실패: {}", json, e);
            throw new IllegalArgumentException("큐 엔트리 역직렬화 실패", e);
        }
    }
}
