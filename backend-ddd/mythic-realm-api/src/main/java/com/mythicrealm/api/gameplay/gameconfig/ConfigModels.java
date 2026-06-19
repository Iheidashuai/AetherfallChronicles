package com.mythicrealm.api.gameplay.gameconfig;

import java.math.BigDecimal;
import java.util.List;

public final class ConfigModels {
    private ConfigModels() {
    }

    public record ItemTemplate(
        String id,
        String name,
        String type,
        String category,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int resistanceBonus,
        int hpBonus,
        int mpBonus,
        BigDecimal critBonus,
        int randomRange,
        String description,
        int sellPrice,
        boolean stackable,
        int maxStack,
        String effectType,
        String effectValueJson,
        boolean tradeable,
        String marketCategory,
        int marketMinUnitPrice,
        int marketMaxUnitPrice,
        double enhanceBonusRate,
        int minEnhanceLevel,
        int maxEnhanceLevel
    ) {
        public boolean equipment() {
            return "equipment".equals(category);
        }
    }

    public record MonsterConfig(
        String id,
        String name,
        int level,
        int maxHP,
        int attackPower,
        int armor,
        int resistance,
        double accuracy,
        double evasion,
        double critChance,
        double critResist,
        int speed,
        String damageType,
        String archetype,
        String mechanic,
        boolean isBoss,
        List<LootEntry> lootTable,
        int expReward,
        int goldReward
    ) {
    }

    public record LootEntry(String itemId, double dropRate) {
    }

    public record DungeonConfig(
        String id,
        String name,
        String description,
        String difficulty,
        List<DungeonRoom> rooms,
        int recommendedLevel,
        int recommendedPower,
        int minimumLevel,
        int minimumPower,
        String bossArchetype,
        int expectedRounds
    ) {
    }

    public record DungeonRoom(String id, List<RoomMonster> monsters, boolean isBossRoom) {
    }

    public record RoomMonster(String monsterId, int count) {
    }

    public record QuestConfig(
        String id,
        String title,
        String category,
        String description,
        String lore,
        int priority,
        String navigationTarget,
        String conditionLogic,
        String resetPeriod,
        int difficultyScore,
        String rewardTier,
        List<String> prerequisiteIds,
        List<QuestCondition> conditions,
        List<QuestReward> rewards
    ) {
    }

    public record QuestCondition(String id, String type, String targetId, int targetValue) {
    }

    public record QuestReward(String type, String targetId, int amount) {
    }
}
