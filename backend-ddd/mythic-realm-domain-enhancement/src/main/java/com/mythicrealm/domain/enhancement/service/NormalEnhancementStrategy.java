package com.mythicrealm.domain.enhancement.service;

/**
 * 普通强化策略 (7-12级)
 *
 * 特点：
 * - 中等成功率
 * - 失败掉1级
 */
public class NormalEnhancementStrategy implements EnhancementStrategy {

    @Override
    public double calculateBaseSuccessRate(int targetLevel) {
        if (targetLevel <= 9) {
            return 0.6;  // 60% 成功率
        }
        if (targetLevel <= 12) {
            return 0.4;  // 40% 成功率
        }
        throw new IllegalArgumentException("普通策略不支持目标等级: " + targetLevel);
    }

    @Override
    public boolean supports(int targetLevel) {
        return targetLevel >= 7 && targetLevel <= 12;
    }
}
