package com.mythicrealm.api.gameplay.combat;

public record DamageResult(
    boolean hit,
    boolean critical,
    int damage,
    double hitChance,
    double reduction,
    double multiplier
) {
    public String eventType() {
        if (!hit) {
            return "miss";
        }
        return critical ? "crit" : "hit";
    }
}
