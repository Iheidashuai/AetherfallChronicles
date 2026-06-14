package com.mythicrealm.api.gameplay.combat;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class CombatStatsService {
    public CombatStats playerStats(PlayerRecord player, Collection<ItemRecord> equipment) {
        EquipmentStats gear = EquipmentStats.from(equipment);
        int maxHp = round(160 + player.level() * 24 + player.constitution() * 20 + player.strength() * 3 + gear.hp());
        int maxMp = round(60 + player.level() * 8 + player.intelligence() * 10 + player.spirit() * 6 + gear.mp());
        int attackPower = switch (player.profession()) {
            case "ranger" -> round(player.level() * 6 + player.agility() * 3.4 + player.strength() * 2.0 + gear.attack());
            case "mage" -> round(player.level() * 6 + player.intelligence() * 4.2 + player.spirit() * 1.2 + gear.attack());
            default -> round(player.level() * 6 + player.strength() * 4.2 + player.constitution() * 0.8 + gear.attack());
        };
        int armor = round(player.level() * 3 + player.constitution() * 3.5 + player.strength() * 0.8 + gear.armor());
        int resistance = round(player.level() * 2 + player.spirit() * 3.5 + player.intelligence() * 1.0 + gear.resistance());
        int speed = round(100 + player.agility() * 1.5);
        double accuracy = DamageCalculator.clamp(0.84 + player.agility() * 0.0015 + player.level() * 0.001, 0.78, 0.98);
        double evasion = DamageCalculator.clamp(0.02 + player.agility() * 0.0012 + player.level() * 0.0005, 0.02, 0.20);
        double critChance = DamageCalculator.clamp(0.05 + player.agility() * 0.0012 + gear.crit(), 0.05, 0.42);
        double critResist = DamageCalculator.clamp(player.spirit() * 0.0008, 0.00, 0.18);
        String damageType = "mage".equals(player.profession()) ? "magic" : "physical";
        return new CombatStats(
            player.level(),
            maxHp,
            maxMp,
            attackPower,
            armor,
            resistance,
            speed,
            accuracy,
            evasion,
            critChance,
            1.50,
            critResist,
            damageType
        );
    }

    public CombatStats monsterStats(MonsterConfig monster) {
        return new CombatStats(
            monster.level(),
            monster.maxHP(),
            0,
            monster.attackPower(),
            monster.armor(),
            monster.resistance(),
            monster.speed(),
            monster.accuracy(),
            monster.evasion(),
            monster.critChance(),
            1.50,
            monster.critResist(),
            monster.damageType()
        );
    }

    public Combatant playerCombatant(PlayerRecord player, Collection<ItemRecord> equipment) {
        return new Combatant(
            Long.toString(player.id()),
            player.name(),
            "player",
            player.profession(),
            "none",
            false,
            playerStats(player, equipment)
        );
    }

    public Combatant monsterCombatant(MonsterConfig monster) {
        return new Combatant(
            monster.id(),
            monster.name(),
            "enemy",
            monster.archetype(),
            monster.mechanic(),
            monster.isBoss(),
            monsterStats(monster)
        );
    }

    public double roomRecoveryRate(PlayerRecord player) {
        return DamageCalculator.clamp(0.04 + player.spirit() * 0.0006, 0.04, 0.10);
    }

    private int round(double value) {
        return Math.max(1, (int) Math.round(value));
    }

    private record EquipmentStats(int attack, int armor, int resistance, int hp, int mp, double crit) {
        static EquipmentStats from(Collection<ItemRecord> items) {
            if (items == null || items.isEmpty()) {
                return new EquipmentStats(0, 0, 0, 0, 0, 0);
            }
            return new EquipmentStats(
                items.stream().mapToInt(ItemRecord::enhancedAttackBonus).sum(),
                items.stream().mapToInt(ItemRecord::enhancedDefenseBonus).sum(),
                items.stream().mapToInt(ItemRecord::enhancedResistanceBonus).sum(),
                items.stream().mapToInt(ItemRecord::enhancedHpBonus).sum(),
                items.stream().mapToInt(ItemRecord::enhancedMpBonus).sum(),
                items.stream().mapToDouble(ItemRecord::enhancedCritBonus).sum()
            );
        }
    }
}
