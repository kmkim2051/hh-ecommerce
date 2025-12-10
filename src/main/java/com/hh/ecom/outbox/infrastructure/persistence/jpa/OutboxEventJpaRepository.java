package com.hh.ecom.outbox.infrastructure.persistence.jpa;

import com.hh.ecom.outbox.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findByOrderId(Long orderId);
}
