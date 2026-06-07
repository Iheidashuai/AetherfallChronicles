package com.mythicrealm.api.gameplay.gameconfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

public final class ConfigModels {
    private ConfigModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemTemplate(
        String id,
        String name,
        String type,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int hpBonus,
        int mpBonus,
        BigDecimal critBonus,
        int randomRange,
        String description,
        int sellPrice
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonsterConfig(
        String id,
        String name,
        int level,
        int maxHP,
        int strength,
        boolean isBoss,
        List<LootEntry> lootTable,
        int expReward,
        int goldReward
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LootEntry(String itemId, double dropRate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DungeonConfig(
        String id,
        String name,
        String description,
        String difficulty,
        List<DungeonRoom> rooms,
        int recommendedLevel,
        int recommendedPower
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DungeonRoom(String id, List<RoomMonster> monsters, boolean isBossRoom) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoomMonster(String monsterId, int count) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestConfig(
        String id,
        String title,
        String category,
        String description,
        String lore,
        int priority,
        String navigationTarget,
        List<String> prerequisiteIds,
        List<QuestCondition> conditions,
        List<QuestReward> rewards
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestCondition(String id, String type, String targetId, int targetValue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestReward(String type, String targetId, int amount) {
    }
}
