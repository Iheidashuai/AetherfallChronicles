package com.mythicrealm.domain.player.model;

/**
 * 职业枚举
 */
public enum Profession {
    WARRIOR("warrior", 10, 5, 8, 3, 4),
    RANGER("ranger", 6, 10, 5, 4, 5),
    MAGE("mage", 3, 4, 4, 10, 9);

    private final String code;
    private final int baseStrength;
    private final int baseAgility;
    private final int baseConstitution;
    private final int baseIntelligence;
    private final int baseSpirit;

    Profession(String code, int baseStrength, int baseAgility, int baseConstitution,
               int baseIntelligence, int baseSpirit) {
        this.code = code;
        this.baseStrength = baseStrength;
        this.baseAgility = baseAgility;
        this.baseConstitution = baseConstitution;
        this.baseIntelligence = baseIntelligence;
        this.baseSpirit = baseSpirit;
    }

    public String getCode() {
        return code;
    }

    public int getBaseStrength() {
        return baseStrength;
    }

    public int getBaseAgility() {
        return baseAgility;
    }

    public int getBaseConstitution() {
        return baseConstitution;
    }

    public int getBaseIntelligence() {
        return baseIntelligence;
    }

    public int getBaseSpirit() {
        return baseSpirit;
    }

    public static Profession fromCode(String code) {
        if (code == null) {
            return WARRIOR;
        }
        for (Profession profession : values()) {
            if (profession.code.equalsIgnoreCase(code)) {
                return profession;
            }
        }
        throw new IllegalArgumentException("暂不支持该职业: " + code);
    }
}
