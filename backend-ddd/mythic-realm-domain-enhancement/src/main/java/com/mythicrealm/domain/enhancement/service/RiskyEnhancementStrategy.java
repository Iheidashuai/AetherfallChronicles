package com.mythicrealm.domain.enhancement.service;

/**
 * 危险强化策略 (13-15级)
 *
 * 特点：
 * - 低成功率
 * - 失败掉2级
 */
public class RiskyEnhancementStrategy implements EnhancementStrategy {

    @Override
    public double calculateBaseSuccessRate(int targetLevel) {
        if (targetLevel >= 13 && targetLevel <= 15) {
            return 0.2;  // 20% 成功率
        }
        throw new IllegalArgumentException("危险策略不支持目标等级: " + targetLevel);
    }

    @Override
    public boolean supports(int targetLevel) {
        return targetLevel >= 13 && targetLevel <= 15;
    }
}
