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
        PointEntity savedEntity;

        if (point.getId() == null) {
            PointEntity entity = PointEntity.from(point);
            savedEntity = pointJpaRepository.save(entity);
        } else {
            PointEntity existingEntity = pointJpaRepository.findById(point.getId())
                    .orElseThrow(() -> new RuntimeException(
                            "수정할 포인트를 찾을 수 없습니다. id=" + point.getId()
                    ));

            PointEntity updatedEntity = PointEntity.builder()
                    .id(existingEntity.getId())
                    .userId(point.getUserId())
                    .balance(point.getBalance())
                    .createdAt(existingEntity.getCreatedAt())
                    .updatedAt(point.getUpdatedAt())
                    .version(existingEntity.getVersion())  // 기존 version 유지
                    .build();

            savedEntity = pointJpaRepository.save(updatedEntity);
        }

        return savedEntity.toDomain();
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
    public void deleteAll() {
        pointJpaRepository.deleteAll();
    }
}
