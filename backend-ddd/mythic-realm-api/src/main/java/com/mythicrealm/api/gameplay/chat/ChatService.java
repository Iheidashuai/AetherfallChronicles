package com.mythicrealm.api.gameplay.chat;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final LeaderboardService leaderboardService;

    public ChatService(
        JdbcTemplate jdbcTemplate,
        PlayerService playerService,
        InventoryService inventoryService,
        LeaderboardService leaderboardService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.leaderboardService = leaderboardService;
    }

    @Transactional
    public List<ChatMessageView> messages() {
        ensureOpeningMessages();
        ensureAmbientMessages();
        return recentMessages();
    }

    @Transactional
    public ChatMessageView send(PlayerRecord player, String rawText) {
        String text = sanitize(rawText);
        if (text.isBlank()) {
            throw ApiException.badRequest("消息不能为空");
        }
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'player', ?)",
            player.id(),
            player.name(),
            text
        );
        List<RobotLite> robots = jdbcTemplate.query(
            "SELECT id, name, title FROM player WHERE controller_type = 'robot' ORDER BY RAND() LIMIT 3",
            (rs, rowNum) -> new RobotLite(rs.getLong("id"), rs.getString("name"), rs.getString("title"))
        );
        if (robots.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO chat_message (sender_name, kind, text) VALUES ('公会书记', 'system', ?)",
                replyFor(text)
            );
        } else {
            RobotLite robot = robots.get(0);
            jdbcTemplate.update(
                "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'robot', ?)",
                robot.id(),
                robot.name(),
                replyFor(text)
            );
        }
        if (robots.size() > 1) {
            jdbcTemplate.update(
                "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'robot', ?)",
                robots.get(1).id(),
                robots.get(1).name(),
                followUpFor(player.name(), text)
            );
        }
        return recentMessages().get(recentMessages().size() - 1);
    }

    private void ensureOpeningMessages() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO chat_message (sender_name, kind, text) VALUES ('公会书记', 'system', '传讯水晶已接入 H5 远征记录。')"
        );
        jdbcTemplate.update(
            """
            INSERT INTO chat_message (player_id, sender_name, kind, text)
            SELECT id, name, 'robot', CONCAT(title, ' 已抵达银冠公会大厅。')
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY level DESC, gold DESC
            LIMIT 3
            """
        );
    }

    private void ensureAmbientMessages() {
        Integer recentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_message WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL 8 MINUTE",
            Integer.class
        );
        int needed = 70 - (recentCount == null ? 0 : recentCount);
        if (needed <= 0) {
            return;
        }
        jdbcTemplate.update(
            """
            INSERT INTO chat_message (player_id, sender_name, kind, text)
            SELECT id, name, 'robot',
                CASE FLOOR(RAND() * 14)
                    WHEN 0 THEN CONCAT(title, ' 刚刷新战力榜，前 100 又卷起来了。')
                    WHEN 1 THEN '刚从副本出来，危险档奖励高，但真的扛不住。'
                    WHEN 2 THEN '商会低价装备有人秒吗？我刚看到一件不错的。'
                    WHEN 3 THEN '强化成功以后战力涨得很明显，金币也掉得很明显。'
                    WHEN 4 THEN '背包整理完舒服多了，普通装直接清。'
                    WHEN 5 THEN CONCAT('有人在刷蛛影林地吗？', title, ' 想换一件护手。')
                    WHEN 6 THEN '别光看颜色，词条歪了也要卖。'
                    WHEN 7 THEN '今日任务还没交，差点忘了。'
                    WHEN 8 THEN '刚买到一件低级稀有，比副本掉的还香。'
                    WHEN 9 THEN '刚有人出传说，世界通告亮得我手一抖。'
                    WHEN 10 THEN '稳妥副本适合挂机刷，极危副本别硬上。'
                    WHEN 11 THEN '有人看见月刃剑士吗？刚才还在喊组队。'
                    WHEN 12 THEN '我回了一个新人问题，结果三个人同时抢答。'
                    ELSE '公会大厅今晚人不少，频道刷得太快了。'
                END
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY RAND()
            LIMIT ?
            """,
            needed
        );
    }

    private List<ChatMessageView> recentMessages() {
        List<RawMessage> raw = jdbcTemplate.query(
            """
            SELECT id, player_id, sender_name, kind, text, created_at
            FROM chat_message
            ORDER BY created_at DESC, id DESC
            LIMIT 80
            """,
            (rs, rowNum) -> {
                long playerId = rs.getLong("player_id");
                boolean playerIdMissing = rs.wasNull();
                return new RawMessage(
                    rs.getLong("id"),
                    playerIdMissing ? null : playerId,
                    rs.getString("sender_name"),
                    rs.getString("kind"),
                    rs.getString("text"),
                    rs.getTimestamp("created_at").toInstant()
                );
            }
        ).reversed();
        Map<String, SpeakerProfile> speakerCache = new HashMap<>();
        return raw.stream()
            .map(message -> new ChatMessageView(
                message.id(),
                message.senderName(),
                message.kind(),
                message.text(),
                message.createdAt(),
                speakerFor(message, speakerCache)
            ))
            .toList();
    }

    private String sanitize(String rawText) {
        if (rawText == null) {
            return "";
        }
        String sanitized = rawText.replaceAll("\\s+", " ").trim();
        return sanitized.substring(0, Math.min(120, sanitized.length()));
    }

    private String replyFor(String text) {
        if (text.contains("市场") || text.contains("装备")) {
            return "商会看板刚刷新，低等级高品质装备也有捡漏空间，我刚蹲到一件。";
        }
        if (text.contains("副本")) {
            return "蛛影林地适合热身，战力够的话可以往更深处推进，先看风险档。";
        }
        if (text.contains("强化")) {
            return "强化别上头，金币也是战力的一部分。";
        }
        return "收到，传讯水晶已经记录。有人要接这个话题吗？";
    }

    private String followUpFor(String playerName, String text) {
        if (text.contains("副本")) {
            return playerName + "，我建议先刷已通过副本攒装备，再碰危险档。";
        }
        if (text.contains("装备") || text.contains("市场")) {
            return playerName + "，看词条和强化等级，别只看品质颜色。";
        }
        if (text.contains("强化")) {
            return playerName + "，+7 以后真的要留金币兜底。";
        }
        return playerName + " 这句我赞同，频道里刚才也有人这么说。";
    }

    private SpeakerProfile speakerFor(RawMessage message, Map<String, SpeakerProfile> cache) {
        String cacheKey = message.playerId() == null ? "robot:" + message.senderName() + ":" + message.kind() : "player:" + message.playerId();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        SpeakerProfile profile = message.playerId() == null
            ? robotSpeaker(message.senderName(), message.kind())
            : playerSpeaker(message.playerId(), message.senderName());
        cache.put(cacheKey, profile);
        return profile;
    }

    private SpeakerProfile playerSpeaker(long playerId, String fallbackName) {
        return jdbcTemplate.query(
            """
            SELECT id, account_id, name, profession, level, experience, gold, strength, agility,
                   constitution, intelligence, spirit, free_points, controller_type, title
            FROM player
            WHERE id = ?
            """,
            (rs, rowNum) -> {
                PlayerRecord player = mapPlayer(rs);
                String kind = rs.getString("controller_type");
                return new SpeakerProfile(
                    player.id(),
                    player.name(),
                    "robot".equals(kind) ? rs.getString("title") : "玩家",
                    kind,
                    player.profession(),
                    player.level(),
                    inventoryService.combatPower(player),
                    player.experience(),
                    player.gold(),
                    player.strength(),
                    player.agility(),
                    player.constitution(),
                    player.intelligence(),
                    player.spirit(),
                    player.freePoints(),
                    "robot".equals(kind)
                        ? leaderboardService.equipmentForRobot(player.id(), player.name(), player.profession(), player.level(), inventoryService.combatPower(player))
                        : leaderboardService.equipmentForPlayer(player)
                );
            },
            playerId
        ).stream().findFirst().orElseGet(() -> systemSpeaker(fallbackName));
    }

    private SpeakerProfile robotSpeaker(String senderName, String kind) {
        if ("system".equals(kind)) {
            return systemSpeaker(senderName);
        }
        return jdbcTemplate.query(
                """
                SELECT id, account_id, name, profession, level, experience, gold, strength, agility,
                       constitution, intelligence, spirit, free_points, title
                FROM player
                WHERE controller_type = 'robot' AND name = ?
                """,
                (rs, rowNum) -> {
                    PlayerRecord robot = mapPlayer(rs);
                    return new SpeakerProfile(
                        robot.id(),
                        robot.name(),
                        rs.getString("title"),
                        "robot",
                        robot.profession(),
                        robot.level(),
                        inventoryService.combatPower(robot),
                        robot.experience(),
                        robot.gold(),
                        robot.strength(),
                        robot.agility(),
                        robot.constitution(),
                        robot.intelligence(),
                        robot.spirit(),
                        robot.freePoints(),
                        leaderboardService.equipmentForRobot(robot.id(), robot.name(), robot.profession(), robot.level(), inventoryService.combatPower(robot))
                    );
                },
                senderName
            )
            .stream()
            .findFirst()
            .orElseGet(() -> systemSpeaker(senderName));
    }

    private SpeakerProfile systemSpeaker(String name) {
        return new SpeakerProfile(null, name, "系统", "system", "system", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
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
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
    }

    private record RawMessage(long id, Long playerId, String senderName, String kind, String text, Instant createdAt) {
    }

    private record RobotLite(long id, String name, String title) {
    }

    public record ChatMessageView(long id, String senderName, String kind, String text, Instant createdAt, SpeakerProfile speaker) {
    }

    public record SpeakerProfile(
        Long playerId,
        String name,
        String title,
        String kind,
        String profession,
        int level,
        int power,
        int experience,
        long gold,
        int strength,
        int agility,
        int constitution,
        int intelligence,
        int spirit,
        int freePoints,
        List<EquipmentSummary> equipment
    ) {
    }
}
