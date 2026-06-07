package com.mythicrealm.domain.enhancement.valueobject;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 强化等级值对象
 */
public record EnhancementLevel(int value) implements ValueObject {
    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 15;

    public EnhancementLevel {
        if (value < MIN_LEVEL || value > MAX_LEVEL) {
            throw new IllegalArgumentException("强化等级必须在 " + MIN_LEVEL + " 到 " + MAX_LEVEL + " 之间");
        }
    }

    public static EnhancementLevel of(int value) {
        return new EnhancementLevel(value);
    }

    public static EnhancementLevel initial() {
        return new EnhancementLevel(MIN_LEVEL);
    }

    public boolean isMaxLevel() {
        return value == MAX_LEVEL;
    }

    public EnhancementLevel increment() {
        if (isMaxLevel()) {
            throw new IllegalStateException("已达到最大强化等级");
        }
        return new EnhancementLevel(value + 1);
    }

    public EnhancementLevel decrement() {
        if (value == MIN_LEVEL) {
            return this;
        }
        return new EnhancementLevel(value - 1);
    }

    public EnhancementLevel decrementBy(int amount) {
        return new EnhancementLevel(Math.max(MIN_LEVEL, value - amount));
    }

    /**
     * 判断是否在安全强化区间 (1-6级)
     */
    public boolean isSafeZone() {
        return value >= 1 && value <= 6;
    }

    /**
     * 判断是否在普通强化区间 (7-12级)
     */
    public boolean isNormalZone() {
        return value >= 7 && value <= 12;
    }

    /**
     * 判断是否在危险强化区间 (13-15级)
     */
    public boolean isRiskyZone() {
        return value >= 13 && value <= 15;
    }
}
