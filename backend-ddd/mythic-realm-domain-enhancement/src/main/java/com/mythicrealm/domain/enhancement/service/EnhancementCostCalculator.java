package com.mythicrealm.domain.enhancement.service;

/**
 * 强化成本计算器
 *
 * 根据装备等级和目标强化等级计算强化消耗
 */
public class EnhancementCostCalculator {

    /**
     * 计算强化成本
     *
     * @param requiredLevel 装备需求等级
     * @param targetEnhancementLevel 目标强化等级
     * @return 强化成本（金币）
     */
    public int calculate(int requiredLevel, int targetEnhancementLevel) {
        // 成本公式: 装备等级² × 目标强化等级 × 10
        int normalizedRequiredLevel = Math.max(1, requiredLevel);
        return normalizedRequiredLevel * normalizedRequiredLevel * targetEnhancementLevel * 10;
    }
}
