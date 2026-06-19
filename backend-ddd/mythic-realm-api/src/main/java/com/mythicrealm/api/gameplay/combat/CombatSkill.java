package com.mythicrealm.api.gameplay.combat;

public record CombatSkill(
    String id,
    String name,
    int rank,
    int unlockLevel,
    int maxRank,
    String category,
    String targetType,
    String damageType,
    double multiplier,
    int cooldown,
    int mpCost,
    String effectType,
    double effectPower,
    int durationRounds,
    String triggerKind,
    int priority,
    String visualKey
) {
    public boolean damaging() {
        return multiplier > 0;
    }

    public boolean healing() {
        return "heal".equals(effectType);
    }

    public boolean shielding() {
        return "shield".equals(effectType);
    }
}
