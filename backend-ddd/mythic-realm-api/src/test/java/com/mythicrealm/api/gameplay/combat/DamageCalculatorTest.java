package com.mythicrealm.api.gameplay.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {
    private final DamageCalculator calculator = new DamageCalculator();

    @Test
    void armorAndResistanceReduceExpectedDamage() {
        CombatStats attacker = stats(20, 220, 40, 40, "physical");
        CombatStats lowArmor = stats(20, 120, 20, 20, "physical");
        CombatStats highArmor = stats(20, 120, 240, 20, "physical");
        CombatStats magicAttacker = stats(20, 220, 40, 40, "magic");
        CombatStats highResistance = stats(20, 120, 20, 240, "physical");

        assertThat(calculator.expectedDamage(attacker, highArmor)).isLessThan(calculator.expectedDamage(attacker, lowArmor));
        assertThat(calculator.expectedDamage(magicAttacker, highResistance)).isLessThan(calculator.expectedDamage(magicAttacker, lowArmor));
    }

    @Test
    void higherAttackRaisesExpectedDamage() {
        CombatStats lowAttack = stats(20, 120, 40, 40, "physical");
        CombatStats highAttack = stats(20, 260, 40, 40, "physical");
        CombatStats defender = stats(20, 120, 90, 80, "physical");

        assertThat(calculator.expectedDamage(highAttack, defender)).isGreaterThan(calculator.expectedDamage(lowAttack, defender));
    }

    @Test
    void hitAndCritChancesAreClamped() {
        CombatStats precise = new CombatStats(30, 500, 100, 200, 50, 50, 100, 2.0, 0.0, 0.9, 1.5, 0.0, "physical");
        CombatStats evasive = new CombatStats(30, 500, 100, 100, 50, 50, 100, 0.1, 0.9, 0.01, 1.5, 0.8, "physical");

        assertThat(calculator.hitChance(precise, evasive)).isEqualTo(0.98);
        assertThat(calculator.hitChance(evasive, precise)).isEqualTo(0.70);
        assertThat(calculator.effectiveCritChance(precise, evasive)).isCloseTo(0.10, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(calculator.effectiveCritChance(evasive, precise)).isEqualTo(0.02);
    }

    @Test
    void balancedCombatDoesNotDegenerateToOneDamage() {
        CombatStats attacker = stats(12, 155, 42, 36, "physical");
        CombatStats defender = stats(12, 110, 65, 48, "physical");

        int maxDamage = 0;
        for (int i = 0; i < 40; i++) {
            maxDamage = Math.max(maxDamage, calculator.basicAttack(attacker, defender, new Random(i)).damage());
        }

        assertThat(maxDamage).isGreaterThan(20);
    }

    private CombatStats stats(int level, int attack, int armor, int resistance, String damageType) {
        return new CombatStats(level, 500, 120, attack, armor, resistance, 100, 0.92, 0.04, 0.10, 1.5, 0.03, damageType);
    }
}
