package com.mythicrealm.api.gameplay.player;

public record PlayerRecord(
    long id,
    long accountId,
    String name,
    String profession,
    int level,
    int experience,
    int gold,
    int strength,
    int agility,
    int constitution,
    int intelligence,
    int spirit,
    int freePoints
) {
    public double maxHp() {
        return 100 + constitution * 10 * (1 + level * 0.1);
    }

    public double maxMp() {
        return 50 + intelligence * 8 * (1 + level * 0.08);
    }

    public double attack() {
        return strength * 2.0 + level * 3.0;
    }

    public double defense() {
        return constitution * 2.0 + level * 1.5;
    }
}
