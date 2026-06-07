package com.mythicrealm.domain.player.event;

import com.mythicrealm.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 金币变更事件
 */
public record GoldChangedEvent(
    Long playerId,
    int oldGold,
    int newGold,
    int changeAmount,
    Instant occurredOn
) implements DomainEvent {

    public GoldChangedEvent(Long playerId, int oldGold, int newGold, int changeAmount) {
        this(playerId, oldGold, newGold, changeAmount, Instant.now());
    }

    public boolean isIncrease() {
        return changeAmount > 0;
    }

    public boolean isDecrease() {
        return changeAmount < 0;
    }
}
