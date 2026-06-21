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

    @Test
    void chatStreamsOpenImmediatelyAndHideRoutineRobotLedgerLines() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/chat/ChatService.java"));

        assertTrue(source.contains(".name(\"ready\")"));
        assertTrue(source.contains(".name(\"ping\")"));
        assertFalse(source.contains("completeWithError"));
        assertFalse(source.contains("emitter.complete();"));
        assertTrue(source.contains("deliver_at AS created_at"));
        assertTrue(source.contains("text LIKE '刚花 %技能%'"));
        assertTrue(source.contains("text LIKE '领取任务《%'"));
        assertTrue(source.contains("text LIKE '使用《%'"));
        assertTrue(source.contains("text LIKE '合成《%'"));
        assertTrue(source.contains("text LIKE '这轮副本击败 %战力评估更新到 %'"));
        assertTrue(source.contains("text LIKE '换了 %元，补进 %金%'"));
        assertTrue(source.contains("text LIKE '%刚把【%】强化到 +%'"));
        assertTrue(source.contains("text LIKE '%强化【%】失败了%'"));
        assertTrue(source.contains("text LIKE '切换到构筑【%'"));
    }
}
