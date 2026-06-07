package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RobotActivityService {
    private static final int ROBOTS_PER_TICK = 24;
    private static final int MIN_ACTIONS_PER_TICK = 14;
    private static final int EXTRA_ACTION_RANGE = 7;

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final RobotBrainService robotBrainService;
    private final RobotActionSupport robotActionSupport;
    private final Random random = new Random();

    public RobotActivityService(
        JdbcTemplate jdbcTemplate,
        InventoryService inventoryService,
        RobotBrainService robotBrainService,
        RobotActionSupport robotActionSupport
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.robotBrainService = robotBrainService;
        this.robotActionSupport = robotActionSupport;
    }

    @Scheduled(initialDelay = 6_000, fixedDelay = 15_000)
    public void simulateTick() {
        List<RobotAgent> robots = jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, real_money,
                   wealth_tier_level, wealth_tier, strength, agility, constitution, intelligence,
                   spirit, free_points, personality, dungeon_clears, peak_enhancement,
                   legendary_loot_count, current_activity_kind, current_activity_text,
                   current_activity_at, last_activity_at
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY RAND()
            LIMIT ?
            """,
            (rs, rowNum) -> mapRobot(rs),
            ROBOTS_PER_TICK
        );
        if (robots.isEmpty()) {
            return;
        }

        int actionCount = Math.min(robots.size(), MIN_ACTIONS_PER_TICK + random.nextInt(EXTRA_ACTION_RANGE));
        for (int index = 0; index < actionCount; index++) {
            RobotAgent actor = robots.get(index);
            RobotAgent target = robots.get((index + 1 + random.nextInt(robots.size())) % robots.size());
            robotBrainService.thinkAndAct(actor, target);
        }
        robotActionSupport.trimChat();
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
