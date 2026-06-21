package com.mythicrealm.api.gameplay.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChatServiceSpeakerShapeTest {

    @Test
    void chatSpeakersStayLightweightForHistoryAndStreams() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/chat/ChatService.java"));

        assertFalse(source.contains("private final InventoryService"));
        assertFalse(source.contains("private final LeaderboardService"));
        assertFalse(source.contains("private final PlayerService"));
        assertFalse(source.contains("inventoryService."));
        assertFalse(source.contains("leaderboardService."));
        assertFalse(source.contains("playerService."));
        assertFalse(source.contains("equipmentForPlayer("));
        assertFalse(source.contains("equipmentForRobot("));
        assertFalse(source.contains("derivedStatsForPlayer("));
        assertTrue(source.contains("quickPower(PlayerRecord player)"));
    }
}
