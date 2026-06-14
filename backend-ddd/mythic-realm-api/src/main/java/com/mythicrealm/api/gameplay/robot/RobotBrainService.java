package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
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

    public RobotBrainService(
        List<RobotDecisionAction> actions,
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        RobotEquipmentService robotEquipmentService,
        RobotActionSupport actionSupport,
        StaminaService staminaService,
        QuestService questService
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
    }

    public RobotActionResult thinkAndAct(RobotAgent actor, RobotAgent target) {
        Random random = new Random(Objects.hash(actor.id(), actor.power(), actor.dungeonClears(), System.nanoTime()));
        RobotDecisionContext context = createContext(actor, target, random);
        List<ScoredAction> candidates = actions.stream()
            .filter(action -> action.canRun(context))
            .map(action -> {
                RobotActionScore score = action.score(context);
                return new ScoredAction(action, score, score.value() + random.nextDouble() * 6.0);
            })
            .filter(candidate -> candidate.score().value() > 0)
            .sorted(Comparator
                .comparingDouble(ScoredAction::finalScore)
                .thenComparing(candidate -> candidate.action().priority())
                .reversed())
            .toList();

        if (candidates.isEmpty()) {
            return actionSupport.rest(actor, "在公会大厅整理背包和下一步路线。", "没有可执行候选动作");
        }
        ScoredAction selected = candidates.getFirst();
        return selected.action().execute(context, selected.score());
    }

    private RobotDecisionContext createContext(RobotAgent actor, RobotAgent target, Random random) {
        DungeonConfig runnableDungeon = chooseRunnableDungeon(actor, random);
        DungeonConfig progressionDungeon = chooseProgressionDungeon(actor);
        RobotEquipmentService.EnhancementOpportunity enhancementOpportunity = robotEquipmentService.enhancementOpportunity(actor.player());
        long spendableGold = actor.player().gold() + actor.player().realMoney() * 1_000L;
        long playerId = actor.id();
        return new RobotDecisionContext(
            actor,
            target,
            runnableDungeon,
            progressionDungeon,
            enhancementOpportunity,
            inventoryCount(playerId),
            activeListings(playerId),
            marketOpportunities(playerId, spendableGold),
            staminaService.snapshot(playerId),
            questService.claimableCount(playerId),
            questService.firstClaimableQuestId(playerId),
            effectQuantity(playerId, "staminaPotion"),
            effectQuantity(playerId, "attributePotion"),
            categoryQuantity(playerId, "chest"),
            templateQuantity(playerId, "mat_fragment_legendary"),
            templateQuantity(playerId, "mat_fragment_immortal"),
            usableEnhancementStoneCount(playerId, enhancementOpportunity),
            random
        );
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
              AND COALESCE(ii.quality, it.quality) IN ('rare', 'epic', 'legendary', 'immortal')
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
            WHERE s.player_id = ? AND it.effect_type = ?
            """,
            Integer.class,
            playerId,
            effectType
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

    private record ScoredAction(RobotDecisionAction action, RobotActionScore score, double finalScore) {
    }
}
