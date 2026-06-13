package com.mythicrealm.domain.enhancement.event;

import com.mythicrealm.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 强化尝试事件
 */
public record EnhancementAttemptedEvent(
    long itemId,
    long playerId,
    int previousLevel,
    int targetLevel,
    double successRate,
    int cost,
    Instant occurredOn
) implements DomainEvent {

    public static EnhancementAttemptedEvent of(
        long itemId,
        long playerId,
        int previousLevel,
        int targetLevel,
        double successRate,
        int cost
    ) {
        return new EnhancementAttemptedEvent(
            itemId,
            playerId,
            previousLevel,
            targetLevel,
            successRate,
            cost,
            Instant.now()
        );
    }
}
