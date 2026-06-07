package com.mythicrealm.domain.equipment;

import com.mythicrealm.common.domain.ValueObject;
import java.math.BigDecimal;

/**
 * 装备属性值对象
 */
public record EquipmentStats(
    int attackBonus,
    int defenseBonus,
    int hpBonus,
    int mpBonus,
    BigDecimal critBonus,
    int enhancementLevel
) implements ValueObject {

    public EquipmentStats {
        if (attackBonus < 0) {
            throw new IllegalArgumentException("攻击加成不能为负");
        }
        if (defenseBonus < 0) {
            throw new IllegalArgumentException("防御加成不能为负");
        }
        if (hpBonus < 0) {
            throw new IllegalArgumentException("生命加成不能为负");
        }
        if (mpBonus < 0) {
            throw new IllegalArgumentException("法力加成不能为负");
        }
        if (critBonus == null || critBonus.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("暴击加成不能为负");
        }
        if (enhancementLevel < 0 || enhancementLevel > 15) {
            throw new IllegalArgumentException("强化等级必须在 0-15 之间");
        }
    }

    public static EquipmentStats create(
        int attackBonus,
        int defenseBonus,
        int hpBonus,
        int mpBonus,
        BigDecimal critBonus
    ) {
        return new EquipmentStats(attackBonus, defenseBonus, hpBonus, mpBonus, critBonus, 0);
    }

    /**
     * 获取强化后的攻击加成
     */
    public int enhancedAttackBonus() {
        return enhancedValue(attackBonus);
    }

    /**
     * 获取强化后的防御加成
     */
    public int enhancedDefenseBonus() {
        return enhancedValue(defenseBonus);
    }

    /**
     * 获取强化后的生命加成
     */
    public int enhancedHpBonus() {
        return enhancedValue(hpBonus);
    }

    /**
     * 获取强化后的法力加成
     */
    public int enhancedMpBonus() {
        return enhancedValue(mpBonus);
    }

    /**
     * 获取强化后的暴击加成
     */
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
