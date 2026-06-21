package com.mythicrealm.api.gameplay.robot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RobotSpeedSchemaTest {
    @Test
    void schemaStoresRobotSpeedAndAdminActions() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("CREATE TABLE game_setting"));
        assertTrue(schema.contains("('robot.speed.multiplier', '1')"));
        assertTrue(schema.contains("CREATE TABLE admin_action_log"));
        assertTrue(schema.contains("action VARCHAR(64) NOT NULL"));
        assertTrue(schema.contains("CREATE TABLE robot_tick_run"));
        assertTrue(schema.contains("planned_count INT NOT NULL DEFAULT 0"));
        assertTrue(schema.contains("backpressure_active BOOLEAN NOT NULL DEFAULT FALSE"));
    }

    @Test
    void marketNoLongerUsesFixedRobotListingTarget() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/market/MarketService.java"));

        assertFalse(source.contains("ROBOT_LISTING_TARGET"));
        assertFalse(source.contains("ensureRobotListings"));
    }
}
