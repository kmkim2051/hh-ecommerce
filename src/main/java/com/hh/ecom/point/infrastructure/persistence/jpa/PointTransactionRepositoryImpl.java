package com.hh.ecom.point.infrastructure.persistence.jpa;

import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.infrastructure.persistence.entity.PointTransactionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class PointTransactionRepositoryImpl implements PointTransactionRepository {
    private final PointTransactionJpaRepository pointTransactionJpaRepository;

    @Override
    public PointTransaction save(PointTransaction transaction) {
        PointTransactionEntity entity = PointTransactionEntity.from(transaction);
        PointTransactionEntity saved = pointTransactionJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<PointTransaction> findById(Long id) {
        return pointTransactionJpaRepository.findById(id)
                .map(PointTransactionEntity::toDomain);
    }

    @Override
    public List<PointTransaction> findByPointId(Long pointId) {
        return pointTransactionJpaRepository.findByPointId(pointId).stream()
                .map(PointTransactionEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        pointTransactionJpaRepository.deleteAll();
    }
}
