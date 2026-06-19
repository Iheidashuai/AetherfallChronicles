package com.mythicrealm.api.gameplay.guild;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guild social ecosystem - P1 (social shell): browse / join / leave / my-guild snapshot,
 * plus a polled guild chat channel. Boss / donation / ranking arrive in later phases.
 */
@Service
public class GuildService {
    private static final List<String> GUILD_OPENING_LINES = List.of(
        "公会大厅开张，欢迎新人随时冒泡。",
        "今天先各刷各的，晚点看看要不要一起推本。",
        "有装备需求的吱一声，会里互通有无。"
    );
    private static final List<String> GUILD_REPLY_LINES = List.of(
        "收到，公会频道记下了。",
        "稳，等会儿一起上分。",
        "欢迎欢迎，缺啥喊一声。",
        "这话题我顶，等下细聊。"
    );

    private final JdbcTemplate jdbcTemplate;

    public GuildService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static String channelFor(long guildId) {
        return "guild:" + guildId;
    }

    /** All guilds, ranked by total contribution (the inter-guild ladder shown when browsing). */
    public List<GuildSummary> browse() {
        List<GuildSummary> rows = jdbcTemplate.query(
            """
            SELECT g.id, g.name, g.level, g.total_contribution, g.recruiting_blurb,
                   COALESCE(lp.name, '虚位以待') AS leader_name,
                   (SELECT COUNT(*) FROM guild_member m WHERE m.guild_id = g.id) AS member_count
            FROM guild g
            LEFT JOIN player lp ON lp.id = g.leader_robot_id
            ORDER BY g.total_contribution DESC, g.id ASC
            """,
            (rs, rowNum) -> new GuildSummary(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("level"),
                rowNum + 1,
                rs.getInt("member_count"),
                rs.getString("leader_name"),
                rs.getString("recruiting_blurb")
            )
        );
        return rows;
    }

    /** The player's guild snapshot, or null if they have not joined one. */
    @Transactional
    public GuildView myGuild(PlayerRecord player) {
        Long guildId = currentGuildId(player.id());
        if (guildId == null) {
            return null;
        }
        ensureGuildOpening(guildId);
        return guildView(guildId, player.id());
    }

    @Transactional
    public GuildView join(PlayerRecord player, long guildId) {
        if (currentGuildId(player.id()) != null) {
            throw ApiException.badRequest("你已经在一个公会里了，请先退出再加入。");
        }
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM guild WHERE id = ?", Integer.class, guildId);
        if (exists == null || exists == 0) {
            throw ApiException.badRequest("公会不存在。");
        }
        jdbcTemplate.update(
            "INSERT INTO guild_member (guild_id, player_id, role) VALUES (?, ?, 'member')",
            guildId,
            player.id()
        );
        ensureGuildOpening(guildId);
        return guildView(guildId, player.id());
    }

    @Transactional
    public void leave(PlayerRecord player) {
        Long guildId = currentGuildId(player.id());
        if (guildId == null) {
            throw ApiException.badRequest("你还没有加入任何公会。");
        }
        jdbcTemplate.update("DELETE FROM guild_member WHERE player_id = ?", player.id());
    }

    public List<GuildChatMessage> chat(PlayerRecord player) {
        Long guildId = requireGuildId(player.id());
        ensureGuildOpening(guildId);
        return recentChat(guildId);
    }

    @Transactional
    public List<GuildChatMessage> sendChat(PlayerRecord player, String rawText) {
        Long guildId = requireGuildId(player.id());
        String text = sanitize(rawText);
        if (text.isBlank()) {
            throw ApiException.badRequest("消息不能为空。");
        }
        insertMessage(player.id(), player.name(), "player", text, guildId);
        // A guildmate bot replies so the channel feels responsive (event-driven banter arrives in later phases).
        jdbcTemplate.query(
            """
            SELECT p.id, p.name FROM guild_member m
            JOIN player p ON p.id = m.player_id
            WHERE m.guild_id = ? AND p.controller_type = 'robot'
            ORDER BY RAND() LIMIT 1
            """,
            rs -> {
                if (rs.next()) {
                    String reply = GUILD_REPLY_LINES.get(ThreadLocalRandom.current().nextInt(GUILD_REPLY_LINES.size()));
                    insertMessage(rs.getLong("id"), rs.getString("name"), "robot", reply, guildId);
                }
                return null;
            },
            guildId
        );
        trimGuildChat(guildId);
        return recentChat(guildId);
    }

