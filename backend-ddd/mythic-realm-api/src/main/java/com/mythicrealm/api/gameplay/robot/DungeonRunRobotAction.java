package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class DungeonRunRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public DungeonRunRobotAction(RobotActionSupport support) {
        this.support = support;
    }

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
        return context.runnableDungeon() != null
            && context.inventoryCount() < 960
            && context.stamina() != null
            && context.stamina().current() > 0;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("没有可稳定推进的副本或背包接近满载");
        }
        double value = 45;
        value += Math.min(18, context.player().level() / 4.0);
        value += Math.min(18, context.runnableDungeon().minimumPower() / Math.max(1.0, context.actor().power()) * 10);
        if (context.runnableDungeon().minimumPower() <= context.actor().power()) {
            value += 10;
        }
        if (context.stamina() != null) {
            double ratio = context.stamina().current() / (double) Math.max(1, context.stamina().max());
            // Logistic urgency to spend stamina while it's plentiful; sharp drop-off when nearly empty.
            value += 18 * RobotResponseCurves.logistic(ratio, 0.5, 8.0);
            if (ratio <= 0.1) {
                value -= 18;
            }
        }
        if (context.progressionDungeon() != null && context.runnableDungeon().id().equals(context.progressionDungeon().id())) {
            value += 12;
        }
        value += 8 * context.archetype().pveBias();
        value -= context.repeatPenalty("dungeon", 7.0);
        String reason = "当前可刷【" + context.runnableDungeon().name() + "】，经验和掉落收益稳定";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.runDungeon(context, score);
    }
}
