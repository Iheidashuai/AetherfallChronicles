package com.mythicrealm.api.gameplay.combat;

public record Combatant(
    String id,
    String name,
    String side,
    String archetype,
    String mechanic,
    boolean boss,
    CombatStats stats
) {
    public boolean player() {
        return "player".equals(side);
    }
}