    private GuildView guildView(long guildId, long viewerPlayerId) {
        GuildSummary summary = jdbcTemplate.query(
            """
            SELECT g.id, g.name, g.level, g.total_contribution, g.recruiting_blurb,
                   COALESCE(lp.name, '虚位以待') AS leader_name,
                   (SELECT COUNT(*) FROM guild_member m WHERE m.guild_id = g.id) AS member_count,
                   (SELECT COUNT(*) + 1 FROM guild g2 WHERE g2.total_contribution > g.total_contribution) AS rank_pos
            FROM guild g
            LEFT JOIN player lp ON lp.id = g.leader_robot_id
            WHERE g.id = ?
            """,
            rs -> {
                if (!rs.next()) {
                    throw ApiException.badRequest("公会不存在。");
                }
                return new GuildSummary(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getInt("level"),
                    rs.getInt("rank_pos"),
                    rs.getInt("member_count"),
                    rs.getString("leader_name"),
                    rs.getString("recruiting_blurb")
                );
            },
            guildId
        );
        List<GuildMember> members = jdbcTemplate.query(
            """
            SELECT m.player_id, p.name, p.controller_type, p.profession, p.level, m.role,
                   m.weekly_contribution, m.total_contribution
            FROM guild_member m
            JOIN player p ON p.id = m.player_id
            WHERE m.guild_id = ?
            ORDER BY (m.role = 'leader') DESC, m.weekly_contribution DESC, p.level DESC
            LIMIT 60
            """,
            (rs, rowNum) -> new GuildMember(
                rs.getLong("player_id"),
                rs.getString("name"),
                "robot".equals(rs.getString("controller_type")) ? "robot" : "player",
                rs.getString("profession"),
                rs.getInt("level"),
                rs.getString("role"),
                rs.getLong("weekly_contribution"),
                rs.getLong("total_contribution")
            ),
            guildId
        );
        String myRole = jdbcTemplate.query(
            "SELECT role FROM guild_member WHERE player_id = ?",
            rs -> rs.next() ? rs.getString("role") : "member",
            viewerPlayerId
        );
        return new GuildView(summary, myRole, members);
    }

    private List<GuildChatMessage> recentChat(long guildId) {
        return jdbcTemplate.query(
            """
            SELECT id, sender_name, kind, text, created_at
            FROM chat_message
            WHERE channel = ?
            ORDER BY id DESC
            LIMIT 60
            """,
            (rs, rowNum) -> new GuildChatMessage(
                rs.getLong("id"),
                rs.getString("sender_name"),
                rs.getString("kind"),
                rs.getString("text"),
                rs.getTimestamp("created_at").toInstant()
            ),
            channelFor(guildId)
        ).reversed();
    }

    private void ensureGuildOpening(long guildId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_message WHERE channel = ?",
            Integer.class,
            channelFor(guildId)
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.query(
            """
            SELECT p.id, p.name FROM guild_member m
            JOIN player p ON p.id = m.player_id
            WHERE m.guild_id = ? AND p.controller_type = 'robot'
            ORDER BY (m.role = 'leader') DESC, RAND()
            LIMIT 3
            """,
            rs -> {
                int i = 0;
                while (rs.next() && i < GUILD_OPENING_LINES.size()) {
                    insertMessage(rs.getLong("id"), rs.getString("name"), "robot", GUILD_OPENING_LINES.get(i), guildId);
                    i++;
                }
                return null;
            },
            guildId
        );
    }

    private void insertMessage(long playerId, String senderName, String kind, String text, long guildId) {
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text, channel) VALUES (?, ?, ?, ?, ?)",
            playerId,
            senderName,
            kind,
            text,
            channelFor(guildId)
        );
    }

    private void trimGuildChat(long guildId) {
        jdbcTemplate.update(
            """
            DELETE FROM chat_message
            WHERE channel = ?
              AND id NOT IN (
                SELECT id FROM (
                    SELECT id FROM chat_message WHERE channel = ? ORDER BY id DESC LIMIT 120
                ) recent
              )
            """,
            channelFor(guildId),
            channelFor(guildId)
        );
    }

    private Long currentGuildId(long playerId) {
        return jdbcTemplate.query(
            "SELECT guild_id FROM guild_member WHERE player_id = ?",
            rs -> rs.next() ? rs.getLong("guild_id") : null,
            playerId
        );
    }

    private Long requireGuildId(long playerId) {
        Long guildId = currentGuildId(playerId);
        if (guildId == null) {
            throw ApiException.badRequest("加入公会后才能使用公会频道。");
        }
        return guildId;
    }

    private String sanitize(String rawText) {
        if (rawText == null) {
            return "";
        }
        String sanitized = rawText.replaceAll("\\s+", " ").trim();
        return sanitized.substring(0, Math.min(120, sanitized.length()));
    }

    public record GuildSummary(
        long id,
        String name,
        int level,
        int rank,
        int memberCount,
        String leaderName,
        String recruitingBlurb
    ) {
    }

    public record GuildMember(
        long playerId,
        String name,
        String kind,
        String profession,
        int level,
        String role,
        long weeklyContribution,
        long totalContribution
    ) {
    }

    public record GuildView(GuildSummary guild, String myRole, List<GuildMember> members) {
    }

    public record GuildChatMessage(long id, String senderName, String kind, String text, Instant createdAt) {
    }
}
