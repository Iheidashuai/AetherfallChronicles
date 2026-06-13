package com.mythicrealm.api.gameplay.robot;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.api.gameplay.player.PlayerRecord;

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
        String normalizedText = normalizeText(text);
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
            normalizedText,
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
            normalizedText
        );
        jdbcTemplate.update(
            "DELETE FROM robot_activity_log WHERE id NOT IN (SELECT id FROM (SELECT id FROM robot_activity_log ORDER BY created_at DESC, id DESC LIMIT 2400) recent)"
        );
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "正在观察局势。";
        }
        String value = text.trim();
        return value.substring(0, Math.min(240, value.length()));
    }

    public RobotActivitySnapshot snapshot() {
        List<RobotActivityView> robots = jdbcTemplate.query(
            """
            SELECT p.id, p.account_id, p.name, p.title, p.profession, p.level, p.experience, p.gold,
                   p.real_money, p.wealth_tier_level, p.wealth_tier, p.strength, p.agility,
                   p.constitution, p.intelligence, p.spirit, p.free_points, p.dungeon_clears,
                   p.peak_enhancement, p.legendary_loot_count, p.current_activity_kind,
                   p.current_activity_text, p.current_activity_at, p.last_activity_at,
                   COALESCE(recharge.total_rmb, 0) AS recharge_rmb,
                   COALESCE(recharge.total_gold, 0) AS recharge_gold
            FROM player p
            LEFT JOIN (
                SELECT player_id, SUM(rmb_amount) AS total_rmb, SUM(gold_amount) AS total_gold
                FROM recharge_order
                GROUP BY player_id
            ) recharge ON recharge.player_id = p.id
            WHERE p.controller_type = 'robot'
            ORDER BY p.current_activity_at DESC, p.level DESC, p.id DESC
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
            SELECT p.id, p.account_id, p.name, p.title, p.profession, p.level, p.experience, p.gold,
                   p.real_money, p.wealth_tier_level, p.wealth_tier, p.strength, p.agility,
                   p.constitution, p.intelligence, p.spirit, p.free_points, p.dungeon_clears,
                   p.peak_enhancement, p.legendary_loot_count, p.current_activity_kind,
                   p.current_activity_text, p.current_activity_at, p.last_activity_at,
                   COALESCE(recharge.total_rmb, 0) AS recharge_rmb,
                   COALESCE(recharge.total_gold, 0) AS recharge_gold
            FROM player p
            LEFT JOIN (
                SELECT player_id, SUM(rmb_amount) AS total_rmb, SUM(gold_amount) AS total_gold
                FROM recharge_order
                GROUP BY player_id
            ) recharge ON recharge.player_id = p.id
            WHERE p.id = ?
              AND p.controller_type = 'robot'
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
            robot.realMoney(),
            robot.wealthTierLevel(),
            robot.wealthTier(),
            rs.getLong("recharge_rmb"),
            rs.getLong("recharge_gold"),
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
        long gold,
        long realMoney,
        int wealthTierLevel,
        String wealthTier,
        long rechargeRmb,
        long rechargeGold,
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
