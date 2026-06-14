package com.mythicrealm.api.gameplay.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatPowerServiceTest {
    private final DamageCalculator damageCalculator = new DamageCalculator();
    private final CombatPowerService powerService = new CombatPowerService(damageCalculator);
    private final CombatStatsService statsService = new CombatStatsService();

    @Test
    void increasingUsefulStatsNeverLowersPower() {
        CombatStats base = stats(10, 100, 80, 60, 120, 100);
        CombatStats moreAttack = stats(10, 120, 80, 60, 120, 100);
        CombatStats moreArmor = stats(10, 100, 95, 60, 120, 100);
        CombatStats moreResistance = stats(10, 100, 80, 75, 120, 100);
        CombatStats moreHp = stats(10, 100, 80, 60, 150, 100);

        int basePower = powerService.combatPower(base);
        assertThat(powerService.combatPower(moreAttack)).isGreaterThanOrEqualTo(basePower);
        assertThat(powerService.combatPower(moreArmor)).isGreaterThanOrEqualTo(basePower);
        assertThat(powerService.combatPower(moreResistance)).isGreaterThanOrEqualTo(basePower);
        assertThat(powerService.combatPower(moreHp)).isGreaterThanOrEqualTo(basePower);
    }

    @Test
    void levelOneNakedProfessionsStayClose() {
        int warrior = powerService.combatPower(statsService.playerStats(player("warrior", 10, 5, 8, 3, 4), List.of()));
        int ranger = powerService.combatPower(statsService.playerStats(player("ranger", 6, 10, 5, 4, 5), List.of()));
        int mage = powerService.combatPower(statsService.playerStats(player("mage", 3, 4, 4, 10, 9), List.of()));
        int max = Math.max(warrior, Math.max(ranger, mage));
        int min = Math.min(warrior, Math.min(ranger, mage));

        assertThat((max - min) / (double) max).isLessThan(0.15);
    }

    @Test
    void starterEquipmentRaisesPowerAboveFirstDungeonFloor() {
        PlayerRecord warrior = player("warrior", 10, 5, 8, 3, 4);
        ItemRecord weapon = item(14, 1, 1, 10, 2, 0.0166);
        ItemRecord armor = item(2, 14, 9, 86, 3, 0.0018);
        ItemRecord helmet = item(2, 5, 3, 33, 4, 0.0034);

        int power = powerService.combatPower(statsService.playerStats(warrior, List.of(weapon, armor, helmet)));

        assertThat(power).isGreaterThan(1000);
    }

    @Test
    void equipmentPowerDominatesGearedCombatPower() {
        CombatStats base = stats(60, 640, 340, 320, 2500, 1300);
        CombatStats geared = stats(60, 1200, 1300, 1050, 9500, 6200);
        int equipmentPower = 45_000;
        int basePower = powerService.basePower(base);
        int totalPower = powerService.combatPower(geared, base, equipmentPower);
        int synergyPower = totalPower - basePower - equipmentPower;

        assertThat(synergyPower).isBetween(0, equipmentPower / 4);
        assertThat((equipmentPower + synergyPower) / (double) totalPower).isGreaterThan(0.60);
    }

    private CombatStats stats(int level, int attack, int armor, int resistance, int hp, int mp) {
        return new CombatStats(level, hp, mp, attack, armor, resistance, 100, 0.90, 0.04, 0.10, 1.5, 0.03, "physical");
    }

    private PlayerRecord player(String profession, int strength, int agility, int constitution, int intelligence, int spirit) {
        return new PlayerRecord(1, 1, profession, profession, 1, 0, 100, strength, agility, constitution, intelligence, spirit, 0);
    }

    private ItemRecord item(int attack, int defense, int resistance, int hp, int mp, double crit) {
        return new ItemRecord(1, 1, "template", "item", "weapon", "rare", 1, attack, defense, resistance, hp, mp, BigDecimal.valueOf(crit), 1, 0, 0);
    }
}
