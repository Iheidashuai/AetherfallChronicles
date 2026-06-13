package com.mythicrealm.domain.player.model;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 玩家属性值对象
 */
public record PlayerStats(
    int strength,
    int agility,
    int constitution,
    int intelligence,
    int spirit,
    int freePoints
) implements ValueObject {

    public PlayerStats {
        if (strength < 0 || agility < 0 || constitution < 0 || intelligence < 0 || spirit < 0 || freePoints < 0) {
            throw new IllegalArgumentException("属性值不能为负数");
        }
    }

    public static PlayerStats fromProfession(Profession profession) {
        return new PlayerStats(
            profession.getBaseStrength(),
            profession.getBaseAgility(),
            profession.getBaseConstitution(),
            profession.getBaseIntelligence(),
            profession.getBaseSpirit(),
            0
        );
    }

    /**
     * 升级时增加属性
     */
    public PlayerStats onLevelUp(Profession profession) {
        int newStrength = strength + 1;
        int newAgility = agility + 1;
        int newConstitution = constitution + 1;
        int newIntelligence = intelligence + 1;
        int newSpirit = spirit + 1;
        int newFreePoints = freePoints + 3;

        // 根据职业额外加点
        switch (profession) {
            case WARRIOR:
                newStrength++;
                newConstitution++;
                break;
            case RANGER:
                newAgility++;
                newStrength++;
                break;
            case MAGE:
                newIntelligence++;
                newSpirit++;
                break;
        }

        return new PlayerStats(newStrength, newAgility, newConstitution,
                             newIntelligence, newSpirit, newFreePoints);
    }

    /**
     * 计算最大生命值
     */
    public double calculateMaxHp(Level level) {
        return 100 + constitution * 10 * (1 + level.value() * 0.1);
    }

    /**
     * 计算最大魔法值
     */
    public double calculateMaxMp(Level level) {
        return 50 + intelligence * 8 * (1 + level.value() * 0.08);
    }

    /**
     * 计算攻击力
     */
    public double calculateAttack(Level level) {
        return strength * 2.0 + level.value() * 3.0;
    }

    /**
     * 计算防御力
     */
    public double calculateDefense(Level level) {
        return constitution * 2.0 + level.value() * 1.5;
    }
}
