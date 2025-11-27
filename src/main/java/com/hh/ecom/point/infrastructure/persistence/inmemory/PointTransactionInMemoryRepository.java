package com.hh.ecom.point.infrastructure.persistence.inmemory;

import com.hh.ecom.point.domain.PointTransaction;
import com.hh.ecom.point.domain.PointTransactionRepository;
import com.hh.ecom.point.infrastructure.persistence.entity.PointTransactionEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Deprecated
public class PointTransactionInMemoryRepository implements PointTransactionRepository {
    private final Map<Long, PointTransactionEntity> transactions = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public PointTransaction save(PointTransaction pointTx) {
        PointTransactionEntity entity = PointTransactionEntity.from(pointTx, idGenerator::getAndIncrement);
        transactions.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public Optional<PointTransaction> findById(Long id) {
        return Optional.ofNullable(transactions.get(id))
                .map(PointTransactionEntity::toDomain);
    }

    @Override
    public List<PointTransaction> findByPointId(Long pointId) {
        return transactions.values().stream()
                .filter(entity -> entity.getPointId().equals(pointId))
                .map(PointTransactionEntity::toDomain)
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
                .toList();
    }

    @Override
    public void deleteAll() {
        transactions.clear();
        idGenerator.set(1);
    }
}
