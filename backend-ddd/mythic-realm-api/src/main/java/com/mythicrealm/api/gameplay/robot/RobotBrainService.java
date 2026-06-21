package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.dungeon.SweepTicketPolicy;
import com.mythicrealm.api.gameplay.endgame.EndgameRiftService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RobotBrainService {
    private final List<RobotDecisionAction> actions;
    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final RobotEquipmentService robotEquipmentService;
    private final RobotActionSupport actionSupport;
    private final StaminaService staminaService;
    private final QuestService questService;
    private final EndgameRiftService endgameRiftService;
    private final RobotMemoryService memoryService;

    @Autowired
    public RobotBrainService(
        List<RobotDecisionAction> actions,
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        RobotEquipmentService robotEquipmentService,
        RobotActionSupport actionSupport,
        StaminaService staminaService,
        QuestService questService,
        RobotMemoryService memoryService
    ) {
        this(actions, jdbcTemplate, gameConfigService, robotEquipmentService, actionSupport, staminaService, questService, null, memoryService);
    }

    public RobotBrainService(
        List<RobotDecisionAction> actions,
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        RobotEquipmentService robotEquipmentService,
        RobotActionSupport actionSupport,
        StaminaService staminaService,
        QuestService questService,
        EndgameRiftService endgameRiftService,
        RobotMemoryService memoryService
    ) {
        this.actions = actions.stream()
            .sorted(Comparator.comparingInt(RobotDecisionAction::priority).reversed())
            .toList();
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.robotEquipmentService = robotEquipmentService;
        this.actionSupport = actionSupport;
        this.staminaService = staminaService;
        this.questService = questService;
        this.endgameRiftService = endgameRiftService;
        this.memoryService = memoryService == null ? new RobotMemoryService() : memoryService;
    }

    public RobotActionResult thinkAndAct(RobotAgent actor, RobotAgent target) {
        return execute(plan(actor, target));
    }

    public RobotIntent plan(RobotAgent actor, RobotAgent target) {
        Random random = new Random(Objects.hash(actor.id(), actor.power(), actor.dungeonClears(), System.nanoTime()));
        RobotDecisionContext context = createContext(actor, target, random);
        List<ScoredAction> candidates = actions.stream()
            .filter(action -> action.canRun(context))
            .map(action -> new ScoredAction(action, action.score(context)))
            .filter(candidate -> candidate.score().value() > 0)
            .toList();

        if (candidates.isEmpty()) {
            return RobotIntent.rest(actor, target, "没有可执行候选动作");
        }
        ScoredAction selected = sampleSoftmax(candidates, temperature(actor), random);
        return RobotIntent.of(actor, target, selected.action(), selected.score(), context);
    }

    public RobotActionResult execute(RobotIntent intent) {
        RobotActionResult result;
        if (intent.action() == null) {
            result = actionSupport.rest(intent.actor(), "在公会大厅整理背包和下一步路线。", intent.reason());
        } else {
            result = intent.action().execute(intent.context(), intent.score());
        }
        memoryService.recordKind(intent.actor().id(), result.kind());
        return result;
    }

    /**
     * Weighted-random (softmax) action selection instead of argmax. The highest
     * utility action is still most likely, but lower-utility actions keep a non-zero
     * chance — robots "satisfice" like people rather than always grabbing the single
     * optimal action (which made them predictable and starved low-value actions).
     * Temperature is the human-vs-expert dial: higher = more varied.
     */
    private ScoredAction sampleSoftmax(List<ScoredAction> candidates, double temperature, Random random) {
        double t = Math.max(1.0, temperature);
        double max = candidates.stream().mapToDouble(c -> c.score().value()).max().orElse(0.0);
        double[] weights = new double[candidates.size()];
        double total = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            double w = Math.exp((candidates.get(i).score().value() - max) / t);
            weights[i] = w;
            total += w;
        }
        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (roll <= cumulative) {
                return candidates.get(i);
            }
        }
        return candidates.getLast();
    }

    /**
     * Selection temperature for this robot: base comes from its personality
     * archetype, then we nudge it up at night so off-peak behaviour feels looser /
     * more idle, the way a real population does.
     */
    private double temperature(RobotAgent actor) {
        RobotArchetype archetype = actor.archetype() == null ? RobotArchetype.CASUAL : actor.archetype();
        double base = archetype.temperature();
        int hour = LocalTime.now().getHour();
        double timeFactor;
        if (hour >= 1 && hour < 7) {
            timeFactor = 1.25; // deep night: sparse, meandering
        } else if (hour >= 19 && hour < 24) {
            timeFactor = 0.95; // prime time: a touch more purposeful
        } else {
            timeFactor = 1.0;
        }
        return Math.min(16.0, base * timeFactor);
    }

    private RobotDecisionContext createContext(RobotAgent actor, RobotAgent target, Random random) {
        DungeonConfig runnableDungeon = chooseRunnableDungeon(actor, random);
        DungeonConfig progressionDungeon = chooseProgressionDungeon(actor);
        RobotEquipmentService.EnhancementOpportunity enhancementOpportunity = robotEquipmentService.enhancementOpportunity(actor.player());
        long spendableGold = actor.player().gold() + actor.player().realMoney() * 1_000L;
        long playerId = actor.id();
        StaminaService.StaminaSnapshot stamina = staminaService.snapshot(playerId);
        int normalSweepTickets = templateQuantity(playerId, SweepTicketPolicy.NORMAL_TICKET_TEMPLATE_ID);
        int specialSweepTickets = templateQuantity(playerId, SweepTicketPolicy.SPECIAL_TICKET_TEMPLATE_ID);
        SweepPlan sweepPlan = chooseSweepPlan(actor, stamina, normalSweepTickets, specialSweepTickets, random);
        boolean riftUnlocked = endgameRiftService != null && endgameRiftService.unlocked(actor.player());
        EndgameRiftService.RiftProgress riftProgress = endgameRiftService == null
            ? new EndgameRiftService.RiftProgress(0, 0, null)
            : endgameRiftService.progress(playerId);
        int riftNextTier = riftUnlocked ? Math.max(1, riftProgress.bestTier() + 1) : 0;
        EndgameRiftService.RiftMaterials riftMaterials = endgameRiftService == null
            ? new EndgameRiftService.RiftMaterials(0, 0, 0)
            : endgameRiftService.materials(playerId);
        return new RobotDecisionContext(
            actor,
            target,
            runnableDungeon,
            progressionDungeon,
            sweepPlan == null ? null : sweepPlan.dungeon(),
            sweepPlan == null ? 0 : sweepPlan.times(),
            enhancementOpportunity,
            inventoryCount(playerId),
            activeListings(playerId),
            marketOpportunities(playerId, spendableGold),
            stamina,
            questService.claimableCount(playerId),
            questService.firstClaimableQuestId(playerId),
            effectQuantity(playerId, "staminaPotion"),
            effectQuantity(playerId, "attributePotion"),
            categoryQuantity(playerId, "chest"),
            templateQuantity(playerId, "mat_fragment_legendary"),
            templateQuantity(playerId, "mat_fragment_immortal"),
            usableEnhancementStoneCount(playerId, enhancementOpportunity),
            riftUnlocked,
            riftProgress.bestTier(),
            riftNextTier,
            riftNextTier <= 0 || endgameRiftService == null ? 0 : endgameRiftService.minimumPower(riftNextTier),
            riftMaterials.essence(),
            riftMaterials.shards(),
            riftMaterials.orbs(),
            templateQuantity(playerId, "mat_socket_core"),
            templateQuantity(playerId, "mat_gem_dust"),
            templateQuantity(playerId, "mat_affix_lock"),
            templateQuantity(playerId, "mat_ascension_core"),
            templateQuantity(playerId, "mat_ascension_guard"),
            effectQuantity(playerId, "gem"),
            buildCount(playerId),
            activeBuildName(playerId),
            activeBuildPresetId(playerId),
            recommendedBuildPresetId(actor, riftNextTier),
            memoryService.recentKindCounts(playerId),
            inGuild(playerId),
            random
        );
    }

    private SweepPlan chooseSweepPlan(
        RobotAgent actor,
        StaminaService.StaminaSnapshot stamina,
        int normalSweepTickets,
        int specialSweepTickets,
        Random random
    ) {
        if (stamina == null || stamina.current() < SweepTicketPolicy.SHORT_SWEEP_TIMES) {
            return null;
        }
        List<String> clearedDungeonIds = clearedDungeonIds(actor.id());
        if (clearedDungeonIds.isEmpty()) {
            return null;
        }
        List<DungeonConfig> normalCandidates = sweepCandidates(actor, clearedDungeonIds, false);
        if (!normalCandidates.isEmpty() && normalSweepTickets >= SweepTicketPolicy.SHORT_SWEEP_TIMES) {
            int times = normalSweepTickets >= SweepTicketPolicy.LONG_SWEEP_TIMES && stamina.current() >= SweepTicketPolicy.LONG_SWEEP_TIMES
                ? SweepTicketPolicy.LONG_SWEEP_TIMES
                : SweepTicketPolicy.SHORT_SWEEP_TIMES;
            return new SweepPlan(normalCandidates.get(random.nextInt(normalCandidates.size())), times);
        }
        if (specialSweepTickets < SweepTicketPolicy.SHORT_SWEEP_TIMES || random.nextInt(100) >= 28) {
            return null;
        }
        List<DungeonConfig> specialCandidates = sweepCandidates(actor, clearedDungeonIds, true);
        if (specialCandidates.isEmpty()) {
            return null;
        }
        int times = specialSweepTickets >= SweepTicketPolicy.LONG_SWEEP_TIMES && stamina.current() >= SweepTicketPolicy.LONG_SWEEP_TIMES
            ? SweepTicketPolicy.LONG_SWEEP_TIMES
            : SweepTicketPolicy.SHORT_SWEEP_TIMES;
        return new SweepPlan(specialCandidates.get(random.nextInt(specialCandidates.size())), times);
    }

    private List<DungeonConfig> sweepCandidates(RobotAgent actor, List<String> clearedDungeonIds, boolean special) {
        return gameConfigService.dungeons().stream()
            .filter(dungeon -> DungeonService.isSpecialDungeon(dungeon.id()) == special)
            .filter(dungeon -> clearedDungeonIds.contains(dungeon.id()))
            .filter(dungeon -> dungeon.minimumLevel() <= Math.max(1, actor.player().level()))
            .filter(dungeon -> dungeon.minimumPower() <= Math.max(1, actor.power()))
            .sorted(Comparator
                .comparingInt((DungeonConfig dungeon) -> Math.abs(dungeon.minimumLevel() - Math.max(1, actor.player().level())))
                .thenComparingInt(dungeon -> Math.abs(dungeon.minimumPower() - actor.power()))
                .thenComparing(DungeonConfig::id))
            .limit(8)
            .toList();
    }

    private List<String> clearedDungeonIds(long playerId) {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT dungeon_id FROM dungeon_run WHERE player_id = ? AND success = TRUE",
            String.class,
            playerId
        );
    }

    private boolean inGuild(long playerId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM guild_member WHERE player_id = ?",
            Integer.class,
            playerId
        );
        return count != null && count > 0;
    }

    private DungeonConfig chooseRunnableDungeon(RobotAgent actor, Random random) {
        List<DungeonConfig> candidates = gameConfigService.dungeons().stream()
            .filter(dungeon -> dungeon.minimumLevel() <= Math.max(1, actor.player().level()))
            .filter(dungeon -> dungeon.minimumPower() <= Math.max(1, actor.power()))
            .sorted(Comparator
                .comparing((DungeonConfig dungeon) -> !DungeonService.isSpecialDungeon(dungeon.id()))
                .thenComparingInt((DungeonConfig dungeon) -> Math.abs(dungeon.minimumLevel() - Math.max(1, actor.player().level())))
                .thenComparingInt(dungeon -> Math.abs(dungeon.minimumPower() - actor.power()))
                .thenComparing(DungeonConfig::id))
            .limit(8)
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private DungeonConfig chooseProgressionDungeon(RobotAgent actor) {
        return gameConfigService.dungeons().stream()
            .filter(dungeon -> dungeon.minimumLevel() <= Math.max(1, actor.player().level() + 8))
            .filter(dungeon -> dungeon.minimumPower() > actor.power() || dungeon.minimumLevel() > actor.player().level())
            .sorted(Comparator
                .comparing((DungeonConfig dungeon) -> !DungeonService.isSpecialDungeon(dungeon.id()))
                .thenComparingInt(DungeonConfig::minimumLevel)
                .thenComparingInt(DungeonConfig::minimumPower)
                .thenComparing(DungeonConfig::id))
            .findFirst()
            .orElse(null);
    }

    private int inventoryCount(long robotId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory_slot WHERE player_id = ?",
            Integer.class,
            robotId
        );
        return count == null ? 0 : count;
    }

    private int activeListings(long robotId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM market_listing WHERE seller_player_id = ? AND status = 'listed'",
            Integer.class,
            robotId
        );
        return count == null ? 0 : count;
    }

    private int marketOpportunities(long robotId, long spendableGold) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM market_listing ml
            LEFT JOIN item_instance ii ON ii.id = ml.item_id
            JOIN item_template it ON it.id = COALESCE(ii.template_id, ml.item_template_id)
            WHERE ml.status = 'listed'
              AND (ml.seller_player_id IS NULL OR ml.seller_player_id <> ?)
              AND (
                  COALESCE(ii.quality, it.quality) IN ('rare', 'epic', 'legendary', 'immortal')
                  OR COALESCE(ml.item_category, it.market_category, it.item_category) IN ('material', 'gem', 'consumable', 'chest', 'sweepTicket')
              )
              AND ml.price <= ?
            """,
            Integer.class,
            robotId,
            Math.max(0, spendableGold)
        );
        return count == null ? 0 : count;
    }

    private int effectQuantity(long playerId, String effectType) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(ii.quantity), 0)
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ?
              AND (it.effect_type = ? OR it.effect_value_json LIKE ?)
            """,
            Integer.class,
            playerId,
            effectType,
            "%\"" + effectType + "\"%"
        );
        return count == null ? 0 : count;
    }

    private int categoryQuantity(long playerId, String itemCategory) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(ii.quantity), 0)
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ? AND it.item_category = ?
            """,
            Integer.class,
            playerId,
            itemCategory
        );
        return count == null ? 0 : count;
    }

    private int templateQuantity(long playerId, String templateId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(ii.quantity), 0)
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            WHERE s.player_id = ? AND ii.template_id = ?
            """,
            Integer.class,
            playerId,
            templateId
        );
        return count == null ? 0 : count;
    }

    private int usableEnhancementStoneCount(
        long playerId,
        RobotEquipmentService.EnhancementOpportunity enhancementOpportunity
    ) {
        if (enhancementOpportunity == null) {
            return effectQuantity(playerId, "enhancementStone");
        }
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(ii.quantity), 0)
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ?
              AND it.effect_type = 'enhancementStone'
              AND it.min_enhance_level <= ?
              AND it.max_enhance_level >= ?
            """,
            Integer.class,
            playerId,
            enhancementOpportunity.nextLevel(),
            enhancementOpportunity.nextLevel()
        );
        return count == null ? 0 : count;
    }

    private int buildCount(long playerId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player_build WHERE player_id = ?",
            Integer.class,
            playerId
        );
        return count == null ? 0 : count;
    }

    private String activeBuildName(long playerId) {
        return jdbcTemplate.queryForList(
            "SELECT name FROM player_build WHERE player_id = ? AND active = TRUE ORDER BY updated_at DESC, id DESC LIMIT 1",
            String.class,
            playerId
        ).stream().findFirst().orElse(null);
    }

    private String activeBuildPresetId(long playerId) {
        return jdbcTemplate.queryForList(
            "SELECT source_preset_id FROM player_build WHERE player_id = ? AND active = TRUE ORDER BY updated_at DESC, id DESC LIMIT 1",
            String.class,
            playerId
        ).stream().findFirst().orElse(null);
    }

    private String recommendedBuildPresetId(RobotAgent actor, int riftNextTier) {
        String preferred = switch (actor.player().profession()) {
            case "warrior" -> riftNextTier >= 8 ? "preset_warrior_duelist"
                : actor.power() < 35_000 ? "preset_warrior_guardian" : "preset_warrior_breaker";
            case "ranger" -> riftNextTier >= 8 ? "preset_ranger_execute"
                : actor.power() < 35_000 ? "preset_ranger_wind" : "preset_ranger_crit";
            case "mage" -> riftNextTier >= 8 ? "preset_mage_renewal"
                : actor.power() < 35_000 ? "preset_mage_ward" : "preset_mage_arcane";
            default -> null;
        };
        if (preferred == null) {
            return null;
        }
        return jdbcTemplate.queryForList(
            "SELECT id FROM build_preset WHERE id = ? AND profession = ? AND enabled = TRUE",
            String.class,
            preferred,
            actor.player().profession()
        ).stream().findFirst().orElse(null);
    }

    private record ScoredAction(RobotDecisionAction action, RobotActionScore score) {
    }

    private record SweepPlan(DungeonConfig dungeon, int times) {
    }
}
