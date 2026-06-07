package com.mythicrealm.domain.enhancement.service;

import com.mythicrealm.domain.enhancement.model.Enhancement;

/**
 * 强化策略接口
 *
 * 不同等级区间使用不同的强化策略
 */
public interface EnhancementStrategy {
    /**
     * 计算基础成功率
     *
     * @param targetLevel 目标等级
     * @return 基础成功率 (0.0 - 1.0)
     */
    double calculateBaseSuccessRate(int targetLevel);

    /**
     * 判断是否支持该目标等级
     *
     * @param targetLevel 目标等级
     * @return 是否支持
     */
    boolean supports(int targetLevel);

    /**
     * 计算最终成功率（基础成功率 + 幸运值加成）
     *
     * @param enhancement 强化对象
     * @return 最终成功率
     */
    default double calculateFinalSuccessRate(Enhancement enhancement) {
        int targetLevel = enhancement.getLevelValue() + 1;
        double baseRate = calculateBaseSuccessRate(targetLevel);
        double luckBonus = enhancement.getLuck().calculateBonus();
        return Math.min(1.0, baseRate + luckBonus);
    }
}
