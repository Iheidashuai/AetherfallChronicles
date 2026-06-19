package com.mythicrealm.api.gameplay.guild;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Guild social ecosystem - P2: a weekly shared-HP-pool boss per guild.
 *
 * Damage uses the cheap-formula model (design 对策 3): both player and bots deal
 * combatPower-based damage, so bots cost almost no compute while still contributing
 * real, per-member, power-scaled damage. The shared HP pool is guarded by a per-guild
 * JVM monitor (single-instance), with read-modify-write done under the monitor on
 * autocommit so the serialization is real.
 *
 * Boss lifecycle here is lazy: if no boss is alive for the current ISO week, one is
 * spawned on access; killing a boss immediately spawns a tougher "victory-lap" tier.
 * (A scheduled weekly settlement / rewards is P4.)
 */
@Service
public class GuildBossService {
    private static final long BASE_HP_PER_MEMBER = 20_000L; // early-game tuning; scales with member count & tier
    private static final double DMG_COEFF = 6.0;
    private static final long MIN_HIT = 800L;
    private static final int PLAYER_ATTACK_STAMINA = 1;
    private static final List<String> BOSS_NAMES = List.of(
        "公会守卫·蛛母",
        "腐根巨像",
        "血月魔将",
        "深渊回响"
    );

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final StaminaService staminaService;
    private final ConcurrentHashMap<Long, Object> bossMonitors = new ConcurrentHashMap<>();

    public GuildBossService(JdbcTemplate jdbcTemplate, InventoryService inventoryService, StaminaService staminaService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.staminaService = staminaService;
    }

    public GuildBossView bossFor(PlayerRecord player) {
        long guildId = requireGuildId(player.id());
        BossRow boss = ensureBoss(guildId);
        return buildView(guildId, boss, player.id());
    }

    /** Player attack: consumes stamina, deals real-combat-power-scaled damage to the shared pool. */
    public AttackResult attack(PlayerRecord player) {
        long guildId = requireGuildId(player.id());
        ensureBoss(guildId);
        staminaService.consume(player.id(), PLAYER_ATTACK_STAMINA); // throws if not enough
        long damage = rollDamage(inventoryService.combatPower(player), ThreadLocalRandom.current());
        DamageOutcome outcome = applyDamage(guildId, player.id(), damage);
        GuildBossView view = buildView(guildId, outcome.boss(), player.id());
        return new AttackResult(outcome.applied(), outcome.killed(), outcome.spawnedTier(), view);
    }

    /** Robot attack via the unified decision brain. Returns false if the robot has no guild. */
    public boolean robotAttack(long robotId, int robotPower, Random random) {
        Long guildId = guildIdOf(robotId);
        if (guildId == null) {
            return false;
        }
        ensureBoss(guildId);
        applyDamage(guildId, robotId, rollDamage(robotPower, random));
        return true;
    }

    private long rollDamage(int power, Random random) {
        double variance = 0.9 + random.nextDouble() * 0.2;
        long raw = (long) (Math.max(1, power) * DMG_COEFF * variance);
        return Math.max(MIN_HIT, raw);
    }

    private DamageOutcome applyDamage(long guildId, long playerId, long damage) {
        Object monitor = bossMonitors.computeIfAbsent(guildId, key -> new Object());
        synchronized (monitor) {
            BossRow boss = aliveBoss(guildId, weekKey());
            if (boss == null) {
                boss = ensureBoss(guildId);
            }
            long applied = Math.min(damage, boss.hpCurrent());
            long newHp = boss.hpCurrent() - applied;
            boolean killed = newHp <= 0;
            jdbcTemplate.update(
                "UPDATE guild_boss SET hp_current = ?, status = ?, killed_at = ? WHERE id = ?",
                newHp,
                killed ? "killed" : "alive",
                killed ? Timestamp.from(Instant.now()) : null,
                boss.id()
            );
            recordContribution(guildId, playerId, applied);
            if (killed) {
                jdbcTemplate.update(
                    "INSERT INTO chat_message (sender_name, kind, text, channel) VALUES ('公会战报', 'system', ?, ?)",
                    "【" + boss.name() + "】被公会击败！更强的挑战即将降临。",
                    GuildService.channelFor(guildId)
                );
                BossRow next = ensureBoss(guildId);
                return new DamageOutcome(next, applied, true, next.tier());
            }
            return new DamageOutcome(boss.withHp(newHp), applied, false, 0);
        }
    }

    private void recordContribution(long guildId, long playerId, long applied) {
        jdbcTemplate.update(
            """
            INSERT INTO guild_boss_contribution (guild_id, week_key, player_id, damage, attempts)
            VALUES (?, ?, ?, ?, 1)
            ON DUPLICATE KEY UPDATE damage = damage + VALUES(damage), attempts = attempts + 1
            """,
            guildId,
            weekKey(),
            playerId,
            applied
        );
        jdbcTemplate.update(
            "UPDATE guild_member SET weekly_contribution = weekly_contribution + ?, total_contribution = total_contribution + ? WHERE player_id = ?",
            applied,
            applied,
            playerId
        );
        jdbcTemplate.update(
            "UPDATE guild SET total_contribution = total_contribution + ? WHERE id = ?",
            applied,
            guildId
        );
    }

