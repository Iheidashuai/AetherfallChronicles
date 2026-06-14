package com.mythicrealm.api.gameplay.combat;

public record CombatStats(
    int level,
    int maxHp,
    int maxMp,
    int attackPower,
    int armor,
    int resistance,
    int speed,
    double accuracy,
    double evasion,
    double critChance,
    double critDamage,
    double critResist,
    String damageType
) {
    public int defenseFor(String incomingDamageType) {
        return "magic".equals(incomingDamageType) ? resistance : armor;
    }
}
