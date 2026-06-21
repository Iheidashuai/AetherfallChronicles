package com.mythicrealm.api.gameplay.ai;

import org.springframework.stereotype.Service;

@Service
public class AiSocialEventService {
    private final AiChatInteractionService aiChatInteractionService;

    public AiSocialEventService(AiChatInteractionService aiChatInteractionService) {
        this.aiChatInteractionService = aiChatInteractionService;
    }

    public void worldHighlight(String kind, String actorName, String text, int priority) {
        if (priority < 2) {
            return;
        }
        aiChatInteractionService.enqueueSocialEvent(
            "world",
            "world",
            "event_world_" + safeKind(kind),
            text,
            null,
            actorName
        );
    }

    public void guildBoss(long guildId, String text) {
        guildEvent(guildId, "event_guild_boss", text);
    }

    public void guildLevel(long guildId, String text) {
        guildEvent(guildId, "event_guild_level", text);
    }

    public void guildDonation(long guildId, String text) {
        guildEvent(guildId, "event_guild_donation", text);
    }

    private void guildEvent(long guildId, String triggerKind, String text) {
        aiChatInteractionService.enqueueSocialEvent(
            "guild",
            "guild:" + guildId,
            triggerKind,
            text,
            null,
            null
        );
    }

    private String safeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "highlight";
        }
        return kind.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
