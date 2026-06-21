package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class DungeonSweepRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public DungeonSweepRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "dungeon_sweep";
    }

    @Override
    public int priority() {
        return 86;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.sweepDungeon() != null
            && context.sweepTimes() > 0
            && context.inventoryCount() < 950;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("没有可扫荡副本或背包接近满载");
        }
        double value = context.sweepTimes() >= 50 ? 88 : 72;
        value += Math.min(14, context.player().level() / 5.0);
        value += 10 * context.archetype().pveBias();
        value -= context.repeatPenalty("dungeon_sweep", 5.0);
        String reason = "扫荡【" + context.sweepDungeon().name() + "】" + context.sweepTimes() + " 次，加速消耗疲劳和扫荡符";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.sweepDungeon(context, score);
    }
}
