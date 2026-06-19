package com.mythicrealm.api.gameplay.endgame.combat;

import java.util.List;
import java.util.Map;

public record BuildCombatPlan(
    long buildId,
    String name,
    String strategy,
    List<PlannedSkill> skills,
    Map<String, Double> talentBonuses
) {
    public static BuildCombatPlan empty() {
        return new BuildCombatPlan(0, "职业默认", "balanced", List.of(), Map.of());
    }

    public double bonus(String key) {
        return talentBonuses == null ? 0 : talentBonuses.getOrDefault(key, 0.0);
    }

    public record PlannedSkill(
        String skillId,
        String name,
        String triggerKind,
        String damageType,
        double multiplier,
        int cooldown,
        int mpCost,
        String effectType,
        double effectPower,
        int durationRounds,
        int priority
    ) {
        public boolean damaging() {
            return multiplier > 0 && ("damage".equals(effectType) || effectType == null || effectType.isBlank());
        }

        public boolean healing() {
            return "heal".equals(effectType);
        }

        public boolean shielding() {
            return "shield".equals(effectType);
        }
    }
}
