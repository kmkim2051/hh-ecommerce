package com.hh.ecom.point.domain;

import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository {
    PointTransaction save(PointTransaction transaction);
    Optional<PointTransaction> findById(Long id);
    List<PointTransaction> findByPointId(Long pointId);
    void deleteAll(); // for testing
}
