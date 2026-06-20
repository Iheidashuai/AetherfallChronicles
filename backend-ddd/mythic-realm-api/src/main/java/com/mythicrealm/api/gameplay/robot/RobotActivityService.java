package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RobotActivityService {
    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final RobotBrainService robotBrainService;
    private final RobotActionSupport robotActionSupport;
    private final RobotSimulationProperties properties;
    private final Random random = new Random();

    public RobotActivityService(
        JdbcTemplate jdbcTemplate,
        InventoryService inventoryService,
        RobotBrainService robotBrainService,
        RobotActionSupport robotActionSupport,
        RobotSimulationProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.robotBrainService = robotBrainService;
        this.robotActionSupport = robotActionSupport;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 6_000, fixedDelay = 15_000)
    public void simulateTick() {
        if (!hasHumanPlayer()) {
            return;
        }
        List<RobotAgent> robots = jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, real_money,
                   wealth_tier_level, wealth_tier, strength, agility, constitution, intelligence,
                   spirit, free_points, personality, personality_archetype, dungeon_clears,
                   peak_enhancement, legendary_loot_count, current_activity_kind,
                   current_activity_text, current_activity_at, last_activity_at
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY RAND()
            LIMIT ?
            """,
            (rs, rowNum) -> mapRobot(rs),
            properties.getRobotsPerTick()
        );
        if (robots.isEmpty()) {
            return;
        }

        int baseCount = properties.getMinActionsPerTick() + random.nextInt(Math.max(1, properties.getExtraActionRange()));
        int scaledCount = (int) Math.round(baseCount * activityFactor(LocalTime.now()));
        int actionCount = Math.min(robots.size(), Math.max(1, scaledCount));
        for (int index = 0; index < actionCount; index++) {
            RobotAgent actor = robots.get(index);
            RobotAgent target = robots.get((index + 1 + random.nextInt(robots.size())) % robots.size());
            robotBrainService.thinkAndAct(actor, target);
        }
        robotActionSupport.trimChat();
    }

    /**
     * Time-of-day multiplier on how many robots act this tick, so the simulated
     * population has believable peaks and lulls instead of flat 24/7 activity:
     * quiet overnight, busy in the evening, normal during the day.
     */
    private double activityFactor(LocalTime now) {
        int hour = now.getHour();
        if (hour >= 1 && hour < 7) {
            return 0.4; // overnight lull
        }
        if (hour >= 19 && hour < 24) {
            return 1.2; // evening prime time
        }
        if (hour >= 12 && hour < 14) {
            return 1.05; // lunch bump
        }
        return 0.85; // daytime baseline
    }

    private boolean hasHumanPlayer() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player WHERE account_id IS NOT NULL",
            Integer.class
        );
        return count != null && count > 0;
    }

    private RobotAgent mapRobot(ResultSet rs) throws SQLException {
        PlayerRecord player = new PlayerRecord(
            rs.getLong("id"),
            rs.getObject("account_id") == null ? 0 : rs.getLong("account_id"),
            rs.getString("name"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getInt("experience"),
            rs.getLong("gold"),
            rs.getLong("real_money"),
            rs.getInt("wealth_tier_level"),
            rs.getString("wealth_tier"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
        return new RobotAgent(
            player,
            rs.getString("title"),
            rs.getString("personality"),
            RobotArchetype.resolve(rs.getString("personality_archetype"), rs.getString("personality")),
            inventoryService.combatPower(player),
            rs.getInt("dungeon_clears"),
            rs.getInt("peak_enhancement"),
            rs.getInt("legendary_loot_count"),
            rs.getString("current_activity_kind"),
            rs.getString("current_activity_text"),
            timestampOrNow(rs, "current_activity_at"),
            timestampOrNow(rs, "last_activity_at")
        );
    }

    private Instant timestampOrNow(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? Instant.now() : value.toInstant();
    }
}
