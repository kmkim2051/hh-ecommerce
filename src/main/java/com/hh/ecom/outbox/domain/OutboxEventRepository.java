package com.hh.ecom.outbox.domain;

import java.util.List;
import java.util.Optional;


public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    Optional<OutboxEvent> findById(Long id);

    List<OutboxEvent> findByOrderId(Long orderId);

    List<OutboxEvent> findAll();

    void deleteAll();
}
