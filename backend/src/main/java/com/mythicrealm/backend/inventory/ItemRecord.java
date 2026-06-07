package com.mythicrealm.backend.inventory;

import java.math.BigDecimal;

public record ItemRecord(
    long id,
    long playerId,
    String templateId,
    String name,
    String itemType,
    String quality,
    int requiredLevel,
    int attackBonus,
    int defenseBonus,
    int hpBonus,
    int mpBonus,
    BigDecimal critBonus,
    int sellPrice,
    int enhancementLevel,
    int enhancementLuck
) {
    public String displayName() {
        return enhancementLevel > 0 ? name + " +" + enhancementLevel : name;
    }

    public int enhancedAttackBonus() {
        return enhancedValue(attackBonus);
    }

    public int enhancedDefenseBonus() {
        return enhancedValue(defenseBonus);
    }

    public int enhancedHpBonus() {
        return enhancedValue(hpBonus);
    }

    public int enhancedMpBonus() {
        return enhancedValue(mpBonus);
    }

    public double enhancedCritBonus() {
        double milestone = 0.0;
        if (enhancementLevel >= 10) {
            milestone += 0.01;
        }
        if (enhancementLevel >= 15) {
            milestone += 0.02;
        }
        return critBonus.doubleValue() * enhancementMultiplier() + milestone;
    }

    private int enhancedValue(int value) {
        if (value <= 0) {
            return 0;
        }
        int result = (int) Math.round(value * enhancementMultiplier());
        if (enhancementLevel >= 5) {
            result += Math.max(1, value / 10);
        }
        if (enhancementLevel >= 10) {
            result += Math.max(1, value / 8);
        }
        if (enhancementLevel >= 15) {
            result += Math.max(1, value / 5);
        }
        return result;
    }

    private double enhancementMultiplier() {
        return 1 + enhancementLevel * 0.03;
    }
}
