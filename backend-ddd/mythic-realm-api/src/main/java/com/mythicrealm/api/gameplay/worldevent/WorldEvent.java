package com.mythicrealm.api.gameplay.worldevent;

public record WorldEvent(
    String id,
    String type,
    int priority,
    String title,
    String summary,
    String relevance,
    String rewardHint,
    String freshness,
    String tone,
    WorldEventAction action
) {
}
