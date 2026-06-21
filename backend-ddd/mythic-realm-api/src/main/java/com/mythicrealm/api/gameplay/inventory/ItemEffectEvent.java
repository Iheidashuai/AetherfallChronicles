package com.mythicrealm.api.gameplay.inventory;

public record ItemEffectEvent(String type, String targetId, int amount) {
    public static ItemEffectEvent of(String type) {
        return new ItemEffectEvent(type, null, 1);
    }
}
