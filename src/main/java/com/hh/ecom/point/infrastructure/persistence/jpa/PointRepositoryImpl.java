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
            // UPDATE: 기존 Entity 조회 및 업데이트
            PointEntity existing = pointJpaRepository.findById(point.getId())
                    .orElseThrow(() -> new RuntimeException(
                            "수정할 포인트를 찾을 수 없습니다. id=" + point.getId()
                    ));
            updateEntity(existing, point);
            entity = existing;
        } else {
            // INSERT: 새 Entity 생성
            entity = PointEntity.from(point);
        }

        PointEntity saved = pointJpaRepository.save(entity);
        return saved.toDomain();
    }

    private void updateEntity(PointEntity existing, Point point) {
        // 영속 Entity를 직접 수정 (JPA Dirty Checking 활용)
        existing.updateFrom(point);
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
