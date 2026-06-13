package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.time.Instant;

public record RobotAgent(
    PlayerRecord player,
    String title,
    String personality,
    int power,
    int dungeonClears,
    int peakEnhancement,
    int legendaryLootCount,
    String currentActivityKind,
    String currentActivityText,
    Instant currentActivityAt,
    Instant lastActivityAt
) {
    public long id() {
        return player.id();
    }

    public String name() {
        return player.name();
    }

    public int wealthTierLevel() {
        return player.wealthTierLevel();
    }
}
