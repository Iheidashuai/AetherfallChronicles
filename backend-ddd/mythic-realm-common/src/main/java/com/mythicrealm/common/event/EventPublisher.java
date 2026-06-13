package com.mythicrealm.common.event;

import com.mythicrealm.common.domain.DomainEvent;

public interface EventPublisher {
    void publish(DomainEvent event);
}
