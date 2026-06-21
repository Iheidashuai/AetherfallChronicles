package com.mythicrealm.api.gameplay.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiChatSchemaTest {

    @Test
    void chatMessagesCarryAiSchedulingAndTraceFields() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("channel VARCHAR(64) NOT NULL DEFAULT 'world'"));
        assertTrue(schema.contains("deliver_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(schema.contains("ai_generated BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(schema.contains("ai_interaction_id BIGINT NULL"));
        assertTrue(schema.contains("reply_to_message_id BIGINT NULL"));
        assertTrue(schema.contains("KEY idx_chat_visible (channel, deliver_at, id)"));
    }

    @Test
    void aiLedgerAndMemoryTablesAreInLatestSchema() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("CREATE TABLE ai_chat_interaction"));
        assertTrue(schema.contains("CREATE TABLE ai_model_call"));
        assertTrue(schema.contains("request_payload LONGTEXT NULL"));
        assertTrue(schema.contains("raw_response LONGTEXT NULL"));
        assertTrue(schema.contains("token_source VARCHAR(32) NOT NULL DEFAULT 'missing'"));
        assertTrue(schema.contains("CREATE TABLE ai_robot_relationship_memory"));
        assertTrue(schema.contains("UNIQUE KEY uk_ai_relationship (robot_id, player_id)"));
        assertTrue(schema.contains("CREATE TABLE ai_public_memory"));
    }
}
