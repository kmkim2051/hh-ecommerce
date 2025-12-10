package com.hh.ecom.outbox.infrastructure.persistence.jpa;

import com.hh.ecom.outbox.domain.OutboxEvent;
import com.hh.ecom.outbox.domain.OutboxEventRepository;
import com.hh.ecom.outbox.infrastructure.persistence.entity.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {
    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = OutboxEventEntity.from(outboxEvent);
        OutboxEventEntity savedEntity = outboxEventJpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return outboxEventJpaRepository.findById(id)
                .map(OutboxEventEntity::toDomain);
    }

    @Override
    public List<OutboxEvent> findByOrderId(Long orderId) {
        return outboxEventJpaRepository.findByOrderId(orderId)
                .stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public List<OutboxEvent> findAll() {
        return outboxEventJpaRepository.findAll()
                .stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteAll() {
        outboxEventJpaRepository.deleteAll();
    }
}
