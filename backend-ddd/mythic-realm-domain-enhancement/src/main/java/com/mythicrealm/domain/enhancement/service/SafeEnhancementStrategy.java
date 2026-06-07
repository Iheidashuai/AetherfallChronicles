package com.mythicrealm.domain.enhancement.service;

/**
 * 安全强化策略 (1-6级)
 *
 * 特点：
 * - 高成功率
 * - 失败不掉级
 */
public class SafeEnhancementStrategy implements EnhancementStrategy {

    @Override
    public double calculateBaseSuccessRate(int targetLevel) {
        if (targetLevel <= 3) {
            return 1.0;  // 100% 成功率
        }
        if (targetLevel <= 6) {
            return 0.8;  // 80% 成功率
        }
        throw new IllegalArgumentException("安全策略不支持目标等级: " + targetLevel);
    }

    @Override
    public boolean supports(int targetLevel) {
        return targetLevel >= 1 && targetLevel <= 6;
    }
}
