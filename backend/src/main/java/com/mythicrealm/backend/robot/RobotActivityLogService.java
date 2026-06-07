package com.mythicrealm.backend.robot;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.mythicrealm.backend.inventory.InventoryService;
import com.mythicrealm.backend.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.backend.player.PlayerRecord;

@Service
public class RobotActivityLogService {
    private final JdbcTemplate jdbcTemplate;
    private final RobotEquipmentService robotEquipmentService;
    private final InventoryService inventoryService;

    public RobotActivityLogService(
        JdbcTemplate jdbcTemplate,
        RobotEquipmentService robotEquipmentService,
        InventoryService inventoryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.robotEquipmentService = robotEquipmentService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public void record(long robotId, String kind, String text) {
        RobotIdentity robot = jdbcTemplate.query(
            "SELECT name, title FROM player WHERE id = ? AND controller_type = 'robot'",
            (rs, rowNum) -> new RobotIdentity(rs.getString("name"), rs.getString("title")),
            robotId
        ).stream().findFirst().orElse(null);
        if (robot == null) {
            return;
        }
        jdbcTemplate.update(
            """
            UPDATE player
            SET current_activity_kind = ?, current_activity_text = ?, current_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            kind,
            text,
            robotId
        );
        jdbcTemplate.update(
            """
            INSERT INTO robot_activity_log (robot_id, actor_name, actor_title, kind, text)
            VALUES (?, ?, ?, ?, ?)
            """,
            robotId,
            robot.name(),
            robot.title(),
            kind,
            text
        );
        jdbcTemplate.update(
            "DELETE FROM robot_activity_log WHERE id NOT IN (SELECT id FROM (SELECT id FROM robot_activity_log ORDER BY created_at DESC, id DESC LIMIT 800) recent)"
        );
    }

    public RobotActivitySnapshot snapshot() {
        List<RobotActivityView> robots = jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, strength, agility,
                   constitution, intelligence, spirit, free_points, dungeon_clears, peak_enhancement,
                   legendary_loot_count, current_activity_kind, current_activity_text, current_activity_at,
                   last_activity_at
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY current_activity_at DESC, level DESC, id DESC
            """,
            (rs, rowNum) -> viewFromRow(rs)
        );
        List<RobotActivityEvent> events = latestEvents(160, null);
        return new RobotActivitySnapshot(robots, events);
    }

    @Transactional
    public RobotActivityDetail detail(long robotId) {
        RobotActivityView robot = jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, strength, agility,
                   constitution, intelligence, spirit, free_points, dungeon_clears, peak_enhancement,
                   legendary_loot_count, current_activity_kind, current_activity_text, current_activity_at,
                   last_activity_at
            FROM player
            WHERE id = ?
              AND controller_type = 'robot'
            """,
            (rs, rowNum) -> viewFromRow(rs),
            robotId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "机器人不存在"));
        List<EquipmentSummary> equipment = robotEquipmentService.equipmentForRobot(
            robot.id(),
            robot.name(),
            robot.profession(),
            robot.level(),
            robot.power()
        );
        return new RobotActivityDetail(robot, latestEventsForRobot(robotId, 240), equipment);
    }

    public List<RobotActivityEvent> latestEvents(int limit, List<String> kinds) {
        String kindFilter = "";
        Object[] args;
        if (kinds == null || kinds.isEmpty()) {
            args = new Object[] { limit };
        } else {
            kindFilter = "WHERE kind IN (" + "?,".repeat(kinds.size()).replaceAll(",$", "") + ") ";
            args = new Object[kinds.size() + 1];
            for (int i = 0; i < kinds.size(); i++) {
                args[i] = kinds.get(i);
            }
            args[kinds.size()] = limit;
        }
        return jdbcTemplate.query(
            """
            SELECT robot_id, actor_name, actor_title, kind, text, created_at
            FROM robot_activity_log
            """ + kindFilter + """
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new RobotActivityEvent(
                rs.getLong("robot_id"),
                rs.getString("actor_name"),
                rs.getString("actor_title"),
                rs.getString("kind"),
                rs.getString("text"),
                rs.getTimestamp("created_at").toInstant()
            ),
            args
        );
    }

    public List<RobotActivityEvent> latestEventsForRobot(long robotId, int limit) {
        return jdbcTemplate.query(
            """
            SELECT robot_id, actor_name, actor_title, kind, text, created_at
            FROM robot_activity_log
            WHERE robot_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new RobotActivityEvent(
                rs.getLong("robot_id"),
                rs.getString("actor_name"),
                rs.getString("actor_title"),
                rs.getString("kind"),
                rs.getString("text"),
                rs.getTimestamp("created_at").toInstant()
            ),
            robotId,
            limit
        );
    }

    private record RobotIdentity(String name, String title) {
    }

    private RobotActivityView viewFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        PlayerRecord robot = mapPlayer(rs);
        return new RobotActivityView(
            robot.id(),
            robot.name(),
            rs.getString("title"),
            robot.profession(),
            robot.level(),
            inventoryService.combatPower(robot),
            robot.gold(),
            rs.getInt("dungeon_clears"),
            rs.getInt("peak_enhancement"),
            rs.getInt("legendary_loot_count"),
            rs.getString("current_activity_kind"),
            rs.getString("current_activity_text"),
            rs.getTimestamp("current_activity_at").toInstant(),
            rs.getTimestamp("last_activity_at").toInstant()
        );
    }

    private PlayerRecord mapPlayer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlayerRecord(
            rs.getLong("id"),
            rs.getObject("account_id") == null ? 0 : rs.getLong("account_id"),
            rs.getString("name"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getInt("experience"),
            rs.getInt("gold"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
    }

    public record RobotActivitySnapshot(List<RobotActivityView> robots, List<RobotActivityEvent> events) {
    }

    public record RobotActivityDetail(
        RobotActivityView robot,
        List<RobotActivityEvent> events,
        List<EquipmentSummary> equipment
    ) {
    }

    public record RobotActivityView(
        long id,
        String name,
        String title,
        String profession,
        int level,
        int power,
        int gold,
        int dungeonClears,
        int peakEnhancement,
        int legendaryLootCount,
        String currentActivityKind,
        String currentActivityText,
        Instant currentActivityAt,
        Instant lastActivityAt
    ) {
    }

    public record RobotActivityEvent(
        long robotId,
        String actorName,
        String actorTitle,
        String kind,
        String text,
        Instant createdAt
    ) {
    }
}
