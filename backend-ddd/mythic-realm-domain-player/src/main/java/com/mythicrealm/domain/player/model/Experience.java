package com.mythicrealm.domain.player.model;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 经验值对象
 */
public record Experience(int value) implements ValueObject {

    public Experience {
        if (value < 0) {
            throw new IllegalArgumentException("经验值不能为负数");
        }
    }

    public static Experience of(int value) {
        return new Experience(value);
    }

    public static Experience zero() {
        return new Experience(0);
    }

    public Experience add(int amount) {
        return new Experience(value + Math.max(0, amount));
    }

    public Experience subtract(int amount) {
        int newValue = value - amount;
        return new Experience(Math.max(0, newValue));
    }

    public boolean canLevelUp(Level level) {
        return value >= level.experienceRequired();
    }
}
