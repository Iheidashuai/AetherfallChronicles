package com.mythicrealm.api.gameplay.recharge;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.recharge.WealthTierService.WealthTier;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeService {
    public static final long GOLD_PER_RMB = 1_000L;

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final WealthTierService wealthTierService;
    private final Random random = new Random();

    public RechargeService(
        JdbcTemplate jdbcTemplate,
        PlayerService playerService,
        WealthTierService wealthTierService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
        this.wealthTierService = wealthTierService;
    }

    @Transactional
    public RechargeResult recharge(PlayerRecord player, long rmbAmount, String reason, String sourceAction) {
        if (rmbAmount <= 0) {
            throw ApiException.badRequest("充值金额必须大于 0 元");
        }
        PlayerRecord current = playerService.requireById(player.id());
        if (current.realMoney() < rmbAmount) {
            throw ApiException.badRequest("真实余额不足，当前只有 " + current.realMoney() + " 元");
        }
        return performRecharge(current, rmbAmount, reason, sourceAction);
    }

    @Transactional
    public Optional<RechargeResult> rechargeForGoldNeed(long playerId, long requiredGold, String sourceAction, String reason) {
        PlayerRecord player = playerService.requireById(playerId);
        if (player.gold() >= requiredGold) {
            return Optional.empty();
        }
        long missingGold = requiredGold - player.gold();
        long minimumRmb = ceilDiv(missingGold, GOLD_PER_RMB);
        WealthTier tier = wealthTierService.tier(player.wealthTierLevel());
        long preferredRmb = Math.max(minimumRmb, Math.round(minimumRmb * tier.rechargeMultiplier()));
        long rmbAmount = Math.min(player.realMoney(), preferredRmb);
        if (rmbAmount < minimumRmb) {
            rmbAmount = Math.min(player.realMoney(), minimumRmb);
        }
        if (rmbAmount <= 0) {
            return Optional.empty();
        }
        return Optional.of(performRecharge(player, rmbAmount, reason, sourceAction));
    }

    @Transactional
    public RechargeDashboard dashboard(PlayerRecord viewer) {
        PlayerRecord player = playerService.requireById(viewer.id());
        RechargeTotals totals = loadTotals();
        List<RobotRechargeRow> robotRows = jdbcTemplate.query(
            """
            SELECT ro.id, ro.player_id, ro.player_name, ro.wealth_tier_level, ro.wealth_tier,
                   ro.rmb_amount, ro.gold_amount, ro.reason, ro.source_action, ro.created_at,
                   p.gold, p.real_money
            FROM recharge_order ro
            JOIN player p ON p.id = ro.player_id
            WHERE p.controller_type = 'robot'
            ORDER BY ro.created_at DESC, ro.id DESC
            LIMIT 180
            """,
            (rs, rowNum) -> new RobotRechargeRow(
                rs.getLong("id"),
                rs.getLong("player_id"),
                rs.getString("player_name"),
                rs.getInt("wealth_tier_level"),
                rs.getString("wealth_tier"),
                rs.getLong("rmb_amount"),
                rs.getLong("gold_amount"),
                rs.getString("reason"),
                rs.getString("source_action"),
                rs.getLong("gold"),
                rs.getLong("real_money"),
                rs.getTimestamp("created_at").toInstant()
            )
        );
        List<WealthTierStat> tierStats = jdbcTemplate.query(
            """
            SELECT wealth_tier_level, wealth_tier, COUNT(*) AS player_count,
                   COALESCE(SUM(real_money), 0) AS real_money_total,
                   COALESCE(SUM(gold), 0) AS gold_total
            FROM player
            GROUP BY wealth_tier_level, wealth_tier
            ORDER BY wealth_tier_level
            """,
            (rs, rowNum) -> {
                int tierLevel = rs.getInt("wealth_tier_level");
                WealthTier tier = wealthTierService.tier(tierLevel);
                return new WealthTierStat(
                    tierLevel,
                    rs.getString("wealth_tier"),
                    rs.getInt("player_count"),
                    rs.getLong("real_money_total"),
                    rs.getLong("gold_total"),
                    tier.minIncome(),
                    tier.maxIncome()
                );
            }
        );
        List<CashIncomeRow> incomeRows = jdbcTemplate.query(
            """
            SELECT cie.id, cie.player_id, cie.player_name, cie.wealth_tier_level, cie.wealth_tier,
                   cie.rmb_amount, cie.created_at
            FROM cash_income_event cie
            ORDER BY cie.created_at DESC, cie.id DESC
            LIMIT 120
            """,
            (rs, rowNum) -> new CashIncomeRow(
                rs.getLong("id"),
                rs.getLong("player_id"),
                rs.getString("player_name"),
                rs.getInt("wealth_tier_level"),
                rs.getString("wealth_tier"),
                rs.getLong("rmb_amount"),
                rs.getTimestamp("created_at").toInstant()
            )
        );
        return new RechargeDashboard(wallet(player), totals, tierStats, robotRows, incomeRows);
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 180_000)
    @Transactional
    public void grantIncomeTick() {
        List<PlayerIncomeTarget> players = jdbcTemplate.query(
            """
            SELECT id, name, controller_type, wealth_tier_level, wealth_tier
            FROM player
            ORDER BY id
            """,
            (rs, rowNum) -> new PlayerIncomeTarget(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("controller_type"),
                rs.getInt("wealth_tier_level"),
                rs.getString("wealth_tier")
            )
        );
        for (PlayerIncomeTarget player : players) {
            WealthTier tier = wealthTierService.tier(player.wealthTierLevel());
            long income = wealthTierService.incomeFor(tier.level(), random);
            jdbcTemplate.update(
                "UPDATE player SET real_money = real_money + ?, last_income_at = CURRENT_TIMESTAMP WHERE id = ?",
                income,
                player.id()
            );
            jdbcTemplate.update(
                """
                INSERT INTO cash_income_event
                (player_id, player_name, controller_type, wealth_tier_level, wealth_tier, rmb_amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                player.id(),
                player.name(),
                player.controllerType(),
                tier.level(),
                tier.name(),
                income
            );
        }
        jdbcTemplate.update(
            "DELETE FROM cash_income_event WHERE id NOT IN (SELECT id FROM (SELECT id FROM cash_income_event ORDER BY created_at DESC, id DESC LIMIT 2000) recent)"
        );
    }

    public RechargeWallet wallet(PlayerRecord player) {
        WealthTier tier = wealthTierService.tier(player.wealthTierLevel());
        return new RechargeWallet(
            player.id(),
            player.name(),
            player.gold(),
            player.realMoney(),
            tier.level(),
            tier.code(),
            tier.name(),
            tier.minIncome(),
            tier.maxIncome(),
            GOLD_PER_RMB
        );
    }

    private RechargeResult performRecharge(PlayerRecord player, long rmbAmount, String reason, String sourceAction) {
        long goldAmount = safeMultiply(rmbAmount, GOLD_PER_RMB);
        WealthTier tier = wealthTierService.tier(player.wealthTierLevel());
        jdbcTemplate.update(
            """
            UPDATE player
            SET real_money = real_money - ?, gold = gold + ?, last_recharge_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            rmbAmount,
            goldAmount,
            player.id()
        );
        jdbcTemplate.update(
            """
            INSERT INTO recharge_order
            (player_id, player_name, controller_type, wealth_tier_level, wealth_tier, rmb_amount, gold_amount, reason, source_action)
            SELECT id, name, controller_type, ?, ?, ?, ?, ?, ?
            FROM player
            WHERE id = ?
            """,
            tier.level(),
            tier.name(),
            rmbAmount,
            goldAmount,
            normalize(reason, "manual_click"),
            normalize(sourceAction, "manual"),
            player.id()
        );
        PlayerRecord updated = playerService.requireById(player.id());
        return new RechargeResult(updated, rmbAmount, goldAmount, normalize(reason, "manual_click"), normalize(sourceAction, "manual"), wallet(updated));
    }

    private RechargeTotals loadTotals() {
        Long totalRmb = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(rmb_amount), 0) FROM recharge_order", Long.class);
        Long totalGold = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(gold_amount), 0) FROM recharge_order", Long.class);
        Long marketListedGold = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(price), 0) FROM market_listing WHERE status = 'listed'", Long.class);
        Long marketSoldGold = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(price), 0) FROM market_listing WHERE status = 'sold'", Long.class);
        Long robotGold = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(gold), 0) FROM player WHERE controller_type = 'robot'", Long.class);
        Long robotRealMoney = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(real_money), 0) FROM player WHERE controller_type = 'robot'", Long.class);
        Long allGold = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(gold), 0) FROM player", Long.class);
        return new RechargeTotals(
            value(totalRmb),
            value(totalGold),
            value(marketListedGold),
            value(marketSoldGold),
            value(robotGold),
            value(robotRealMoney),
            value(allGold)
        );
    }

    private long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException error) {
            throw ApiException.badRequest("充值金额过大");
        }
    }

    private long ceilDiv(long left, long right) {
        return (left + right - 1) / right;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record PlayerIncomeTarget(long id, String name, String controllerType, int wealthTierLevel, String wealthTier) {
    }

    public record RechargeResult(
        PlayerRecord player,
        long rmbAmount,
        long goldAmount,
        String reason,
        String sourceAction,
        RechargeWallet wallet
    ) {
    }

    public record RechargeWallet(
        long playerId,
        String playerName,
        long gold,
        long realMoney,
        int wealthTierLevel,
        String wealthTierCode,
        String wealthTier,
        long minIncome,
        long maxIncome,
        long exchangeRate
    ) {
    }

    public record RechargeDashboard(
        RechargeWallet wallet,
        RechargeTotals totals,
        List<WealthTierStat> tierStats,
        List<RobotRechargeRow> robotRecharges,
        List<CashIncomeRow> incomeEvents
    ) {
    }

    public record RechargeTotals(
        long totalRmb,
        long totalGold,
        long marketListedGold,
        long marketSoldGold,
        long robotGold,
        long robotRealMoney,
        long allPlayerGold
    ) {
    }

    public record WealthTierStat(
        int wealthTierLevel,
        String wealthTier,
        int playerCount,
        long realMoneyTotal,
        long goldTotal,
        long minIncome,
        long maxIncome
    ) {
    }

    public record RobotRechargeRow(
        long id,
        long playerId,
        String playerName,
        int wealthTierLevel,
        String wealthTier,
        long rmbAmount,
        long goldAmount,
        String reason,
        String sourceAction,
        long currentGold,
        long currentRealMoney,
        Instant createdAt
    ) {
    }

    public record CashIncomeRow(
        long id,
        long playerId,
        String playerName,
        int wealthTierLevel,
        String wealthTier,
        long rmbAmount,
        Instant createdAt
    ) {
    }
}
