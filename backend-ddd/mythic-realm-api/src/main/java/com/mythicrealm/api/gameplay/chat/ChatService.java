package com.mythicrealm.api.gameplay.chat;

import com.mythicrealm.api.gameplay.ai.AiChatInteractionService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.DerivedStats;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
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
    private final AiChatInteractionService aiChatInteractionService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public ChatService(
        JdbcTemplate jdbcTemplate,
        AiChatInteractionService aiChatInteractionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiChatInteractionService = aiChatInteractionService;
    }

    @Transactional
    public List<ChatMessageView> messages(PlayerRecord player, String rawScope) {
        String channel = resolveChannel(player, rawScope);
        ensureOpeningMessages(channel);
        return recentMessages(channel);
    }

    public SseEmitter stream(PlayerRecord player, String rawScope, long afterId) {
        String channel = resolveChannel(player, rawScope);
        ensureOpeningMessages(channel);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(error -> open.set(false));
        streamExecutor.execute(() -> runStream(emitter, open, Math.max(0, afterId), channel));
        return emitter;
    }

    @Transactional
    public ChatMessageView send(PlayerRecord player, String rawText, String rawScope) {
        String channel = resolveChannel(player, rawScope);
        String scope = normalizeScope(rawScope);
        String text = sanitize(rawText);
        if (text.isBlank()) {
            throw ApiException.badRequest("消息不能为空");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chat_message (player_id, sender_name, kind, text, channel) VALUES (?, ?, 'player', ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, player.id());
            statement.setString(2, player.name());
            statement.setString(3, text);
            statement.setString(4, channel);
            return statement;
        }, keyHolder);
        Number playerMessageId = keyHolder.getKey();
        if (playerMessageId != null) {
            aiChatInteractionService.enqueuePlayerMessage(player, playerMessageId.longValue(), scope, channel, text);
        }
        trimOldMessages(channel, "guild".equals(scope) ? 140 : 300);
        if (playerMessageId == null) {
            List<ChatMessageView> messages = recentMessages(channel);
            return messages.get(messages.size() - 1);
        }
        return messageById(playerMessageId.longValue());
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    private void runStream(SseEmitter emitter, AtomicBoolean open, long afterId, String channel) {
        long lastId = afterId;
        long nextAmbientAt = System.currentTimeMillis() + nextAmbientDelayMillis();
        long nextHeartbeatAt = System.currentTimeMillis() + 15_000;
        try {
            emitter.send(SseEmitter.event().name("ready").data(channel));
            while (open.get()) {
                List<ChatMessageView> pending = messagesAfter(lastId, channel);
                if ("world".equals(channel) && pending.isEmpty() && System.currentTimeMillis() >= nextAmbientAt) {
                    insertAmbientMessage(channel);
                    nextAmbientAt = System.currentTimeMillis() + nextAmbientDelayMillis();
                    pending = messagesAfter(lastId, channel);
                }
                if (pending.isEmpty()) {
                    if (System.currentTimeMillis() >= nextHeartbeatAt) {
                        emitter.send(SseEmitter.event().name("ping").data(Long.toString(System.currentTimeMillis())));
                        nextHeartbeatAt = System.currentTimeMillis() + 15_000;
                    }
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
                    nextHeartbeatAt = System.currentTimeMillis() + 15_000;
                    sleep(open, 650);
                }
            }
        } catch (IOException | IllegalStateException error) {
            open.set(false);
        } finally {
            open.set(false);
        }
    }

    private void insertAmbientMessage(String channel) {
        List<RobotLite> robots = jdbcTemplate.query(
            "SELECT id, name, title FROM player WHERE controller_type = 'robot' ORDER BY RAND() LIMIT 1",
            (rs, rowNum) -> new RobotLite(rs.getLong("id"), rs.getString("name"), rs.getString("title"))
        );
        String text = AMBIENT_LINES.get(ThreadLocalRandom.current().nextInt(AMBIENT_LINES.size()));
        if (robots.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO chat_message (sender_name, kind, text, channel) VALUES (?, 'system', ?, ?)",
                "公会书记",
                text,
                channel
            );
        } else {
            RobotLite robot = robots.get(0);
            jdbcTemplate.update(
                "INSERT INTO chat_message (player_id, sender_name, kind, text, channel) VALUES (?, ?, 'robot', ?, ?)",
                robot.id(),
                robot.name(),
                text,
                channel
            );
        }
        trimOldMessages(channel, 300);
    }

    private long nextAmbientDelayMillis() {
        return ThreadLocalRandom.current().nextLong(35_000, 70_000);
    }

    private void sleep(AtomicBoolean open, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            open.set(false);
        }
    }

    private void trimOldMessages(String channel, int limit) {
        jdbcTemplate.update(
            """
            DELETE FROM chat_message
            WHERE channel = ?
              AND id NOT IN (
                SELECT id FROM (
                    SELECT id FROM chat_message
                    WHERE channel = ?
                    ORDER BY id DESC
                    LIMIT ?
                ) recent_messages
            )
            """,
            channel,
            channel,
            limit
        );
    }

    private void ensureOpeningMessages(String channel) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message WHERE channel = ?", Integer.class, channel);
        if (count != null && count > 0) {
            return;
        }
        if (!"world".equals(channel)) {
            ensureGuildOpening(channel);
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO chat_message (sender_name, kind, text, channel) VALUES ('公会书记', 'system', '传讯水晶已接入 H5 远征记录。', ?)",
            channel
        );
        jdbcTemplate.update(
            """
            INSERT INTO chat_message (player_id, sender_name, kind, text, channel)
            SELECT id, name, 'robot', CONCAT(title, ' 已抵达银冠公会大厅。'), ?
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY level DESC, gold DESC
            LIMIT 3
            """,
            channel
        );
    }

    private void ensureGuildOpening(String channel) {
        Long guildId = parseGuildChannel(channel);
        if (guildId == null) {
            return;
        }
        jdbcTemplate.query(
            """
            SELECT p.id, p.name
            FROM guild_member gm
            JOIN player p ON p.id = gm.player_id
            WHERE gm.guild_id = ? AND p.controller_type = 'robot'
            ORDER BY (gm.role = 'leader') DESC, RAND()
            LIMIT 3
            """,
            rs -> {
                int index = 0;
                List<String> lines = List.of("公会频道接通，今天有人一起推本吗？", "我在大厅看战报，Boss 有动静喊我。", "缺装备可以先说，商会里我也会留意。");
                while (rs.next() && index < lines.size()) {
                    jdbcTemplate.update(
                        "INSERT INTO chat_message (player_id, sender_name, kind, text, channel) VALUES (?, ?, 'robot', ?, ?)",
                        rs.getLong("id"),
                        rs.getString("name"),
                        lines.get(index),
                        channel
                    );
                    index++;
                }
                return null;
            },
            guildId
        );
    }

    private List<ChatMessageView> recentMessages(String channel) {
        List<RawMessage> raw = jdbcTemplate.query(
            """
            SELECT id, player_id, sender_name, kind, text, deliver_at AS created_at
            FROM chat_message
            WHERE channel = ?
              AND deliver_at <= CURRENT_TIMESTAMP
              AND NOT (
                kind = 'robot' AND (
                  text LIKE '刚花 %技能%' OR
                  text LIKE '领取任务《%' OR
                  text LIKE '使用《%' OR
                  text LIKE '合成《%' OR
                  text LIKE '这轮副本击败 %战力评估更新到 %' OR
                  text LIKE '换了 %元，补进 %金%' OR
                  text LIKE '%刚把【%】强化到 +%' OR
                  text LIKE '%强化【%】失败了%' OR
                  text LIKE '切换到构筑【%'
                )
              )
            ORDER BY id DESC
            LIMIT 80
            """,
            (rs, rowNum) -> mapRawMessage(rs),
            channel
        ).reversed();
        return toViews(raw);
    }

    private List<ChatMessageView> messagesAfter(long afterId, String channel) {
        return toViews(jdbcTemplate.query(
            """
            SELECT cm.id, cm.player_id, cm.sender_name, cm.kind, cm.text, cm.deliver_at AS created_at
            FROM chat_message cm
            WHERE cm.channel = ?
              AND cm.id > ?
              AND cm.deliver_at <= CURRENT_TIMESTAMP
              AND NOT (
                cm.kind = 'robot' AND (
                  cm.text LIKE '刚花 %技能%' OR
                  cm.text LIKE '领取任务《%' OR
                  cm.text LIKE '使用《%' OR
                  cm.text LIKE '合成《%' OR
                  cm.text LIKE '这轮副本击败 %战力评估更新到 %' OR
                  cm.text LIKE '换了 %元，补进 %金%' OR
                  cm.text LIKE '%刚把【%】强化到 +%' OR
                  cm.text LIKE '%强化【%】失败了%' OR
                  cm.text LIKE '切换到构筑【%'
                )
              )
              AND NOT EXISTS (
                SELECT 1
                FROM chat_message hidden
                WHERE hidden.channel = cm.channel
                  AND hidden.id > ?
                  AND hidden.id < cm.id
                  AND hidden.deliver_at > CURRENT_TIMESTAMP
              )
            ORDER BY cm.id ASC
            LIMIT 24
            """,
            (rs, rowNum) -> mapRawMessage(rs),
            channel,
            afterId,
            afterId
        ));
    }

    private ChatMessageView messageById(long messageId) {
        return toViews(jdbcTemplate.query(
            """
            SELECT id, player_id, sender_name, kind, text, deliver_at AS created_at
            FROM chat_message
            WHERE id = ?
            """,
            (rs, rowNum) -> mapRawMessage(rs),
            messageId
        )).stream().findFirst().orElseThrow(() -> ApiException.notFound("消息不存在"));
    }

    private String resolveChannel(PlayerRecord player, String rawScope) {
        String scope = normalizeScope(rawScope);
        if ("world".equals(scope)) {
            return "world";
        }
        Long guildId = currentGuildId(player.id());
        if (guildId == null) {
            throw ApiException.badRequest("加入公会后才能使用公会频道。");
        }
        return "guild:" + guildId;
    }

    private String normalizeScope(String rawScope) {
        String scope = rawScope == null || rawScope.isBlank() ? "world" : rawScope.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"world".equals(scope) && !"guild".equals(scope)) {
            throw ApiException.badRequest("未知聊天频道");
        }
        return scope;
    }

    private Long currentGuildId(long playerId) {
        return jdbcTemplate.query(
            "SELECT guild_id FROM guild_member WHERE player_id = ?",
            rs -> rs.next() ? rs.getLong("guild_id") : null,
            playerId
        );
    }

    private Long parseGuildChannel(String channel) {
        if (channel == null || !channel.startsWith("guild:")) {
            return null;
        }
        try {
            return Long.parseLong(channel.substring("guild:".length()));
        } catch (NumberFormatException error) {
            return null;
        }
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
                    quickPower(player),
                    player.experience(),
                    player.gold(),
                    player.strength(),
                    player.agility(),
                    player.constitution(),
                    player.intelligence(),
                    player.spirit(),
                    player.freePoints(),
                    null,
                    0,
                    List.of()
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
                (rs, rowNum) -> robotSpeakerFromRow(rs),
                senderName
            )
            .stream()
            .findFirst()
            .orElseGet(() -> systemSpeaker(senderName));
    }

    private SpeakerProfile robotSpeakerFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        PlayerRecord robot = mapPlayer(rs);
        return new SpeakerProfile(
            robot.id(),
            robot.name(),
            rs.getString("title"),
            "robot",
            robot.profession(),
            robot.level(),
            quickPower(robot),
            robot.experience(),
            robot.gold(),
            robot.strength(),
            robot.agility(),
            robot.constitution(),
            robot.intelligence(),
            robot.spirit(),
            robot.freePoints(),
            null,
            0,
            List.of()
        );
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
            null,
            0,
            List.of()
        );
    }

    private int quickPower(PlayerRecord player) {
        int attributeScore = player.strength() * 10
            + player.agility() * 8
            + player.constitution() * 9
            + player.intelligence() * 7
            + player.spirit() * 7;
        return Math.max(0, player.level() * 800 + attributeScore);
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
