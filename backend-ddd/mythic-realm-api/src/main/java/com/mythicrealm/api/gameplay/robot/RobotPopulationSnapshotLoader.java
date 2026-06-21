package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RobotPopulationSnapshotLoader {
    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;

    public RobotPopulationSnapshotLoader(JdbcTemplate jdbcTemplate, InventoryService inventoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
    }

    public boolean hasHumanPlayer() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player WHERE account_id IS NOT NULL",
            Integer.class
        );
        return count != null && count > 0;
    }

    public List<RobotAgent> loadRobots(int maxRobots) {
        return jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, real_money,
                   wealth_tier_level, wealth_tier, strength, agility, constitution, intelligence,
                   spirit, free_points, personality, personality_archetype, dungeon_clears,
                   peak_enhancement, legendary_loot_count, current_activity_kind,
                   current_activity_text, current_activity_at, last_activity_at
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY last_activity_at ASC, id ASC
            LIMIT ?
            """,
            (rs, rowNum) -> mapRobot(rs),
            Math.max(1, maxRobots)
        );
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
