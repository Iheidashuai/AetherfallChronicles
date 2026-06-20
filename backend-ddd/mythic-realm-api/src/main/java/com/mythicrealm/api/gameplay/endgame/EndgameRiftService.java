package com.mythicrealm.api.gameplay.endgame;

import com.mythicrealm.api.gameplay.combat.CombatStats;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.build.BuildCombatPlanService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.endgame.combat.BuildCombatPlan;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine.BattleEvent;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine.BattleRequest;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine.BattleResult;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine.BattleRoom;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine.BattleStats;
import com.mythicrealm.api.gameplay.endgame.combat.EndgameCombatEngine.UnitSpec;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndgameRiftService {
    public static final int UNLOCK_LEVEL = 60;
    private static final String UNLOCK_DUNGEON = "dungeon_48_star";
    private static final int STAMINA_COST = 3;
    private static final String ESSENCE = "mat_abyss_essence";
    private static final String SHARD = "mat_tempering_shard";
    private static final String ORB = "mat_reforge_orb";
    private static final String SOCKET_CORE = "mat_socket_core";
    private static final String GEM_DUST = "mat_gem_dust";
    private static final String ASCENSION_CORE = "mat_ascension_core";
    private static final String AFFIX_LOCK = "mat_affix_lock";
    private static final List<String> GEM_DROPS = List.of("gem_ruby_1", "gem_emerald_1", "gem_sapphire_1", "gem_topaz_1");
    private static final String WEEKLY_CHEST = "chest_abyss_weekly";

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final StaminaService staminaService;
    private final CombatStatsService combatStatsService;
    private final EndgameCombatEngine combatEngine;
    private final QuestService questService;
    private final BuildCombatPlanService buildCombatPlanService;

    public EndgameRiftService(
        JdbcTemplate jdbcTemplate,
        PlayerService playerService,
        InventoryService inventoryService,
        StaminaService staminaService,
        CombatStatsService combatStatsService,
        EndgameCombatEngine combatEngine,
        QuestService questService,
        BuildCombatPlanService buildCombatPlanService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.staminaService = staminaService;
        this.combatStatsService = combatStatsService;
        this.combatEngine = combatEngine;
        this.questService = questService;
        this.buildCombatPlanService = buildCombatPlanService;
    }

    public RiftSnapshot snapshot(PlayerRecord player) {
        boolean unlocked = unlocked(player);
        RiftProgress progress = progress(player.id());
        int bestTier = progress.bestTier();
        int nextTier = unlocked ? Math.max(1, bestTier + 1) : 0;
        List<Integer> challengeTiers = unlocked ? challengeTiers(bestTier) : List.of();
        int previewTier = Math.max(1, nextTier);
        return new RiftSnapshot(
            unlocked,
            unlocked ? "深渊裂隙已开启" : "需要 Lv." + UNLOCK_LEVEL + " 且通关星陨深渊·王座",
            UNLOCK_LEVEL,
            UNLOCK_DUNGEON,
            bestTier,
            progress.bestScore(),
            progress.bestRating(),
            bestWeekly(player.id()).tier(),
            challengeTiers,
            nextTier,
            STAMINA_COST,
            recommendedPower(previewTier),
            minimumPower(previewTier),
            modifiersForTier(previewTier),
            rewardPreview(previewTier, modifiersForTier(previewTier), "A"),
            tierPreviews(challengeTiers),
            materials(player.id()),
            staminaService.snapshot(player.id()),
            leaderboard(player.id(), 12),
            weeklyRewardAvailable(player.id())
        );
    }

    @Transactional
    public RiftRunResult run(PlayerRecord player, int tier, String requestId) {
        String normalizedRequestId = normalizeRequestId(requestId);
        if (normalizedRequestId != null) {
            RiftRunResult existing = existingRun(player.id(), normalizedRequestId);
            if (existing != null) {
                return existing;
            }
        }
        if (!unlocked(player)) {
            throw ApiException.badRequest("深渊裂隙尚未解锁");
        }
        RiftProgress progress = progress(player.id());
        int safeTier = Math.max(1, tier);
        if (safeTier > progress.bestTier() + 1) {
            throw ApiException.badRequest("不能跳层挑战，只能挑战已通关层或下一层");
        }
        int combatPower = inventoryService.combatPower(player);
        int recommendedPower = recommendedPower(safeTier);
        int minimumPower = minimumPower(safeTier);
        if (combatPower < minimumPower) {
            throw ApiException.badRequest("深渊战力不足，需要 " + minimumPower + " 战力");
        }
        StaminaSnapshot stamina = staminaService.consume(player.id(), STAMINA_COST);
        List<RiftModifier> modifiers = modifiersForTier(safeTier);
        List<String> modifierIds = modifiers.stream().map(RiftModifier::id).toList();
        long seed = Objects.hash(player.id(), safeTier, normalizedRequestId, System.nanoTime());
        BattleResult battle = combatEngine.run(new BattleRequest(
            player.name(),
            player.profession(),
            playerBattleStats(player),
            safeTier,
            modifierIds,
            roomsForTier(safeTier, recommendedPower),
            seed,
            buildCombatPlanService.activePlan(player)
        ));
        RiftReward reward = battle.success()
            ? rewardPreview(safeTier, modifiers, battle.rating())
            : new RiftReward(0, 0, 0, 1.0);
        if (battle.success()) {
            inventoryService.grantItem(player.id(), ESSENCE, reward.essence(), new Random(seed + 11));
            if (reward.shards() > 0) {
                inventoryService.grantItem(player.id(), SHARD, reward.shards(), new Random(seed + 17));
            }
            if (reward.orbs() > 0) {
                inventoryService.grantItem(player.id(), ORB, reward.orbs(), new Random(seed + 23));
            }
            grantProcessingDrops(player.id(), safeTier, battle.rating(), seed);
        }

        long runId = persistRun(player, safeTier, normalizedRequestId, battle, combatPower, recommendedPower, modifiers, reward, seed);
        persistEvents(runId, battle.events());
        if (battle.success()) {
            updateProgress(player.id(), safeTier, battle.score(), battle.rating(), runId);
            questService.recordEvent(player.id(), new QuestEvent("riftCompleted", null, 1));
            questService.recordEvent(player.id(), new QuestEvent("riftTierReached", null, safeTier));
        }
        return toRunResult(player.id(), runId, stamina);
    }

    public RiftSimulationResult simulate(PlayerRecord player, int tier, long buildId, BuildCombatPlan plan) {
        int safeTier = Math.max(1, tier);
        int recommendedPower = recommendedPower(safeTier);
        int combatPower = inventoryService.combatPower(player);
        List<RiftModifier> modifiers = modifiersForTier(safeTier);
        long seed = Objects.hash(player.id(), buildId, safeTier, "simulation", System.nanoTime());
        BattleResult battle = combatEngine.run(new BattleRequest(
            player.name(),
            player.profession(),
            playerBattleStats(player),
            safeTier,
            modifiers.stream().map(RiftModifier::id).toList(),
            roomsForTier(safeTier, recommendedPower),
            seed,
            plan
        ));
        long simulationId = persistSimulation(buildId, player.id(), safeTier, battle, combatPower);
        return new RiftSimulationResult(
            simulationId,
            safeTier,
            battle.success(),
            battle.rating(),
            battle.score(),
            battle.turnsTaken(),
            battle.monstersKilled(),
            combatPower,
            recommendedPower,
            battle.playerFinalHp(),
            battle.playerMaxHp(),
            battle.events()
        );
    }

    @Transactional
    public WeeklyRewardResult claimWeeklyReward(PlayerRecord player) {
        WeeklyBest weekly = bestWeekly(player.id());
        if (weekly.tier() <= 0 || weekly.runId() <= 0) {
            throw ApiException.badRequest("本周还没有可领取的深渊奖励");
        }
        String weekKey = weekKey();
        Integer claimed = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player_rift_weekly_reward WHERE player_id = ? AND week_key = ?",
            Integer.class,
            player.id(),
            weekKey
        );
        if (claimed != null && claimed > 0) {
            throw ApiException.badRequest("本周深渊奖励已领取");
        }
        int essence = 20 + weekly.tier() * 4;
        int shards = weekly.tier() >= 5 ? weekly.tier() / 2 : 0;
        int orbs = weekly.tier() >= 10 ? Math.max(1, weekly.tier() / 12) : 0;
        inventoryService.grantItem(player.id(), ESSENCE, essence, new Random(System.nanoTime()));
        if (shards > 0) {
            inventoryService.grantItem(player.id(), SHARD, shards, new Random(System.nanoTime() + 3));
        }
        if (orbs > 0) {
            inventoryService.grantItem(player.id(), ORB, orbs, new Random(System.nanoTime() + 7));
        }
        inventoryService.grantItem(player.id(), SOCKET_CORE, Math.max(1, weekly.tier() / 4), new Random(System.nanoTime() + 9));
        inventoryService.grantItem(player.id(), GEM_DUST, 8 + weekly.tier(), new Random(System.nanoTime() + 10));
        if (weekly.tier() >= 8) {
            inventoryService.grantItem(player.id(), ASCENSION_CORE, Math.max(1, weekly.tier() / 10), new Random(System.nanoTime() + 11));
        }
        if (weekly.tier() >= 10) {
            inventoryService.grantItem(player.id(), AFFIX_LOCK, 1, new Random(System.nanoTime() + 12));
        }
        ItemRecord chest = inventoryService.grantItem(player.id(), WEEKLY_CHEST, 1, new Random(System.nanoTime() + 13)).getFirst();
        jdbcTemplate.update(
            """
            INSERT INTO player_rift_weekly_reward
            (player_id, week_key, best_tier, claimed_run_id, essence_gained, shard_gained, orb_gained, chest_item_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            player.id(),
            weekKey,
            weekly.tier(),
            weekly.runId(),
            essence,
            shards,
            orbs,
            chest.id()
        );
        questService.recordEvent(player.id(), QuestEvent.of("riftWeeklyRewardClaimed"));
        return new WeeklyRewardResult(weekKey, weekly.tier(), essence, shards, orbs, chest, materials(player.id()));
    }

    public boolean unlocked(PlayerRecord player) {
        if (player.level() < UNLOCK_LEVEL) {
            return false;
        }
        Integer clears = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dungeon_run WHERE player_id = ? AND dungeon_id = ? AND success = TRUE",
            Integer.class,
            player.id(),
            UNLOCK_DUNGEON
        );
        return clears != null && clears > 0;
    }

    public int recommendedPower(int tier) {
        return (int) Math.round(70000 * Math.pow(1.08, Math.max(0, tier - 1)));
    }

    public int minimumPower(int tier) {
        return (int) Math.round(recommendedPower(tier) * 0.82);
    }

    public RiftProgress progress(long playerId) {
        return jdbcTemplate.query(
            """
            SELECT best_tier, best_score, best_rating
            FROM player_rift_progress
            WHERE player_id = ?
            """,
            (rs, rowNum) -> new RiftProgress(rs.getInt("best_tier"), rs.getInt("best_score"), rs.getString("best_rating")),
            playerId
        ).stream().findFirst().orElse(new RiftProgress(0, 0, null));
    }

    public RiftMaterials materials(long playerId) {
        return new RiftMaterials(
            inventoryService.materialQuantity(playerId, ESSENCE),
            inventoryService.materialQuantity(playerId, SHARD),
            inventoryService.materialQuantity(playerId, ORB)
        );
    }

    private BattleStats playerBattleStats(PlayerRecord player) {
        CombatStats stats = combatStatsService.playerStats(player, inventoryService.equippedItems(player.id()).values());
        return new BattleStats(
            stats.level(),
            stats.maxHp(),
            stats.attackPower(),
            stats.armor(),
            stats.resistance(),
            stats.speed(),
            stats.accuracy(),
            stats.evasion(),
            stats.critChance(),
            stats.critResist(),
            stats.damageType()
        );
    }

    private void grantProcessingDrops(long playerId, int tier, String rating, long seed) {
        Random random = new Random(seed + 41);
        inventoryService.grantItem(playerId, GEM_DUST, Math.max(1, tier / 2), random);
        if (tier >= 4 && random.nextDouble() < 0.42) {
            inventoryService.grantItem(playerId, SOCKET_CORE, 1, new Random(seed + 43));
        }
        if (tier >= 6 && random.nextDouble() < 0.26) {
            inventoryService.grantItem(playerId, GEM_DROPS.get(random.nextInt(GEM_DROPS.size())), 1, new Random(seed + 47));
        }
        if (tier >= 8 && random.nextDouble() < 0.22) {
            inventoryService.grantItem(playerId, ASCENSION_CORE, 1, new Random(seed + 53));
        }
        if (tier >= 10 && "S".equals(rating) && random.nextDouble() < 0.18) {
            inventoryService.grantItem(playerId, AFFIX_LOCK, 1, new Random(seed + 59));
        }
    }

    private List<BattleRoom> roomsForTier(int tier, int recommendedPower) {
        return List.of(
            new BattleRoom("深渊外环", List.of(enemy("裂隙掠食者", "skirmisher", false, tier, recommendedPower, 0.88), enemy("暗影祭司", "caster", false, tier, recommendedPower, 0.82))),
            new BattleRoom("回声长廊", List.of(enemy("深渊卫士", "guard", false, tier, recommendedPower, 1.05), enemy("虚空射手", "ranger", false, tier, recommendedPower, 0.92))),
            new BattleRoom("坍缩核心", List.of(enemy("裂隙统领", "elite", false, tier, recommendedPower, 1.35))),
            new BattleRoom("深渊王座", List.of(enemy("深渊领主 T" + tier, "boss", true, tier, recommendedPower, 2.75)))
        );
    }

    private UnitSpec enemy(String name, String archetype, boolean boss, int tier, int recommendedPower, double scale) {
        int level = UNLOCK_LEVEL + Math.max(1, tier);
        int hp = (int) Math.round((1250 + tier * 160 + recommendedPower / 90.0) * scale);
        int attack = (int) Math.round((220 + tier * 18 + recommendedPower / 950.0) * (boss ? 1.18 : 1.0));
        int armor = (int) Math.round(120 + tier * 9 + recommendedPower / 1350.0);
        int resistance = (int) Math.round(105 + tier * 8 + recommendedPower / 1500.0);
        int speed = boss ? 112 + tier / 2 : 102 + tier / 3;
        BattleStats stats = new BattleStats(level, hp, attack, armor, resistance, speed, 0.88, 0.04 + Math.min(0.08, tier * 0.001), 0.08 + Math.min(0.12, tier * 0.002), 0.03, boss ? "magic" : "physical");
        return new UnitSpec(name, archetype, boss, stats);
    }

    private List<Integer> challengeTiers(int bestTier) {
        int upper = Math.max(1, bestTier + 1);
        int lower = Math.max(1, upper - 8);
        List<Integer> tiers = new ArrayList<>();
        for (int tier = lower; tier <= upper; tier++) {
            tiers.add(tier);
        }
        return tiers;
    }

    private List<RiftTierPreview> tierPreviews(List<Integer> tiers) {
        return tiers.stream()
            .map(tier -> {
                List<RiftModifier> modifiers = modifiersForTier(tier);
                return new RiftTierPreview(
                    tier,
                    recommendedPower(tier),
                    minimumPower(tier),
                    modifiers,
                    rewardPreview(tier, modifiers, "A")
                );
            })
            .toList();
    }

    public List<RiftModifier> modifiersForTier(int tier) {
        List<RiftModifier> enabled = modifierConfigs().stream().filter(RiftModifier::enabled).toList();
        if (enabled.isEmpty()) {
            return List.of();
        }
        int count = Math.min(3, 1 + Math.max(0, tier / 5));
        List<RiftModifier> selected = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            selected.add(enabled.get(Math.floorMod(tier + index * 2, enabled.size())));
        }
        return selected;
    }

    private List<RiftModifier> modifierConfigs() {
        return jdbcTemplate.query(
            "SELECT * FROM rift_modifier_config ORDER BY sort_order, id",
            (rs, rowNum) -> new RiftModifier(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("difficulty_score"),
                rs.getDouble("reward_bonus"),
                rs.getBoolean("enabled")
            )
        );
    }

    private RiftReward rewardPreview(int tier, List<RiftModifier> modifiers, String rating) {
        int difficulty = modifiers.stream().mapToInt(RiftModifier::difficultyScore).sum();
        double multiplier = 1.0 + modifiers.stream().mapToDouble(RiftModifier::rewardBonus).sum() + ratingBonus(rating);
        int essence = Math.max(1, (int) Math.round((6 + tier * 1.8 + difficulty * 0.25) * multiplier));
        int shards = tier >= 5 ? Math.max(1, (int) Math.round((tier / 5.0) * multiplier)) : 0;
        int orbs = tier >= 10 && "S".equals(rating) ? Math.max(1, tier / 12) : 0;
        return new RiftReward(essence, shards, orbs, multiplier);
    }

    private double ratingBonus(String rating) {
        return switch (rating) {
            case "S" -> 0.20;
            case "A" -> 0.12;
            case "B" -> 0.05;
            default -> 0.0;
        };
    }

    private int modifierDifficulty(List<RiftModifier> modifiers) {
        return modifiers.stream().mapToInt(RiftModifier::difficultyScore).sum();
    }

    private long persistRun(PlayerRecord player, int tier, String requestId, BattleResult battle, int combatPower, int recommendedPower, List<RiftModifier> modifiers, RiftReward reward, long seed) {
        String controllerType = jdbcTemplate.queryForObject("SELECT controller_type FROM player WHERE id = ?", String.class, player.id());
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO rift_run
                (player_id, player_name, controller_type, request_id, tier, success, rating, score, turns_taken,
                 monsters_killed, combat_power, recommended_power, difficulty_score, reward_multiplier, modifier_ids,
                 essence_gained, shard_gained, orb_gained, player_final_hp, player_max_hp, seed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, player.id());
            ps.setString(2, player.name());
            ps.setString(3, controllerType == null ? "player" : controllerType);
            ps.setString(4, requestId);
            ps.setInt(5, tier);
            ps.setBoolean(6, battle.success());
            ps.setString(7, battle.rating());
            ps.setInt(8, battle.score());
            ps.setInt(9, battle.turnsTaken());
            ps.setInt(10, battle.monstersKilled());
            ps.setInt(11, combatPower);
            ps.setInt(12, recommendedPower);
            ps.setInt(13, modifierDifficulty(modifiers));
            ps.setDouble(14, reward.multiplier());
            ps.setString(15, String.join(",", modifiers.stream().map(RiftModifier::id).toList()));
            ps.setInt(16, reward.essence());
            ps.setInt(17, reward.shards());
            ps.setInt(18, reward.orbs());
            ps.setInt(19, battle.playerFinalHp());
            ps.setInt(20, battle.playerMaxHp());
            ps.setLong(21, seed);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long persistSimulation(long buildId, long playerId, int tier, BattleResult battle, int combatPower) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO build_simulation_run
                (build_id, player_id, tier, success, rating, score, turns_taken, combat_power, player_final_hp, player_max_hp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, buildId);
            ps.setLong(2, playerId);
            ps.setInt(3, tier);
            ps.setBoolean(4, battle.success());
            ps.setString(5, battle.rating());
            ps.setInt(6, battle.score());
            ps.setInt(7, battle.turnsTaken());
            ps.setInt(8, combatPower);
            ps.setInt(9, battle.playerFinalHp());
            ps.setInt(10, battle.playerMaxHp());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void persistEvents(long runId, List<BattleEvent> events) {
        for (BattleEvent event : events) {
            jdbcTemplate.update(
                """
                INSERT INTO rift_run_event
                (rift_run_id, event_index, turn, room_index, event_type, actor_name, target_name, text, value, actor_hp, target_hp, tone)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                event.index(),
                event.turn(),
                event.roomIndex(),
                event.eventType(),
                event.actorName(),
                event.targetName(),
                event.text(),
                event.value(),
                event.actorHp(),
                event.targetHp(),
                event.tone()
            );
        }
    }

    private void updateProgress(long playerId, int tier, int score, String rating, long runId) {
        jdbcTemplate.update(
            """
            INSERT INTO player_rift_progress (player_id, best_tier, best_score, best_rating, best_run_id)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              best_tier = GREATEST(best_tier, VALUES(best_tier)),
              best_score = CASE
                WHEN VALUES(best_tier) > best_tier THEN VALUES(best_score)
                WHEN VALUES(best_tier) = best_tier THEN GREATEST(best_score, VALUES(best_score))
                ELSE best_score
              END,
              best_rating = CASE
                WHEN VALUES(best_tier) > best_tier OR (VALUES(best_tier) = best_tier AND VALUES(best_score) >= best_score) THEN VALUES(best_rating)
                ELSE best_rating
              END,
              best_run_id = CASE
                WHEN VALUES(best_tier) > best_tier OR (VALUES(best_tier) = best_tier AND VALUES(best_score) >= best_score) THEN VALUES(best_run_id)
                ELSE best_run_id
              END
            """,
            playerId,
            tier,
            score,
            rating,
            runId
        );
    }

    private RiftRunResult existingRun(long playerId, String requestId) {
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM rift_run WHERE player_id = ? AND request_id = ?",
            Long.class,
            playerId,
            requestId
        );
        if (ids.isEmpty()) {
            return null;
        }
        return toRunResult(playerId, ids.getFirst(), staminaService.snapshot(playerId));
    }

    private RiftRunResult toRunResult(long playerId, long runId, StaminaSnapshot stamina) {
        StoredRun run = jdbcTemplate.query(
            "SELECT * FROM rift_run WHERE id = ?",
            (rs, rowNum) -> new StoredRun(
                rs.getLong("id"),
                rs.getInt("tier"),
                rs.getBoolean("success"),
                rs.getString("rating"),
                rs.getInt("score"),
                rs.getInt("turns_taken"),
                rs.getInt("monsters_killed"),
                rs.getInt("combat_power"),
                rs.getInt("recommended_power"),
                rs.getString("modifier_ids"),
                rs.getInt("essence_gained"),
                rs.getInt("shard_gained"),
                rs.getInt("orb_gained"),
                rs.getInt("player_final_hp"),
                rs.getInt("player_max_hp")
            ),
            runId
        ).getFirst();
        List<BattleEvent> events = jdbcTemplate.query(
            "SELECT * FROM rift_run_event WHERE rift_run_id = ? ORDER BY event_index",
            (rs, rowNum) -> new BattleEvent(
                rs.getInt("event_index"),
                rs.getInt("turn"),
                rs.getInt("room_index"),
                rs.getString("event_type"),
                rs.getString("actor_name"),
                rs.getString("target_name"),
                rs.getString("text"),
                rs.getInt("value"),
                rs.getInt("actor_hp"),
                rs.getInt("target_hp"),
                rs.getString("tone")
            ),
            runId
        );
        return new RiftRunResult(
            run.id(),
            run.tier(),
            run.success(),
            run.rating(),
            run.score(),
            run.turnsTaken(),
            run.monstersKilled(),
            run.combatPower(),
            run.recommendedPower(),
            List.of(run.modifierIds().isBlank() ? new String[0] : run.modifierIds().split(",")),
            new RiftReward(run.essence(), run.shards(), run.orbs(), 1.0),
            run.playerFinalHp(),
            run.playerMaxHp(),
            events,
            materials(playerId),
            stamina
        );
    }

    private List<RiftLeaderboardEntry> leaderboard(long viewerPlayerId, int limit) {
        Instant weekStart = weekStart();
        return jdbcTemplate.query(
            """
            SELECT
              r.player_id,
              COALESCE(p.name, r.player_name) AS display_name,
              COALESCE(p.controller_type, r.controller_type) AS display_controller_type,
              r.tier,
              r.rating,
              r.score,
              r.turns_taken,
              r.player_final_hp,
              r.player_max_hp,
              r.created_at
            FROM rift_run r
            LEFT JOIN player p ON p.id = r.player_id
            WHERE r.success = TRUE
              AND r.created_at >= ?
              AND NOT EXISTS (
                SELECT 1
                FROM rift_run better
                WHERE better.player_id = r.player_id
                  AND better.success = TRUE
                  AND better.created_at >= ?
                  AND (
                    better.tier > r.tier
                    OR (better.tier = r.tier AND better.score > r.score)
                    OR (better.tier = r.tier AND better.score = r.score AND better.player_final_hp > r.player_final_hp)
                    OR (better.tier = r.tier AND better.score = r.score AND better.player_final_hp = r.player_final_hp AND better.turns_taken < r.turns_taken)
                    OR (better.tier = r.tier AND better.score = r.score AND better.player_final_hp = r.player_final_hp AND better.turns_taken = r.turns_taken AND better.created_at < r.created_at)
                    OR (better.tier = r.tier AND better.score = r.score AND better.player_final_hp = r.player_final_hp AND better.turns_taken = r.turns_taken AND better.created_at = r.created_at AND better.id < r.id)
                  )
              )
            ORDER BY r.tier DESC, r.score DESC, r.player_final_hp DESC, r.turns_taken ASC, r.created_at ASC, r.id ASC
            LIMIT ?
            """,
            (rs, rowNum) -> new RiftLeaderboardEntry(
                rowNum + 1,
                rs.getLong("player_id"),
                rs.getString("display_name"),
                rs.getString("display_controller_type"),
                rs.getInt("tier"),
                rs.getString("rating"),
                rs.getInt("score"),
                rs.getInt("turns_taken"),
                rs.getInt("player_final_hp"),
                rs.getInt("player_max_hp"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getLong("player_id") == viewerPlayerId
            ),
            Timestamp.from(weekStart),
            Timestamp.from(weekStart),
            limit
        );
    }

    private WeeklyBest bestWeekly(long playerId) {
        Instant weekStart = weekStart();
        return jdbcTemplate.query(
            """
            SELECT id, tier, score
            FROM rift_run
            WHERE player_id = ? AND success = TRUE AND created_at >= ?
            ORDER BY tier DESC, score DESC, created_at ASC
            LIMIT 1
            """,
            (rs, rowNum) -> new WeeklyBest(rs.getLong("id"), rs.getInt("tier"), rs.getInt("score")),
            playerId,
            Timestamp.from(weekStart)
        ).stream().findFirst().orElse(new WeeklyBest(0, 0, 0));
    }

    private Instant weekStart() {
        return LocalDate.now(ZoneId.systemDefault())
            .with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant();
    }

    private boolean weeklyRewardAvailable(long playerId) {
        WeeklyBest weekly = bestWeekly(playerId);
        if (weekly.tier() <= 0) {
            return false;
        }
        Integer claimed = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player_rift_weekly_reward WHERE player_id = ? AND week_key = ?",
            Integer.class,
            playerId,
            weekKey()
        );
        return claimed == null || claimed == 0;
    }

    private String weekKey() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        WeekFields weekFields = WeekFields.ISO;
        return now.getYear() + "-W" + String.format("%02d", now.get(weekFields.weekOfWeekBasedYear()));
    }

    private String normalizeRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? null : requestId.trim();
    }

    public record RiftSnapshot(
        boolean unlocked,
        String unlockHint,
        int requiredLevel,
        String requiredDungeonId,
        int bestTier,
        int bestScore,
        String bestRating,
        int weeklyBestTier,
        List<Integer> challengeTiers,
        int nextTier,
        int staminaCost,
        int recommendedPower,
        int minimumPower,
        List<RiftModifier> modifiers,
        RiftReward rewardPreview,
        List<RiftTierPreview> tierPreviews,
        RiftMaterials materials,
        StaminaSnapshot stamina,
        List<RiftLeaderboardEntry> leaderboard,
        boolean weeklyRewardAvailable
    ) {
    }

    public record RiftRunResult(
        long runId,
        int tier,
        boolean success,
        String rating,
        int score,
        int turnsTaken,
        int monstersKilled,
        int combatPower,
        int recommendedPower,
        List<String> modifierIds,
        RiftReward rewards,
        int playerFinalHp,
        int playerMaxHp,
        List<BattleEvent> events,
        RiftMaterials materials,
        StaminaSnapshot stamina
    ) {
    }

    public record RiftSimulationResult(
        long simulationId,
        int tier,
        boolean success,
        String rating,
        int score,
        int turnsTaken,
        int monstersKilled,
        int combatPower,
        int recommendedPower,
        int playerFinalHp,
        int playerMaxHp,
        List<BattleEvent> events
    ) {
    }

    public record WeeklyRewardResult(String weekKey, int bestTier, int essence, int shards, int orbs, ItemRecord chest, RiftMaterials materials) {
    }

    public record RiftModifier(String id, String name, String description, int difficultyScore, double rewardBonus, boolean enabled) {
    }

    public record RiftReward(int essence, int shards, int orbs, double multiplier) {
    }

    public record RiftTierPreview(int tier, int recommendedPower, int minimumPower, List<RiftModifier> modifiers, RiftReward rewardPreview) {
    }

    public record RiftMaterials(int essence, int shards, int orbs) {
    }

    public record RiftProgress(int bestTier, int bestScore, String bestRating) {
    }

    public record RiftLeaderboardEntry(int rank, long playerId, String playerName, String controllerType, int tier, String rating, int score, int turnsTaken, int playerFinalHp, int playerMaxHp, String createdAt, boolean self) {
    }

    private record WeeklyBest(long runId, int tier, int score) {
    }

    private record StoredRun(long id, int tier, boolean success, String rating, int score, int turnsTaken, int monstersKilled, int combatPower, int recommendedPower, String modifierIds, int essence, int shards, int orbs, int playerFinalHp, int playerMaxHp) {
    }
}
