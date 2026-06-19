package com.mythicrealm.api.gameplay.combat;

import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class DamageCalculator {
    public static final double BASIC_ATTACK_MULTIPLIER = 1.0;

    public DamageResult basicAttack(CombatStats attacker, CombatStats defender, Random random) {
        return attack(attacker, defender, BASIC_ATTACK_MULTIPLIER, 1.0, random);
    }

    public DamageResult attack(
        CombatStats attacker,
        CombatStats defender,
        double skillMultiplier,
        double mechanicMultiplier,
        Random random
    ) {
        return attack(attacker, defender, skillMultiplier, mechanicMultiplier, attacker.damageType(), random);
    }

    public DamageResult attack(
        CombatStats attacker,
        CombatStats defender,
        double skillMultiplier,
        double mechanicMultiplier,
        String damageType,
        Random random
    ) {
        double hitChance = hitChance(attacker, defender);
        if (random.nextDouble() > hitChance) {
            return new DamageResult(false, false, 0, hitChance, 0, skillMultiplier * mechanicMultiplier);
        }
        boolean critical = random.nextDouble() < effectiveCritChance(attacker, defender);
        double critMultiplier = critical ? attacker.critDamage() : 1.0;
        int defense = defender.defenseFor(damageType == null || damageType.isBlank() ? attacker.damageType() : damageType);
        double reduction = damageReduction(defense, attacker.level());
        double variance = 0.94 + random.nextDouble() * 0.12;
        double raw = attacker.attackPower() * skillMultiplier * mechanicMultiplier * (1 - reduction) * variance * critMultiplier;
        return new DamageResult(true, critical, Math.max(1, (int) Math.round(raw)), hitChance, reduction, skillMultiplier * mechanicMultiplier);
    }

    public double expectedDamage(CombatStats attacker, CombatStats defender) {
        double hitChance = hitChance(attacker, defender);
        double critChance = effectiveCritChance(attacker, defender);
        int defense = defender.defenseFor(attacker.damageType());
        double reduction = damageReduction(defense, attacker.level());
        return Math.max(1, attacker.attackPower() * (1 - reduction) * hitChance * (1 + critChance * (attacker.critDamage() - 1)));
    }

    public double hitChance(CombatStats attacker, CombatStats defender) {
        double levelGap = attacker.level() - defender.level();
        return clamp(attacker.accuracy() - defender.evasion() + levelGap * 0.01, 0.70, 0.98);
    }

    public double effectiveCritChance(CombatStats attacker, CombatStats defender) {
        return clamp(attacker.critChance() - defender.critResist(), 0.02, 0.42);
    }

    public double damageReduction(int defense, int attackerLevel) {
        return clamp(defense / (defense + 180.0 + attackerLevel * 22.0), 0.03, 0.72);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