    private BossRow ensureBoss(long guildId) {
        String week = weekKey();
        BossRow alive = aliveBoss(guildId, week);
        if (alive != null) {
            return alive;
        }
        Object monitor = bossMonitors.computeIfAbsent(guildId, key -> new Object());
        synchronized (monitor) {
            alive = aliveBoss(guildId, week);
            if (alive != null) {
                return alive;
            }
            Integer maxTier = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(tier), 0) FROM guild_boss WHERE guild_id = ? AND week_key = ?",
                Integer.class,
                guildId,
                week
            );
            int tier = (maxTier == null ? 0 : maxTier) + 1;
            int members = Math.max(1, memberCount(guildId));
            double mult = 1 + 0.5 * (tier - 1);
            long hp = (long) (BASE_HP_PER_MEMBER * members * mult);
            String baseName = BOSS_NAMES.get(Math.min(tier - 1, BOSS_NAMES.size() - 1));
            String name = tier > BOSS_NAMES.size() ? baseName + " +" + (tier - BOSS_NAMES.size()) : baseName;
            jdbcTemplate.update(
                """
                INSERT INTO guild_boss (guild_id, week_key, tier, name, hp_max, hp_current, status)
                VALUES (?, ?, ?, ?, ?, ?, 'alive')
                """,
                guildId,
                week,
                tier,
                name,
                hp,
                hp
            );
            return aliveBoss(guildId, week);
        }
    }

    private BossRow aliveBoss(long guildId, String week) {
        return jdbcTemplate.query(
            """
            SELECT id, guild_id, week_key, tier, name, hp_max, hp_current, status
            FROM guild_boss
            WHERE guild_id = ? AND week_key = ? AND status = 'alive'
            ORDER BY tier DESC LIMIT 1
            """,
            rs -> rs.next()
                ? new BossRow(
                    rs.getLong("id"),
                    rs.getLong("guild_id"),
                    rs.getString("week_key"),
                    rs.getInt("tier"),
                    rs.getString("name"),
                    rs.getLong("hp_max"),
                    rs.getLong("hp_current"),
                    rs.getString("status"))
                : null,
            guildId,
            week
        );
    }

    private GuildBossView buildView(long guildId, BossRow boss, long viewerId) {
        String week = weekKey();
        List<Contributor> top = jdbcTemplate.query(
            """
            SELECT c.player_id, p.name, p.controller_type, c.damage
            FROM guild_boss_contribution c
            JOIN player p ON p.id = c.player_id
            WHERE c.guild_id = ? AND c.week_key = ?
            ORDER BY c.damage DESC
            LIMIT 12
            """,
            (rs, rowNum) -> new Contributor(
                rs.getLong("player_id"),
                rs.getString("name"),
                "robot".equals(rs.getString("controller_type")) ? "robot" : "player",
                rs.getLong("damage"),
                rowNum + 1
            ),
            guildId,
            week
        );
        Long myDamage = jdbcTemplate.query(
            "SELECT damage FROM guild_boss_contribution WHERE guild_id = ? AND week_key = ? AND player_id = ?",
            rs -> rs.next() ? rs.getLong("damage") : 0L,
            guildId,
            week,
            viewerId
        );
        long mine = myDamage == null ? 0L : myDamage;
        Integer ahead = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM guild_boss_contribution WHERE guild_id = ? AND week_key = ? AND damage > ?",
            Integer.class,
            guildId,
            week,
            mine
        );
        int myRank = mine <= 0 ? 0 : (ahead == null ? 0 : ahead) + 1;
        BossInfo info = new BossInfo(boss.id(), boss.name(), boss.tier(), boss.hpCurrent(), boss.hpMax(), boss.status(), week);
        return new GuildBossView(info, top, mine, myRank);
    }

    private int memberCount(long guildId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM guild_member WHERE guild_id = ?", Integer.class, guildId);
        return count == null ? 0 : count;
    }

    private long requireGuildId(long playerId) {
        Long guildId = guildIdOf(playerId);
        if (guildId == null) {
            throw ApiException.badRequest("加入公会后才能挑战公会 Boss。");
        }
        return guildId;
    }

    private Long guildIdOf(long playerId) {
        return jdbcTemplate.query(
            "SELECT guild_id FROM guild_member WHERE player_id = ?",
            rs -> rs.next() ? rs.getLong("guild_id") : null,
            playerId
        );
    }

    private String weekKey() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        return now.getYear() + "-W" + String.format("%02d", now.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    private record BossRow(long id, long guildId, String weekKey, int tier, String name, long hpMax, long hpCurrent, String status) {
        BossRow withHp(long hp) {
            return new BossRow(id, guildId, weekKey, tier, name, hpMax, hp, status);
        }
    }

    private record DamageOutcome(BossRow boss, long applied, boolean killed, int spawnedTier) {
    }

    public record BossInfo(long id, String name, int tier, long hpCurrent, long hpMax, String status, String weekKey) {
    }

    public record Contributor(long playerId, String name, String kind, long damage, int rank) {
    }

    public record GuildBossView(BossInfo boss, List<Contributor> topContributors, long myDamage, int myRank) {
    }

    public record AttackResult(long damage, boolean killed, int spawnedTier, GuildBossView view) {
    }
}
