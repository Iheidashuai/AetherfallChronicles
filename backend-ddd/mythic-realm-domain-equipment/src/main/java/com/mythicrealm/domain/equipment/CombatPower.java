package com.mythicrealm.domain.equipment;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 战斗力值对象
 */
public record CombatPower(int value) implements ValueObject {

    public CombatPower {
        if (value < 0) {
            throw new IllegalArgumentException("战斗力不能为负");
        }
    }

    public static CombatPower zero() {
        return new CombatPower(0);
    }

    public CombatPower add(CombatPower other) {
        return new CombatPower(this.value + other.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
