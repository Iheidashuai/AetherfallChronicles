package com.mythicrealm.api.gameplay.guild;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Guild social ecosystem - P4: weekly world-clock that settles the inter-guild ranking.
 *
 * On ISO-week rollover it snapshots each guild's weekly contribution into
 * guild_weekly_result, pays guild coin to members of the top guilds, posts a
 * server-wide "本周第一公会" announcement, and resets weekly counters. Bosses reset
 * automatically because GuildBossService keys them on the ISO week.
 *
 * Not a bot decision — the world advances the week, not any robot.
 */
@Service
public class GuildSeasonService {
    private final JdbcTemplate jdbcTemplate;
    private volatile String lastKnownWeek;

    public GuildSeasonService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.lastKnownWeek = weekKey();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 120_000)
    public void tick() {
        String current = weekKey();
        if (!current.equals(lastKnownWeek)) {
            settle(lastKnownWeek);
            lastKnownWeek = current;
        }
    }

    /** Settle the given week: snapshot ranking, pay top guilds, announce, reset counters. */
    void settle(String weekKey) {
        List<long[]> guilds = jdbcTemplate.query(
            "SELECT id, weekly_contribution FROM guild ORDER BY weekly_contribution DESC, total_contribution DESC, id ASC",
            (rs, rowNum) -> new long[] { rs.getLong("id"), rs.getLong("weekly_contribution") }
        );
        Long firstGuildId = null;
        int rank = 1;
        for (long[] guild : guilds) {
            long guildId = guild[0];
            long weekly = guild[1];
            int rewardCoin = rank == 1 ? 200 : rank <= 3 ? 100 : rank <= 5 ? 50 : 0;
            jdbcTemplate.update(
                """
                INSERT INTO guild_weekly_result (week_key, guild_id, rank_pos, weekly_contribution, reward_coin)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE rank_pos = VALUES(rank_pos), weekly_contribution = VALUES(weekly_contribution), reward_coin = VALUES(reward_coin)
                """,
                weekKey, guildId, rank, weekly, rewardCoin
            );
            if (rewardCoin > 0) {
                jdbcTemplate.update(
                    "UPDATE player p JOIN guild_member m ON m.player_id = p.id SET p.guild_coin = p.guild_coin + ? WHERE m.guild_id = ?",
                    rewardCoin, guildId
                );
            }
            if (rank == 1 && weekly > 0) {
                firstGuildId = guildId;
            }
            rank++;
        }
        if (firstGuildId != null) {
            String name = jdbcTemplate.queryForObject("SELECT name FROM guild WHERE id = ?", String.class, firstGuildId);
            jdbcTemplate.update(
                "INSERT INTO global_announcement (kind, actor_name, text, priority) VALUES ('system', ?, ?, 10)",
                name,
                "本周第一公会：" + name + "！全公会成员获得公会币奖励。"
            );
        }
        jdbcTemplate.update("UPDATE guild SET weekly_contribution = 0");
        jdbcTemplate.update("UPDATE guild_member SET weekly_contribution = 0");
    }

    private String weekKey() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        return now.getYear() + "-W" + String.format("%02d", now.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }
}
