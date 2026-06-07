package com.mythicrealm.domain.player.model;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 金币值对象
 */
public record Gold(int value) implements ValueObject {

    public Gold {
        if (value < 0) {
            throw new IllegalArgumentException("金币数量不能为负数");
        }
    }

    public static Gold of(int value) {
        return new Gold(value);
    }

    public static Gold zero() {
        return new Gold(0);
    }

    public Gold add(int amount) {
        return new Gold(value + Math.max(0, amount));
    }

    public Gold subtract(int amount) {
        if (value < amount) {
            throw new IllegalArgumentException("金币不足");
        }
        return new Gold(value - amount);
    }

    public boolean canAfford(int cost) {
        return value >= cost;
    }
}
