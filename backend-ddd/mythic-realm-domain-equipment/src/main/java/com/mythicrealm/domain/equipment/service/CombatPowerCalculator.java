package com.mythicrealm.domain.equipment.service;

import com.mythicrealm.domain.equipment.CombatPower;
import com.mythicrealm.domain.equipment.EquipmentStats;
import java.util.Collection;

/**
 * 战斗力计算领域服务
 */
public class CombatPowerCalculator {

    /**
     * 计算玩家战斗力
     *
     * @param playerStats 玩家基础属性
     * @param equipmentStats 所有装备属性列表
     * @return 总战斗力
     */
    public CombatPower calculate(PlayerStats playerStats, Collection<EquipmentStats> equipmentStats) {
        // 计算装备总属性
        int totalAttack = equipmentStats.stream().mapToInt(EquipmentStats::enhancedAttackBonus).sum();
        int totalDefense = equipmentStats.stream().mapToInt(EquipmentStats::enhancedDefenseBonus).sum();
        int totalHp = equipmentStats.stream().mapToInt(EquipmentStats::enhancedHpBonus).sum();
        int totalMp = equipmentStats.stream().mapToInt(EquipmentStats::enhancedMpBonus).sum();
        double totalCrit = equipmentStats.stream().mapToDouble(EquipmentStats::enhancedCritBonus).sum();

        // 计算最终属性
        double attack = playerStats.attack() + totalAttack;
        double defense = playerStats.defense() + totalDefense;
        double hp = playerStats.maxHp() + totalHp;
        double mp = playerStats.maxMp() + totalMp;
        double critRate = Math.min(0.45, playerStats.agility() * 0.001 + totalCrit);
        double equippedSlots = equipmentStats.size();

        // 计算各项得分
        double offenseScore = attack * 12;
        double defenseScore = defense * 8;
        double healthScore = Math.sqrt(Math.max(1, hp)) * 26;
        double manaScore = Math.sqrt(Math.max(1, mp)) * 12;
        double critScore = offenseScore * critRate * 0.8;
        double levelScore = playerStats.level() * 45.0;
        double slotSetBonus = 1 + Math.min(0.10, equippedSlots * 0.008);

        int totalPower = (int) ((offenseScore + defenseScore + healthScore + manaScore + critScore + levelScore) * slotSetBonus);
        return new CombatPower(totalPower);
    }

    /**
     * 玩家基础属性
     */
    public record PlayerStats(
        int level,
        int attack,
        int defense,
        int maxHp,
        int maxMp,
        int agility
    ) {
        public PlayerStats {
            if (level <= 0) {
                throw new IllegalArgumentException("等级必须大于0");
            }
            if (attack < 0) {
                throw new IllegalArgumentException("攻击力不能为负");
            }
            if (defense < 0) {
                throw new IllegalArgumentException("防御力不能为负");
            }
            if (maxHp <= 0) {
                throw new IllegalArgumentException("最大生命值必须大于0");
            }
            if (maxMp < 0) {
                throw new IllegalArgumentException("最大法力值不能为负");
            }
            if (agility < 0) {
                throw new IllegalArgumentException("敏捷不能为负");
            }
        }
    }
}
