package com.mythicrealm.api.gameplay.robot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RobotBrainServiceTest {
    @Test
    void robotOnlyRunsDungeonsThatMeetHardGate() {
        RobotBrainService brain = brainWith(
            List.of(dungeon("easy", 5, 1200), dungeon("locked", 8, 6000)),
            new DungeonProbeAction(),
            new GrowthAction()
        );

        RobotActionResult result = brain.thinkAndAct(agent(1, 6, 1500, 10_000), agent(2, 6, 1500, 10_000));

        assertThat(result.kind()).isEqualTo("dungeon");
        assertThat(result.text()).isEqualTo("easy");
    }

    @Test
    void robotPrefersProgressionWhenBlockedByDungeonGate() {
        RobotBrainService brain = brainWith(
            List.of(dungeon("locked", 8, 6000)),
            new DungeonProbeAction(),
            new GrowthAction()
        );

        RobotActionResult result = brain.thinkAndAct(agent(1, 5, 2500, 10_000), agent(2, 5, 2500, 10_000));

        assertThat(result.kind()).isEqualTo("growth");
        assertThat(result.text()).contains("3500");
    }

    @Test
    void dungeonActionRequiresAvailableStamina() {
        RobotActionSupport support = mock(RobotActionSupport.class);
        DungeonRunRobotAction action = new DungeonRunRobotAction(support);
        RobotDecisionContext context = new RobotDecisionContext(
            agent(1, 6, 1500, 10_000),
            agent(2, 6, 1500, 10_000),
            dungeon("easy", 5, 1200),
            null,
            null,
            10,
            0,
            0,
            new StaminaSnapshot(0, 200, 600, 120000, Instant.EPOCH),
            0,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            new java.util.Random(1)
        );

        assertThat(action.canRun(context)).isFalse();
    }

    private RobotBrainService brainWith(List<DungeonConfig> dungeons, RobotDecisionAction... actions) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), anyLong())).thenReturn(0);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), anyLong(), anyLong())).thenReturn(0);

        GameConfigService gameConfigService = mock(GameConfigService.class);
        when(gameConfigService.dungeons()).thenReturn(dungeons);

        RobotEquipmentService equipmentService = mock(RobotEquipmentService.class);
        when(equipmentService.enhancementOpportunity(any())).thenReturn(
            new RobotEquipmentService.EnhancementOpportunity(1, "Training Blade", "Weapon", 0, 1, 100, true, false, 50)
        );

        RobotActionSupport support = mock(RobotActionSupport.class);
        when(support.rest(any(), any(), any())).thenReturn(RobotActionResult.success("rest", "rest"));
        StaminaService staminaService = mock(StaminaService.class);
        when(staminaService.snapshot(anyLong())).thenReturn(new StaminaSnapshot(200, 200, 0, 0, Instant.EPOCH));
        QuestService questService = mock(QuestService.class);
        when(questService.claimableCount(anyLong())).thenReturn(0);
        when(questService.firstClaimableQuestId(anyLong())).thenReturn(null);

        return new RobotBrainService(List.of(actions), jdbcTemplate, gameConfigService, equipmentService, support, staminaService, questService);
    }

    private RobotAgent agent(long id, int level, int power, long gold) {
        return new RobotAgent(
            new PlayerRecord(id, id, "bot-" + id, "warrior", level, 0, gold, 0, 0, "common", 10, 8, 10, 4, 6, 0),
            "title",
            "dungeon progression",
            power,
            0,
            0,
            0,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }

    private DungeonConfig dungeon(String id, int minimumLevel, int minimumPower) {
        return new DungeonConfig(id, id, "desc", "normal", List.of(), minimumLevel, minimumPower, minimumLevel, minimumPower, "boss", 10);
    }

    private static class DungeonProbeAction implements RobotDecisionAction {
        @Override
        public String key() {
            return "dungeon";
        }

        @Override
        public int priority() {
            return 80;
        }

        @Override
        public boolean canRun(RobotDecisionContext context) {
            return context.runnableDungeon() != null;
        }

        @Override
        public RobotActionScore score(RobotDecisionContext context) {
            return new RobotActionScore(100, "run");
        }

        @Override
        public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
            return RobotActionResult.success("dungeon", context.runnableDungeon().id());
        }
    }

    private static class GrowthAction implements RobotDecisionAction {
        @Override
        public String key() {
            return "growth";
        }

        @Override
        public int priority() {
            return 70;
        }

        @Override
        public boolean canRun(RobotDecisionContext context) {
            return context.runnableDungeon() == null
                && context.progressionDungeon() != null
                && context.enhancementOpportunity() != null;
        }

        @Override
        public RobotActionScore score(RobotDecisionContext context) {
            return new RobotActionScore(90, "blocked");
        }

        @Override
        public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
            return RobotActionResult.success("growth", "missing power " + context.powerGapToProgression());
        }
    }
}
