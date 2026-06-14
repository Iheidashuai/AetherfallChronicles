package com.mythicrealm.api.gameplay.combat;

import org.springframework.stereotype.Service;

@Service
public class CombatPowerService {
    private final DamageCalculator damageCalculator;

    public CombatPowerService(DamageCalculator damageCalculator) {
        this.damageCalculator = damageCalculator;
    }

    public int combatPower(CombatStats stats) {
        return Math.max(1, combatStatPower(stats));
    }

    public int combatPower(CombatStats totalStats, CombatStats baseStats, int equipmentPower) {
        int basePower = basePower(baseStats);
        int rawSynergy = Math.max(0, combatStatPower(totalStats) - combatStatPower(baseStats)) / 30;
        int synergyPower = Math.min(rawSynergy, Math.max(0, equipmentPower) / 4);
        return Math.max(1, basePower + Math.max(0, equipmentPower) + synergyPower);
    }

    public int basePower(CombatStats baseStats) {
        double value = baseStats.level() * 180.0
            + baseStats.maxHp() * 0.40
            + baseStats.maxMp() * 0.15
            + baseStats.attackPower() * 16.0
            + baseStats.armor() * 8.0
            + baseStats.resistance() * 8.0
            + baseStats.speed() * 4.0
            + baseStats.critChance() * 1500.0;
        return Math.max(1, (int) Math.round(value));
    }

    public int combatStatPower(CombatStats stats) {
        CombatStats sameLevelPhysicalTarget = referenceTarget(stats.level(), "physical");
        CombatStats sameLevelMagicTarget = referenceTarget(stats.level(), "magic");
        double expectedPhysical = damageCalculator.expectedDamage(stats, sameLevelPhysicalTarget);
        double expectedMagic = damageCalculator.expectedDamage(stats, sameLevelMagicTarget);
        double expectedDamageVsSameLevel = "magic".equals(stats.damageType()) ? expectedMagic : expectedPhysical;
        double expectedDps = expectedDamageVsSameLevel * (stats.speed() / 100.0) * (1 + stats.critChance() * 0.5);
        double physicalEhp = stats.maxHp() / Math.max(0.10, 1 - damageCalculator.damageReduction(stats.armor(), stats.level()));
        double magicEhp = stats.maxHp() / Math.max(0.10, 1 - damageCalculator.damageReduction(stats.resistance(), stats.level()));
        double mixedEhp = physicalEhp * 0.65 + magicEhp * 0.35;
        return Math.max(1, (int) Math.round(stats.level() * 50 + expectedDps * 55 + mixedEhp * 0.55 + stats.maxMp() * 0.15));
    }

    private CombatStats referenceTarget(int level, String damageType) {
        return new CombatStats(
            level,
            160 + level * 25,
            0,
            1,
            18 + level * 4,
            14 + level * 3,
            100,
            0.88,
            0.04,
            0.05,
            1.50,
            0.03,
            damageType
        );
    }
}
