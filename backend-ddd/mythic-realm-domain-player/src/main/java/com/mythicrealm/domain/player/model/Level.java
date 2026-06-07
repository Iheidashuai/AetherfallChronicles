package com.mythicrealm.domain.player.model;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 等级值对象
 */
public record Level(int value) implements ValueObject {

    public static final int MAX_LEVEL = 90;
    public static final int MIN_LEVEL = 1;

    public Level {
        if (value < MIN_LEVEL || value > MAX_LEVEL) {
            throw new IllegalArgumentException("等级必须在 " + MIN_LEVEL + " 到 " + MAX_LEVEL + " 之间");
        }
    }

    public static Level of(int value) {
        return new Level(value);
    }

    public static Level initial() {
        return new Level(MIN_LEVEL);
    }

    public boolean canLevelUp() {
        return value < MAX_LEVEL;
    }

    public Level levelUp() {
        if (!canLevelUp()) {
            throw new IllegalStateException("已达到最高等级");
        }
        return new Level(value + 1);
    }

    public boolean isMaxLevel() {
        return value >= MAX_LEVEL;
    }

    /**
     * 计算升级所需经验
     */
    public int experienceRequired() {
        if (value >= MAX_LEVEL) {
            return 0;
        }
        return (int) (100 * Math.pow(value, 1.8));
    }
}
