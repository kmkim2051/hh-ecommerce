package com.hh.ecom.point.infrastructure.persistence.jpa;

import com.hh.ecom.point.domain.Point;
import com.hh.ecom.point.domain.PointRepository;
import com.hh.ecom.point.infrastructure.persistence.entity.PointEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class PointRepositoryImpl implements PointRepository {
    private final PointJpaRepository pointJpaRepository;

    @Override
    public Point save(Point point) {
        PointEntity entity;
        if (point.getId() != null) {
            // UPDATE: 기존 엔티티를 조회하여 version 보존
            entity = pointJpaRepository.findById(point.getId())
                    .map(existing -> updateEntity(existing, point))
                    .orElse(PointEntity.from(point));
        } else {
            // INSERT: 새 엔티티 생성
            entity = PointEntity.from(point);
        }
        PointEntity saved = pointJpaRepository.save(entity);
        return saved.toDomain();
    }

    private PointEntity updateEntity(PointEntity existing, Point point) {
        return PointEntity.builder()
                .id(existing.getId())
                .userId(point.getUserId())
                .balance(point.getBalance())
                .updatedAt(point.getUpdatedAt())
                .version(existing.getVersion())  // 기존 version 보존
                .build();
    }

    @Override
    public Optional<Point> findById(Long id) {
        return pointJpaRepository.findById(id)
                .map(PointEntity::toDomain);
    }

    @Override
    public Optional<Point> findByUserId(Long userId) {
        return pointJpaRepository.findByUserId(userId)
                .map(PointEntity::toDomain);
    }

    @Override
    public Optional<Point> findByUserIdForUpdate(Long userId) {
        return pointJpaRepository.findByUserIdWithLock(userId)
                .map(PointEntity::toDomain);
    }

    @Override
    public void deleteAll() {
        pointJpaRepository.deleteAll();
    }
}
