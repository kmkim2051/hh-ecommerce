package com.hh.ecom.point.infrastructure.persistence.inmemory;

import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import com.hh.ecom.point.infrastructure.persistence.entity.PointEntity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PointInMemoryRepository implements PointRepository {
    private final Map<Long, PointEntity> points = new ConcurrentHashMap<>();
    private final Map<Long, Long> userIdToPointId = new ConcurrentHashMap<>(); // userId -> pointId 매핑
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Point save(Point point) {
        PointEntity entity;

        if (point.getId() == null) {
            // 새로운 포인트 계좌 생성
            Long newId = idGenerator.getAndIncrement();
            entity = PointEntity.from(point);
            entity = PointEntity.builder()
                    .id(newId)
                    .userId(entity.getUserId())
                    .balance(entity.getBalance())
                    .updatedAt(entity.getUpdatedAt())
                    // version은 Entity에서 자동 관리
                    .build();

            // userId -> pointId 매핑 저장
            userIdToPointId.put(point.getUserId(), newId);
        } else {
            // 기존 포인트 계좌 업데이트
            PointEntity existingEntity = points.get(point.getId());
            if (existingEntity == null) {
                throw new PointException(PointErrorCode.POINT_NOT_FOUND);
            }

            entity = PointEntity.from(point);
            // ID는 유지
            entity = PointEntity.builder()
                    .id(point.getId())
                    .userId(entity.getUserId())
                    .balance(entity.getBalance())
                    .updatedAt(entity.getUpdatedAt())
                    // version은 InMemory에서는 무시 (JPA에서만 의미 있음)
                    .build();
        }

        points.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public Optional<Point> findById(Long id) {
        return Optional.ofNullable(points.get(id))
                .map(PointEntity::toDomain);
    }

    @Override
    public Optional<Point> findByUserId(Long userId) {
        Long pointId = userIdToPointId.get(userId);
        if (pointId == null) {
            return Optional.empty();
        }
        return findById(pointId);
    }

    @Override
    public Optional<Point> findByUserIdForUpdate(Long userId) {
        // In-memory에서는 락이 필요없으므로 findByUserId와 동일
        return findByUserId(userId);
    }

    @Override
    public void deleteAll() {
        points.clear();
        userIdToPointId.clear();
        idGenerator.set(1);
    }
}
