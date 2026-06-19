package com.mythicrealm.api.gameplay.skill;

import com.mythicrealm.api.gameplay.combat.CombatSkill;

public record SkillTemplate(
    String id,
    String name,
    String ownerScope,
    String profession,
    String archetype,
    int unlockLevel,
    int maxRank,
    String category,
    String targetType,
    String damageType,
    double baseMultiplier,
    double rankMultiplierGrowth,
    int cooldown,
    int mpCostBase,
    int mpCostGrowth,
    String effectType,
    double effectPowerBase,
    double effectPowerGrowth,
    int durationRounds,
    String triggerKind,
    int priority,
    String visualKey,
    String description,
    double tierCoef
) {
    public CombatSkill toCombatSkill(int rank) {
        int safeRank = Math.max(1, Math.min(maxRank, rank));
        return new CombatSkill(
            id,
            name,
            safeRank,
            unlockLevel,
            maxRank,
            category,
            targetType,
            damageType,
            baseMultiplier + rankMultiplierGrowth * (safeRank - 1),
            cooldown,
            Math.max(0, mpCostBase + mpCostGrowth * (safeRank - 1)),
            effectType,
            effectPowerBase + effectPowerGrowth * (safeRank - 1),
            durationRounds,
            triggerKind,
            priority,
            visualKey
        );
    }
}
