package com.hh.ecom.outbox.domain;

public interface MessagePublisher {
    void publish(String topic, String key, Object message);
}
