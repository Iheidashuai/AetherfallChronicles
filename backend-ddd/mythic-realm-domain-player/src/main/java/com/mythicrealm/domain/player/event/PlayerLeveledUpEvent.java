package com.mythicrealm.domain.player.event;

import com.mythicrealm.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 玩家升级事件
 */
public record PlayerLeveledUpEvent(
    Long playerId,
    int newLevel,
    int oldLevel,
    int strength,
    int agility,
    int constitution,
    int intelligence,
    int spirit,
    Instant occurredOn
) implements DomainEvent {

    public PlayerLeveledUpEvent(Long playerId, int newLevel, int oldLevel,
                               int strength, int agility, int constitution,
                               int intelligence, int spirit) {
        this(playerId, newLevel, oldLevel, strength, agility, constitution,
             intelligence, spirit, Instant.now());
    }
}
