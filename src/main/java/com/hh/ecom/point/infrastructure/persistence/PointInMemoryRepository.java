package com.hh.ecom.point.infrastructure.persistence;

import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.domain.exception.PointErrorCode;
import com.hh.ecom.point.domain.exception.PointException;
import com.hh.ecom.point.infrastructure.persistence.entity.PointEntity;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
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
                    .version(entity.getVersion())
                    .updatedAt(entity.getUpdatedAt())
                    .build();

            // userId -> pointId 매핑 저장
            userIdToPointId.put(point.getUserId(), newId);
        } else {
            // 기존 포인트 계좌 업데이트 (낙관적 락 적용)
            PointEntity existingEntity = points.get(point.getId());
            if (existingEntity == null) {
                throw new PointException(PointErrorCode.POINT_NOT_FOUND);
            }

            // 버전 체크 (낙관적 락)
            if (!existingEntity.getVersion().equals(point.getVersion() - 1)) {
                throw new PointException(PointErrorCode.OPTIMISTIC_LOCK_FAILURE);
            }

            entity = PointEntity.from(point);
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
    public void deleteAll() {
        points.clear();
        userIdToPointId.clear();
        idGenerator.set(1);
    }
}
