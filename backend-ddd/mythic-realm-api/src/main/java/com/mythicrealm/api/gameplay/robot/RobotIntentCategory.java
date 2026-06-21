package com.mythicrealm.api.gameplay.robot;

public enum RobotIntentCategory {
    COMBAT("combat"),
    EQUIPMENT("equipment"),
    MARKET("market"),
    LIGHT("light"),
    WORLD_CHAT("world-chat");

    private final String wireName;

    RobotIntentCategory(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static RobotIntentCategory fromActionKey(String key) {
        if (key == null || key.isBlank()) {
            return LIGHT;
        }
        return switch (key) {
            case "dungeon", "dungeon_sweep", "rift_run", "arena", "guild_boss" -> COMBAT;
            case "enhance", "processing_ascend", "processing_reforge", "processing_socket", "build", "rift_refine" -> EQUIPMENT;
            case "market_buy", "market_supply" -> MARKET;
            case "chat" -> WORLD_CHAT;
            default -> LIGHT;
        };
    }
}
