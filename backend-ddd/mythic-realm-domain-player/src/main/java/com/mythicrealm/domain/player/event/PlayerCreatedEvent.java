package com.mythicrealm.domain.player.event;

import com.mythicrealm.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 玩家创建事件
 */
public record PlayerCreatedEvent(
    Long playerId,
    Long accountId,
    String name,
    String profession,
    Instant occurredOn
) implements DomainEvent {

    public PlayerCreatedEvent(Long playerId, Long accountId, String name, String profession) {
        this(playerId, accountId, name, profession, Instant.now());
    }
}
