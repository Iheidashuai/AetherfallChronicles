package com.mythicrealm.api.gameplay.worldevent;

import java.util.Map;

public record WorldEventAction(
    String label,
    String targetScreen,
    String targetId,
    Map<String, Object> params
) {
    public static WorldEventAction to(String label, String targetScreen) {
        return new WorldEventAction(label, targetScreen, null, Map.of());
    }

    public static WorldEventAction to(String label, String targetScreen, String targetId, Map<String, Object> params) {
        return new WorldEventAction(label, targetScreen, targetId, params == null ? Map.of() : params);
    }
}
