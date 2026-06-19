package com.mythicrealm.api.gameplay.chat;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.DerivedStats;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {
    private static final List<String> AMBIENT_LINES = List.of(
        "副本大厅有人补输出吗？我刚测完一轮。",
        "刚从铁匠铺出来，强化成功一次真的很提神。",
        "市场里有几件低等级好词条装备，手慢可能就没了。",
        "今天先刷装备再冲副本，战力门槛别硬莽。",
        "有人看到高护甲装备可以留意下，Boss 重击越来越疼了。",
        "我刚换了一件抗性装，法系怪明显好打很多。",
        "副本风险提示挺准，黄色以上我一般先补装备。",
        "背包快满了，扫荡之前记得清一下。"
    );

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final LeaderboardService leaderboardService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

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
        return recentMessages();
    }

    public SseEmitter stream(long afterId) {
        ensureOpeningMessages();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(error -> open.set(false));
        streamExecutor.execute(() -> runStream(emitter, open, Math.max(0, afterId)));
        return emitter;
    }

    @Transactional
    public ChatMessageView send(PlayerRecord player, String rawText) {
        String text = sanitize(rawText);
        if (text.isBlank()) {
            throw ApiException.badRequest("消息不能为空");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'player', ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, player.id());
            statement.setString(2, player.name());
            statement.setString(3, text);
            return statement;
        }, keyHolder);
        Number playerMessageId = keyHolder.getKey();
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
        trimOldMessages();
        if (playerMessageId == null) {
            return recentMessages().get(recentMessages().size() - 1);
        }
        return messageById(playerMessageId.longValue());
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    private void runStream(SseEmitter emitter, AtomicBoolean open, long afterId) {
        long lastId = afterId;
        long nextAmbientAt = System.currentTimeMillis() + nextAmbientDelayMillis();
        try {
            while (open.get()) {
                List<ChatMessageView> pending = messagesAfter(lastId);
                if (pending.isEmpty() && System.currentTimeMillis() >= nextAmbientAt) {
                    insertAmbientMessage();
                    nextAmbientAt = System.currentTimeMillis() + nextAmbientDelayMillis();
                    pending = messagesAfter(lastId);
                }
                if (pending.isEmpty()) {
                    sleep(open, 900);
                    continue;
                }
                for (ChatMessageView message : pending) {
                    if (!open.get()) {
                        return;
                    }
                    emitter.send(SseEmitter.event()
                        .name("message")
                        .id(Long.toString(message.id()))
                        .data(message));
                    lastId = Math.max(lastId, message.id());
                    sleep(open, 650);
                }
            }
        } catch (IOException | IllegalStateException error) {
            if (open.get()) {
                emitter.completeWithError(error);
            }
        } finally {
            open.set(false);
        }
    }

    private void insertAmbientMessage() {
        List<RobotLite> robots = jdbcTemplate.query(
            "SELECT id, name, title FROM player WHERE controller_type = 'robot' ORDER BY RAND() LIMIT 1",
            (rs, rowNum) -> new RobotLite(rs.getLong("id"), rs.getString("name"), rs.getString("title"))
        );
        String text = AMBIENT_LINES.get(ThreadLocalRandom.current().nextInt(AMBIENT_LINES.size()));
        if (robots.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO chat_message (sender_name, kind, text) VALUES (?, 'system', ?)",
                "公会书记",
                text
            );
        } else {
            RobotLite robot = robots.get(0);
            jdbcTemplate.update(
                "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'robot', ?)",
                robot.id(),
                robot.name(),
                text
            );
        }
        trimOldMessages();
    }

    private long nextAmbientDelayMillis() {
        return ThreadLocalRandom.current().nextLong(2_800, 6_800);
    }

    private void sleep(AtomicBoolean open, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            open.set(false);
        }
    }

    private void trimOldMessages() {
        jdbcTemplate.update(
            """
            DELETE FROM chat_message
            WHERE channel = 'world'
              AND id NOT IN (
                SELECT id FROM (
                    SELECT id FROM chat_message
                    WHERE channel = 'world'
                    ORDER BY id DESC
                    LIMIT 260
                ) recent_messages
            )
            """
        );
    }

    private void ensureOpeningMessages() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message WHERE channel = 'world'", Integer.class);
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

    private List<ChatMessageView> recentMessages() {
        List<RawMessage> raw = jdbcTemplate.query(
            """
            SELECT id, player_id, sender_name, kind, text, created_at
            FROM chat_message
            WHERE channel = 'world'
            ORDER BY id DESC
            LIMIT 80
            """,
            (rs, rowNum) -> mapRawMessage(rs)
        ).reversed();
        return toViews(raw);
    }

    private List<ChatMessageView> messagesAfter(long afterId) {
        return toViews(jdbcTemplate.query(
            """
            SELECT id, player_id, sender_name, kind, text, created_at
            FROM chat_message
            WHERE id > ? AND channel = 'world'
            ORDER BY id ASC
            LIMIT 24
            """,
            (rs, rowNum) -> mapRawMessage(rs),
            afterId
        ));
    }

    private ChatMessageView messageById(long messageId) {
        return toViews(jdbcTemplate.query(
            """
            SELECT id, player_id, sender_name, kind, text, created_at
            FROM chat_message
            WHERE id = ?
            """,
            (rs, rowNum) -> mapRawMessage(rs),
            messageId
        )).stream().findFirst().orElseGet(() -> recentMessages().get(recentMessages().size() - 1));
    }

    private RawMessage mapRawMessage(java.sql.ResultSet rs) throws java.sql.SQLException {
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

    private List<ChatMessageView> toViews(List<RawMessage> raw) {
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
                List<EquipmentSummary> equipment = "robot".equals(kind)
                    ? leaderboardService.equipmentForRobot(player.id(), player.name(), player.profession(), player.level(), inventoryService.combatPower(player))
                    : leaderboardService.equipmentForPlayer(player);
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
                    leaderboardService.derivedStatsForPlayer(player),
                    equipment.stream().mapToInt(EquipmentSummary::power).sum(),
                    equipment
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
                    List<EquipmentSummary> equipment = leaderboardService.equipmentForRobot(robot.id(), robot.name(), robot.profession(), robot.level(), inventoryService.combatPower(robot));
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
                        leaderboardService.derivedStatsForPlayer(robot),
                        equipment.stream().mapToInt(EquipmentSummary::power).sum(),
                        equipment
                    );
                },
                senderName
            )
            .stream()
            .findFirst()
            .orElseGet(() -> systemSpeaker(senderName));
    }

    private SpeakerProfile systemSpeaker(String name) {
        return new SpeakerProfile(
            null,
            name,
            "系统",
            "system",
            "system",
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            new DerivedStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            0,
            List.of()
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
        DerivedStats derivedStats,
        int equipmentPower,
        List<EquipmentSummary> equipment
    ) {
    }
}
