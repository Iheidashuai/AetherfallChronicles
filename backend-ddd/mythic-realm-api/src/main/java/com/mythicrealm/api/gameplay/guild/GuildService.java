package com.mythicrealm.api.gameplay.guild;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
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

    private static final long LEVEL_STEP = 3_000_000L;
    private static final int MAX_GUILD_LEVEL = 20;
    private static final long DONATE_GOLD_PER_COIN = 1_000L;
    private static final long BOSS_DMG_PER_COIN = 5_000L;
    private static final double PERK_DUNGEON_GOLD_PER_LEVEL = 1.0; // +1% dungeon gold per level (displayed)
    private static final double PERK_BOSS_DMG_PER_LEVEL = 2.0; // +2% guild-boss damage per level (applied)

    private final JdbcTemplate jdbcTemplate;
    private final StaminaService staminaService;

    public GuildService(JdbcTemplate jdbcTemplate, StaminaService staminaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.staminaService = staminaService;
    }

    public static String channelFor(long guildId) {
        return "guild:" + guildId;
    }

    // ---- P3: contribution accounting, leveling, perks ----

    /** Central contribution sink shared by boss damage and donations. */
    public void applyContribution(long guildId, long playerId, long contribution, long coinGain) {
        if (contribution > 0) {
            jdbcTemplate.update(
                "UPDATE guild_member SET weekly_contribution = weekly_contribution + ?, total_contribution = total_contribution + ? WHERE player_id = ?",
                contribution, contribution, playerId
            );
            jdbcTemplate.update(
                "UPDATE guild SET total_contribution = total_contribution + ?, weekly_contribution = weekly_contribution + ? WHERE id = ?",
                contribution, contribution, guildId
            );
            recomputeLevel(guildId);
        }
        if (coinGain > 0) {
            jdbcTemplate.update("UPDATE player SET guild_coin = guild_coin + ? WHERE id = ?", coinGain, playerId);
        }
    }

    private void recomputeLevel(long guildId) {
        Long total = jdbcTemplate.queryForObject("SELECT total_contribution FROM guild WHERE id = ?", Long.class, guildId);
        Integer current = jdbcTemplate.queryForObject("SELECT level FROM guild WHERE id = ?", Integer.class, guildId);
        if (total == null || current == null) {
            return;
        }
        int next = (int) Math.min(MAX_GUILD_LEVEL, 1 + total / LEVEL_STEP);
        if (next > current) {
            jdbcTemplate.update("UPDATE guild SET level = ? WHERE id = ?", next, guildId);
            jdbcTemplate.update(
                "INSERT INTO chat_message (sender_name, kind, text, channel) VALUES ('公会战报', 'system', ?, ?)",
                "公会升级到 Lv." + next + "！全员增益提升。",
                channelFor(guildId)
            );
        }
    }

    /** Guild-boss damage multiplier from the guild's level perk (applied in GuildBossService). */
    public double bossDamageMultiplier(long guildId) {
        Integer level = jdbcTemplate.queryForObject("SELECT level FROM guild WHERE id = ?", Integer.class, guildId);
        int lvl = level == null ? 1 : level;
        return 1.0 + lvl * PERK_BOSS_DMG_PER_LEVEL / 100.0;
    }

    private List<String> perksFor(int level) {
        return List.of(
            "公会 Boss 伤害 +" + (int) (level * PERK_BOSS_DMG_PER_LEVEL) + "%",
            "副本金币 +" + (int) (level * PERK_DUNGEON_GOLD_PER_LEVEL) + "%（规划中）"
        );
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
        Long fund = jdbcTemplate.queryForObject("SELECT fund FROM guild WHERE id = ?", Long.class, guildId);
        return new GuildView(
            summary,
            myRole,
            members,
            perksFor(summary.level()),
            currentGuildCoin(viewerPlayerId),
            fund == null ? 0 : fund
        );
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

    // ---- P3: donation + shop ----

    public DonateResult donate(PlayerRecord player, long amount) {
        long guildId = requireGuildIdForAction(player.id());
        if (amount <= 0) {
            throw ApiException.badRequest("捐献金额必须大于 0。");
        }
        Long gold = jdbcTemplate.queryForObject("SELECT gold FROM player WHERE id = ?", Long.class, player.id());
        if (gold == null || gold < amount) {
            throw ApiException.badRequest("金币不足，无法捐献。");
        }
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", amount, player.id());
        jdbcTemplate.update("UPDATE guild SET fund = fund + ? WHERE id = ?", amount, guildId);
        long coin = Math.max(1, amount / DONATE_GOLD_PER_COIN);
        applyContribution(guildId, player.id(), amount, coin);
        jdbcTemplate.update(
            "INSERT INTO chat_message (sender_name, kind, text, channel) VALUES ('公会战报', 'system', ?, ?)",
            player.name() + " 为公会捐献了 " + amount + " 金，公会资金增加。",
            channelFor(guildId)
        );
        return new DonateResult(amount, coin, currentGuildCoin(player.id()), guildView(guildId, player.id()));
    }

    /** Robot donates a slice of spare gold to its guild. Returns false if not eligible. */
    public boolean robotDonate(long robotId) {
        Long guildId = currentGuildId(robotId);
        if (guildId == null) {
            return false;
        }
        Long gold = jdbcTemplate.queryForObject("SELECT gold FROM player WHERE id = ?", Long.class, robotId);
        if (gold == null || gold < 5_000) {
            return false;
        }
        long amount = Math.min(gold / 8, 80_000);
        if (amount < 1_000) {
            return false;
        }
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", amount, robotId);
        jdbcTemplate.update("UPDATE guild SET fund = fund + ? WHERE id = ?", amount, guildId);
        applyContribution(guildId, robotId, amount, Math.max(1, amount / DONATE_GOLD_PER_COIN));
        return true;
    }

    public GuildShopView shop(PlayerRecord player) {
        requireGuildIdForAction(player.id());
        List<GuildShopOffer> offers = jdbcTemplate.query(
            "SELECT id, name, description, cost_guild_coin, reward_kind, reward_amount FROM guild_shop_offer ORDER BY sort_order",
            (rs, rowNum) -> new GuildShopOffer(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("cost_guild_coin"),
                rs.getString("reward_kind"),
                rs.getInt("reward_amount")
            )
        );
        return new GuildShopView(currentGuildCoin(player.id()), offers);
    }

    public GuildShopView buy(PlayerRecord player, String offerId) {
        requireGuildIdForAction(player.id());
        ShopOfferRow row = jdbcTemplate.query(
            "SELECT id, cost_guild_coin, reward_kind, reward_amount FROM guild_shop_offer WHERE id = ?",
            rs -> rs.next()
                ? new ShopOfferRow(rs.getString("id"), rs.getInt("cost_guild_coin"), rs.getString("reward_kind"), rs.getInt("reward_amount"))
                : null,
            offerId
        );
        if (row == null) {
            throw ApiException.badRequest("商品不存在。");
        }
        long coin = currentGuildCoin(player.id());
        if (coin < row.cost()) {
            throw ApiException.badRequest("公会币不足，需要 " + row.cost() + "，当前 " + coin + "。");
        }
        jdbcTemplate.update("UPDATE player SET guild_coin = guild_coin - ? WHERE id = ?", row.cost(), player.id());
        switch (row.rewardKind()) {
            case "gold" -> jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", row.rewardAmount(), player.id());
            case "stamina" -> staminaService.add(player.id(), row.rewardAmount());
            default -> { }
        }
        return shop(player);
    }

    // ---- P4: inter-guild weekly ranking ----

    public List<GuildRankEntry> ranking(PlayerRecord player) {
        Long myGuild = currentGuildId(player.id());
        return jdbcTemplate.query(
            "SELECT id, name, level, weekly_contribution FROM guild ORDER BY weekly_contribution DESC, total_contribution DESC, id ASC",
            (rs, rowNum) -> new GuildRankEntry(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("level"),
                rs.getLong("weekly_contribution"),
                rowNum + 1,
                myGuild != null && myGuild == rs.getLong("id")
            )
        );
    }

    private long currentGuildCoin(long playerId) {
        Long coin = jdbcTemplate.queryForObject("SELECT guild_coin FROM player WHERE id = ?", Long.class, playerId);
        return coin == null ? 0 : coin;
    }

    private long requireGuildIdForAction(long playerId) {
        Long guildId = currentGuildId(playerId);
        if (guildId == null) {
            throw ApiException.badRequest("加入公会后才能进行该操作。");
        }
        return guildId;
    }

    private record ShopOfferRow(String id, int cost, String rewardKind, int rewardAmount) {
    }

    public record DonateResult(long amount, long coinGained, long guildCoin, GuildView guild) {
    }

    public record GuildShopOffer(String id, String name, String description, int costGuildCoin, String rewardKind, int rewardAmount) {
    }

    public record GuildShopView(long guildCoin, List<GuildShopOffer> offers) {
    }

    public record GuildRankEntry(long id, String name, int level, long weeklyContribution, int rank, boolean mine) {
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

    public record GuildView(
        GuildSummary guild,
        String myRole,
        List<GuildMember> members,
        List<String> perks,
        long myGuildCoin,
        long fund
    ) {
    }

    public record GuildChatMessage(long id, String senderName, String kind, String text, Instant createdAt) {
    }
}
