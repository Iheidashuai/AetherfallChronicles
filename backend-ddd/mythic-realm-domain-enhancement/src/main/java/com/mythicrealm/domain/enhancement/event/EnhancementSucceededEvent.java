package com.mythicrealm.domain.enhancement.event;

import com.mythicrealm.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 强化成功事件
 */
public record EnhancementSucceededEvent(
    long itemId,
    long playerId,
    int previousLevel,
    int newLevel,
    int cost,
    double successRate,
    Instant occurredOn
) implements DomainEvent {

    public static EnhancementSucceededEvent of(
        long itemId,
        long playerId,
        int previousLevel,
        int newLevel,
        int cost,
        double successRate
    ) {
        return new EnhancementSucceededEvent(
            itemId,
            playerId,
            previousLevel,
            newLevel,
            cost,
            successRate,
            Instant.now()
        );
    }
}
