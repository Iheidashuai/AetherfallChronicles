package com.mythicrealm.common.domain;

import java.time.Instant;

/**
 * 领域事件
 */
public interface DomainEvent {
    default Instant occurredOn() {
        return Instant.now();
    }
}
