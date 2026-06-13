package com.mythicrealm.domain.enhancement.event;

import com.mythicrealm.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 强化失败事件
 */
public record EnhancementFailedEvent(
    long itemId,
    long playerId,
    int previousLevel,
    int newLevel,
    int newLuck,
    int cost,
    double successRate,
    Instant occurredOn
) implements DomainEvent {

    public static EnhancementFailedEvent of(
        long itemId,
        long playerId,
        int previousLevel,
        int newLevel,
        int newLuck,
        int cost,
        double successRate
    ) {
        return new EnhancementFailedEvent(
            itemId,
            playerId,
            previousLevel,
            newLevel,
            newLuck,
            cost,
            successRate,
            Instant.now()
        );
    }
}
