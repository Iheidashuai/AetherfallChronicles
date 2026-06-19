package com.mythicrealm.domain.arena;

import com.mythicrealm.common.exception.BusinessException;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaBattleEvent;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaBattleResult;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaFighter;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaFighterSnapshot;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaMatchDetail;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaMatchSummary;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaOpponentView;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaOverview;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaPlayer;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaProfileView;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaShopOffer;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaShopPurchaseResult;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaActivityPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaCombatPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaLoadoutPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaPlayerPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaRewardPort;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArenaService {
    public static final int DAILY_ATTEMPTS = 5;
    private static final ZoneId GAME_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final ArenaPlayerPort playerPort;
    private final ArenaLoadoutPort loadoutPort;
    private final ArenaCombatPort combatPort;
    private final ArenaRewardPort rewardPort;
    private final ArenaActivityPort activityPort;

    public ArenaService(
        JdbcTemplate jdbcTemplate,
        ArenaPlayerPort playerPort,
        ArenaLoadoutPort loadoutPort,
        ArenaCombatPort combatPort,
        ArenaRewardPort rewardPort,
        ArenaActivityPort activityPort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerPort = playerPort;
        this.loadoutPort = loadoutPort;
        this.combatPort = combatPort;
        this.rewardPort = rewardPort;
        this.activityPort = activityPort;
    }

    @Transactional
    public ArenaOverview overview(long playerId) {
        ArenaPlayer player = playerPort.requirePlayer(playerId);
        ensureProfile(player.id());
        resetAttemptsIfNeeded(player.id());
        ArenaProfileView profile = profile(player);
        return new ArenaOverview(
            profile,
            opponents(player, profile),
            recentMatches(player.id(), 8),
            shopOffers(profile),
            rankings(player.id(), 20)
        );
    }

    @Transactional
    public ArenaMatchDetail challenge(long attackerId, long defenderId) {
        if (attackerId == defenderId) {
            throw new BusinessException("不能挑战自己");
        }
        ArenaPlayer attacker = playerPort.requirePlayer(attackerId);
        ArenaPlayer defender = playerPort.findPlayer(defenderId).orElseThrow(() -> new BusinessException("目标不存在"));
        ensureProfile(attacker.id());
        ensureProfile(defender.id());
        resetAttemptsIfNeeded(attacker.id());

        ProfileRow attackerProfile = profileRow(attacker.id());
        if (attackerProfile.todayAttemptsUsed() >= DAILY_ATTEMPTS) {
            throw new BusinessException("今日挑战次数不足");
        }

        ArenaFighter attackerFighter = loadoutPort.fighter(attacker.id());
        ArenaFighter defenderFighter = loadoutPort.fighter(defender.id());
        long seed = Math.abs(Objects.hash(attacker.id(), defender.id(), System.nanoTime()));
        ArenaBattleResult battle = combatPort.fight(attackerFighter, defenderFighter, new Random(seed));
        boolean attackerWon = battle.attackerWon();
        int attackerDelta = attackerWon ? ratingGain(attackerProfile.rating(), profileRow(defender.id()).rating()) : -ratingLoss(attackerProfile.rating(), profileRow(defender.id()).rating());
        int defenderDelta = attackerWon ? -Math.max(4, attackerDelta / 2) : Math.max(12, ratingGain(profileRow(defender.id()).rating(), attackerProfile.rating()) / 2);
        int coins = attackerWon ? 35 : 12;

        jdbcTemplate.update(
            """
            UPDATE arena_profile
            SET rating = GREATEST(0, rating + ?), arena_coins = arena_coins + ?, today_attempts_used = today_attempts_used + 1,
                wins = wins + ?, losses = losses + ?, win_streak = ?, updated_at = CURRENT_TIMESTAMP
            WHERE player_id = ?
            """,
            attackerDelta,
            coins,
            attackerWon ? 1 : 0,
            attackerWon ? 0 : 1,
            attackerWon ? attackerProfile.winStreak() + 1 : 0,
            attacker.id()
        );
        jdbcTemplate.update(
            """
            UPDATE arena_profile
            SET rating = GREATEST(0, rating + ?), wins = wins + ?, losses = losses + ?, win_streak = ?, updated_at = CURRENT_TIMESTAMP
            WHERE player_id = ?
            """,
            defenderDelta,
            attackerWon ? 0 : 1,
            attackerWon ? 1 : 0,
            attackerWon ? 0 : profileRow(defender.id()).winStreak() + 1,
            defender.id()
        );

        long matchId = insertMatch(attacker, defender, battle, attackerDelta, defenderDelta, coins);
        insertParticipant(matchId, "attacker", attackerFighter);
        insertParticipant(matchId, "defender", defenderFighter);
        insertEvents(matchId, battle.events());

        String resultText = attackerWon ? "击败 " + defender.name() + "，夺得 " + coins + " 枚竞技币。" : "惜败于 " + defender.name() + "，获得 " + coins + " 枚竞技币。";
        activityPort.recordActivity(attacker.id(), "arena", "竞技场：" + resultText);
        activityPort.recordActivity(defender.id(), "arena", "竞技场防守：" + (attackerWon ? "被 " + attacker.name() + " 击破。" : "守住了 " + attacker.name() + " 的挑战。"));
        activityPort.recordQuestEvent(attacker.id(), "arenaChallenge", 1);
        if (attackerWon) {
            activityPort.recordQuestEvent(attacker.id(), "arenaWin", 1);
        }
        return matchDetail(attacker.id(), matchId);
    }

    public ArenaMatchDetail matchDetail(long viewerId, long matchId) {
        ArenaMatchSummary summary = matchSummary(matchId);
        if (summary.attackerId() != viewerId && summary.defenderId() != viewerId) {
            playerPort.requirePlayer(viewerId);
        }
        ArenaPlayer player = playerPort.requirePlayer(viewerId);
        return new ArenaMatchDetail(
            summary,
            profile(player),
            participant(matchId, "attacker"),
            participant(matchId, "defender"),
            events(matchId)
        );
    }

    @Transactional
    public ArenaShopPurchaseResult buyShopOffer(long playerId, String offerId) {
        ArenaPlayer player = playerPort.requirePlayer(playerId);
        ensureProfile(player.id());
        resetAttemptsIfNeeded(player.id());
        ProfileRow profile = profileRow(player.id());
        ShopRow offer = shopRow(offerId);
        if (profile.rating() < offer.requiredRating()) {
            throw new BusinessException("段位不足，暂未解锁该奖励");
        }
        if (profile.arenaCoins() < offer.priceCoins()) {
            throw new BusinessException("竞技币不足，需要 " + offer.priceCoins() + " 枚");
        }
        jdbcTemplate.update("UPDATE arena_profile SET arena_coins = arena_coins - ?, updated_at = CURRENT_TIMESTAMP WHERE player_id = ?", offer.priceCoins(), player.id());
        List<String> rewards = rewardPort.grantItem(player.id(), offer.itemTemplateId(), offer.itemQuantity(), new Random(Objects.hash(player.id(), offerId, System.nanoTime())));
        jdbcTemplate.update(
            """
            INSERT INTO arena_shop_purchase (player_id, offer_id, price_coins, item_template_id, item_quantity)
            VALUES (?, ?, ?, ?, ?)
            """,
            player.id(),
            offer.id(),
            offer.priceCoins(),
            offer.itemTemplateId(),
            offer.itemQuantity()
        );
        activityPort.recordActivity(player.id(), "arena_shop", "在竞技场商店兑换【" + offer.name() + "】。");
        ArenaProfileView updated = profile(player);
        return new ArenaShopPurchaseResult(shopOffer(offer, updated), updated, rewards);
    }

    public List<ArenaProfileView> rankings(long viewerId, int limit) {
        playerPort.requirePlayer(viewerId);
        return jdbcTemplate.query(
                """
                SELECT ap.player_id
                FROM arena_profile ap
                JOIN player p ON p.id = ap.player_id
                ORDER BY ap.rating DESC, ap.wins DESC, p.level DESC, ap.updated_at ASC
                LIMIT ?
                """,
                (rs, rowNum) -> playerPort.requirePlayer(rs.getLong("player_id")),
                Math.max(1, limit)
            )
            .stream()
            .map(this::profile)
            .toList();
    }

    private List<ArenaOpponentView> opponents(ArenaPlayer player, ArenaProfileView profile) {
        return playerPort.activePlayers(80).stream()
            .filter(candidate -> candidate.id() != player.id())
            .peek(candidate -> ensureProfile(candidate.id()))
            .map(candidate -> opponent(candidate, profile))
            .sorted(Comparator.comparingInt((ArenaOpponentView opponent) -> Math.abs(opponent.rating() - profile.rating()))
                .thenComparing(ArenaOpponentView::combatPower))
            .limit(9)
            .toList();
    }

    private ArenaOpponentView opponent(ArenaPlayer player, ArenaProfileView viewer) {
        ArenaProfileView target = profile(player);
        String disabled = viewer.todayAttemptsUsed() >= DAILY_ATTEMPTS ? "今日挑战次数不足" : null;
        int diff = target.combatPower() - viewer.combatPower();
        String hint = diff > 8000 ? "强敌" : diff > 1500 ? "有压力" : diff < -3000 ? "稳胜" : "势均力敌";
        return new ArenaOpponentView(
            target.playerId(),
            target.name(),
            target.profession(),
            target.level(),
            target.controllerType(),
            target.combatPower(),
            target.rating(),
            target.tier(),
            target.rank(),
            target.wins(),
            target.losses(),
            hint,
            disabled == null,
            disabled
        );
    }

    private ArenaProfileView profile(ArenaPlayer player) {
        ensureProfile(player.id());
        ProfileRow row = profileRow(player.id());
        ArenaFighter fighter = loadoutPort.fighter(player.id());
        return new ArenaProfileView(
            player.id(),
            player.name(),
            player.profession(),
            player.level(),
            player.controllerType(),
            fighter.combatPower(),
            row.rating(),
            tier(row.rating()),
            rank(player.id()),
            row.arenaCoins(),
            row.todayAttemptsUsed(),
            DAILY_ATTEMPTS,
            row.wins(),
            row.losses(),
            row.winStreak()
        );
    }

    private void ensureProfile(long playerId) {
        jdbcTemplate.update(
            """
            INSERT INTO arena_profile (player_id, last_attempt_day)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE player_id = player_id
            """,
            playerId,
            LocalDate.now(GAME_ZONE).toString()
        );
    }

    private void resetAttemptsIfNeeded(long playerId) {
        jdbcTemplate.update(
            """
            UPDATE arena_profile
            SET today_attempts_used = 0, last_attempt_day = ?
            WHERE player_id = ? AND last_attempt_day <> ?
            """,
            LocalDate.now(GAME_ZONE).toString(),
            playerId,
            LocalDate.now(GAME_ZONE).toString()
        );
    }

    private int rank(long playerId) {
        Integer rank = jdbcTemplate.queryForObject(
            """
            SELECT ranked.rank_no
            FROM (
                SELECT player_id, ROW_NUMBER() OVER (ORDER BY rating DESC, wins DESC, updated_at ASC) AS rank_no
                FROM arena_profile
            ) ranked
            WHERE ranked.player_id = ?
            """,
            Integer.class,
            playerId
        );
        return rank == null ? 0 : rank;
    }

    private ProfileRow profileRow(long playerId) {
        return jdbcTemplate.query(
            "SELECT * FROM arena_profile WHERE player_id = ?",
            (rs, rowNum) -> new ProfileRow(
                rs.getLong("player_id"),
                rs.getInt("rating"),
                rs.getInt("arena_coins"),
                rs.getInt("today_attempts_used"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getInt("win_streak")
            ),
            playerId
        ).stream().findFirst().orElseThrow(() -> new BusinessException("竞技场档案不存在"));
    }

    private long insertMatch(ArenaPlayer attacker, ArenaPlayer defender, ArenaBattleResult battle, int attackerDelta, int defenderDelta, int coins) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO arena_match
                (attacker_id, defender_id, attacker_won, round_limit_reached, attacker_hp, defender_hp,
                 attacker_rating_change, defender_rating_change, arena_coins, result_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, attacker.id());
            ps.setLong(2, defender.id());
            ps.setBoolean(3, battle.attackerWon());
            ps.setBoolean(4, battle.roundLimitReached());
            ps.setInt(5, battle.attackerHp());
            ps.setInt(6, battle.defenderHp());
            ps.setInt(7, attackerDelta);
            ps.setInt(8, defenderDelta);
            ps.setInt(9, coins);
            ps.setString(10, battle.attackerWon() ? attacker.name() + " 进攻胜利" : defender.name() + " 防守成功");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertParticipant(long matchId, String side, ArenaFighter fighter) {
        jdbcTemplate.update(
            """
            INSERT INTO arena_match_participant
            (match_id, side, player_id, name, profession, level, combat_power, max_hp, attack_power,
             armor, resistance, build_name, strategy, equipment_summary, skill_summary)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            matchId,
            side,
            fighter.playerId(),
            fighter.name(),
            fighter.profession(),
            fighter.level(),
            fighter.combatPower(),
            fighter.stats().maxHp(),
            fighter.stats().attackPower(),
            fighter.stats().armor(),
            fighter.stats().resistance(),
            fighter.buildName(),
            fighter.strategy(),
            String.join(" | ", fighter.equipmentSummary()),
            String.join(" | ", fighter.skills().stream().map(skill -> skill.name()).toList())
        );
    }

    private void insertEvents(long matchId, List<ArenaBattleEvent> events) {
        for (ArenaBattleEvent event : events) {
            jdbcTemplate.update(
                """
                INSERT INTO arena_match_event
                (match_id, sequence_no, actor, event_type, tone, text, attacker_hp, defender_hp,
                 damage, critical, missed, skill_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                matchId,
                event.sequenceNo(),
                event.actor(),
                event.eventType(),
                event.tone(),
                event.text(),
                event.attackerHp(),
                event.defenderHp(),
                event.damage(),
                event.critical(),
                event.missed(),
                event.skillName()
            );
        }
    }

    private List<ArenaMatchSummary> recentMatches(long playerId, int limit) {
        return jdbcTemplate.query(
            """
            SELECT m.*, attacker.name AS attacker_name, defender.name AS defender_name
            FROM arena_match m
            JOIN player attacker ON attacker.id = m.attacker_id
            JOIN player defender ON defender.id = m.defender_id
            WHERE m.attacker_id = ? OR m.defender_id = ?
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new ArenaMatchSummary(
                rs.getLong("id"),
                rs.getLong("attacker_id"),
                rs.getString("attacker_name"),
                rs.getLong("defender_id"),
                rs.getString("defender_name"),
                rs.getBoolean("attacker_won"),
                rs.getInt("attacker_rating_change"),
                rs.getInt("defender_rating_change"),
                rs.getInt("arena_coins"),
                rs.getString("result_text"),
                rs.getTimestamp("created_at").toInstant()
            ),
            playerId,
            playerId,
            limit
        );
    }

    private ArenaMatchSummary matchSummary(long matchId) {
        return jdbcTemplate.query(
            """
            SELECT m.*, attacker.name AS attacker_name, defender.name AS defender_name
            FROM arena_match m
            JOIN player attacker ON attacker.id = m.attacker_id
            JOIN player defender ON defender.id = m.defender_id
            WHERE m.id = ?
            """,
            (rs, rowNum) -> new ArenaMatchSummary(
                rs.getLong("id"),
                rs.getLong("attacker_id"),
                rs.getString("attacker_name"),
                rs.getLong("defender_id"),
                rs.getString("defender_name"),
                rs.getBoolean("attacker_won"),
                rs.getInt("attacker_rating_change"),
                rs.getInt("defender_rating_change"),
                rs.getInt("arena_coins"),
                rs.getString("result_text"),
                rs.getTimestamp("created_at").toInstant()
            ),
            matchId
        ).stream().findFirst().orElseThrow(() -> new BusinessException("战报不存在"));
    }

    private ArenaFighterSnapshot participant(long matchId, String side) {
        return jdbcTemplate.query(
            "SELECT * FROM arena_match_participant WHERE match_id = ? AND side = ?",
            (rs, rowNum) -> new ArenaFighterSnapshot(
                rs.getLong("player_id"),
                rs.getString("name"),
                rs.getString("profession"),
                rs.getInt("level"),
                rs.getInt("combat_power"),
                rs.getInt("max_hp"),
                rs.getInt("attack_power"),
                rs.getInt("armor"),
                rs.getInt("resistance"),
                rs.getString("build_name"),
                rs.getString("strategy"),
                splitSummary(rs.getString("equipment_summary")),
                splitSummary(rs.getString("skill_summary"))
            ),
            matchId,
            side
        ).stream().findFirst().orElseThrow(() -> new BusinessException("战斗快照不存在"));
    }

    private List<ArenaBattleEvent> events(long matchId) {
        return jdbcTemplate.query(
            "SELECT * FROM arena_match_event WHERE match_id = ? ORDER BY sequence_no",
            (rs, rowNum) -> new ArenaBattleEvent(
                rs.getInt("sequence_no"),
                rs.getString("actor"),
                rs.getString("event_type"),
                rs.getString("tone"),
                rs.getString("text"),
                rs.getInt("attacker_hp"),
                rs.getInt("defender_hp"),
                rs.getInt("damage"),
                rs.getBoolean("critical"),
                rs.getBoolean("missed"),
                rs.getString("skill_name")
            ),
            matchId
        );
    }

    private List<ArenaShopOffer> shopOffers(ArenaProfileView profile) {
        return jdbcTemplate.query(
            "SELECT * FROM arena_shop_offer WHERE enabled = TRUE ORDER BY sort_order, price_coins",
            (rs, rowNum) -> shopOffer(new ShopRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("item_template_id"),
                rs.getInt("item_quantity"),
                rs.getInt("price_coins"),
                rs.getInt("required_rating"),
                rs.getInt("sort_order")
            ), profile)
        );
    }

    private ShopRow shopRow(String offerId) {
        return jdbcTemplate.query(
            "SELECT * FROM arena_shop_offer WHERE id = ? AND enabled = TRUE",
            (rs, rowNum) -> new ShopRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("item_template_id"),
                rs.getInt("item_quantity"),
                rs.getInt("price_coins"),
                rs.getInt("required_rating"),
                rs.getInt("sort_order")
            ),
            offerId
        ).stream().findFirst().orElseThrow(() -> new BusinessException("竞技场商店商品不存在"));
    }

    private ArenaShopOffer shopOffer(ShopRow row, ArenaProfileView profile) {
        boolean unlocked = profile.rating() >= row.requiredRating();
        boolean affordable = profile.arenaCoins() >= row.priceCoins();
        String disabled = !unlocked ? "需要 " + tier(row.requiredRating()) : !affordable ? "竞技币不足" : null;
        return new ArenaShopOffer(row.id(), row.name(), row.description(), row.itemTemplateId(), row.itemQuantity(), row.priceCoins(), row.requiredRating(), row.sortOrder(), affordable, unlocked, disabled);
    }

    private int ratingGain(int ownRating, int targetRating) {
        return Math.max(18, Math.min(42, 26 + (targetRating - ownRating) / 35));
    }

    private int ratingLoss(int ownRating, int targetRating) {
        return Math.max(8, Math.min(24, 14 + (ownRating - targetRating) / 80));
    }

    private String tier(int rating) {
        if (rating >= 1900) {
            return "王者";
        }
        if (rating >= 1600) {
            return "钻石";
        }
        if (rating >= 1350) {
            return "黄金";
        }
        if (rating >= 1150) {
            return "白银";
        }
        return "青铜";
    }

    private List<String> splitSummary(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split(" \\| "));
    }

    private record ProfileRow(long playerId, int rating, int arenaCoins, int todayAttemptsUsed, int wins, int losses, int winStreak) {
    }

    private record ShopRow(String id, String name, String description, String itemTemplateId, int itemQuantity, int priceCoins, int requiredRating, int sortOrder) {
    }
}
