package com.mythicrealm.domain.enhancement.valueobject;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 强化幸运值值对象
 */
public record EnhancementLuck(int value) implements ValueObject {
    public static final int MIN_LUCK = 0;
    public static final double LUCK_BONUS_RATE = 0.05; // 每点幸运值增加5%成功率

    public EnhancementLuck {
        if (value < MIN_LUCK) {
            throw new IllegalArgumentException("幸运值不能小于 " + MIN_LUCK);
        }
    }

    public static EnhancementLuck of(int value) {
        return new EnhancementLuck(value);
    }

    public static EnhancementLuck initial() {
        return new EnhancementLuck(MIN_LUCK);
    }

    public EnhancementLuck increment() {
        return new EnhancementLuck(value + 1);
    }

    public EnhancementLuck reset() {
        return initial();
    }

    /**
     * 计算幸运值带来的成功率加成
     */
    public double calculateBonus() {
        return value * LUCK_BONUS_RATE;
    }
}
