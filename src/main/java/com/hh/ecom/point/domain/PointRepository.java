package com.hh.ecom.point.domain;

import java.util.Optional;

public interface PointRepository {
    Point save(Point point);
    Optional<Point> findById(Long id);
    Optional<Point> findByUserId(Long userId);
    Optional<Point> findByUserIdForUpdate(Long userId);
    void deleteAll(); // for testing
}
