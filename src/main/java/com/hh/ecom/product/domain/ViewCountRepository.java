package com.hh.ecom.product.domain;

import java.util.List;
import java.util.Map;

public interface ViewCountRepository {
    void incrementViewCount(Long productId);

    Map<Long, Long> getAndClearAllDeltas();

    Long getDelta(Long productId);

    List<Long> getTopViewedProductIds(Integer days, Integer limit);

    void flushBuffer();
}
