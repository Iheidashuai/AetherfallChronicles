package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
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

    public RobotBrainService(
        List<RobotDecisionAction> actions,
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        RobotEquipmentService robotEquipmentService,
        RobotActionSupport actionSupport
    ) {
        this.actions = actions.stream()
            .sorted(Comparator.comparingInt(RobotDecisionAction::priority).reversed())
            .toList();
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.robotEquipmentService = robotEquipmentService;
        this.actionSupport = actionSupport;
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
        return new RobotDecisionContext(
            actor,
            target,
            runnableDungeon,
            progressionDungeon,
            enhancementOpportunity,
            inventoryCount(actor.id()),
            activeListings(actor.id()),
            marketOpportunities(actor.id(), spendableGold),
            random
        );
    }

    private DungeonConfig chooseRunnableDungeon(RobotAgent actor, Random random) {
        List<DungeonConfig> candidates = gameConfigService.dungeons().stream()
            .filter(dungeon -> dungeon.recommendedLevel() <= Math.max(1, actor.player().level() + 3))
            .filter(dungeon -> dungeon.recommendedPower() <= Math.max(1, (int) (actor.power() * 1.35)))
            .sorted(Comparator
                .comparing((DungeonConfig dungeon) -> !DungeonService.isSpecialDungeon(dungeon.id()))
                .thenComparingInt((DungeonConfig dungeon) -> Math.abs(dungeon.recommendedLevel() - Math.max(1, actor.player().level())))
                .thenComparingInt(dungeon -> Math.abs(dungeon.recommendedPower() - actor.power()))
                .thenComparing(DungeonConfig::id))
            .limit(8)
            .toList();
        if (candidates.isEmpty()) {
            candidates = gameConfigService.dungeons().stream()
                .filter(dungeon -> !DungeonService.isSpecialDungeon(dungeon.id()))
                .sorted(Comparator.comparingInt(DungeonConfig::recommendedLevel).thenComparing(DungeonConfig::id))
                .limit(3)
                .toList();
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private DungeonConfig chooseProgressionDungeon(RobotAgent actor) {
        return gameConfigService.dungeons().stream()
            .filter(dungeon -> dungeon.recommendedLevel() <= Math.max(1, actor.player().level() + 8))
            .filter(dungeon -> dungeon.recommendedPower() > actor.power() || dungeon.recommendedLevel() > actor.player().level())
            .sorted(Comparator
                .comparing((DungeonConfig dungeon) -> !DungeonService.isSpecialDungeon(dungeon.id()))
                .thenComparingInt(DungeonConfig::recommendedLevel)
                .thenComparingInt(DungeonConfig::recommendedPower)
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

    private record ScoredAction(RobotDecisionAction action, RobotActionScore score, double finalScore) {
    }
}
