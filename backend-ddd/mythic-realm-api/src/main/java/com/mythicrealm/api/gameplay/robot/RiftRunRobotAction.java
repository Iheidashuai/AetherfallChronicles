package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class RiftRunRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public RiftRunRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "rift_run";
    }

    @Override
    public int priority() {
        return 82;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.riftUnlocked()
            && context.stamina() != null
            && context.stamina().current() >= 3
            && context.riftNextTier() > 0
            && context.actor().power() >= context.riftMinimumPower();
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("深渊未解锁、疲劳不足或战力未达标");
        }
        double value = 48;
        value += Math.min(22, context.riftNextTier() * 1.6);
        value += Math.max(0, 12 - context.riftEssence() / 8.0);
        value += 8 * context.archetype().pveBias();
        value -= context.repeatPenalty("rift", 6.0);
        return new RobotActionScore(value, "挑战深渊 T" + context.riftNextTier() + "，冲榜并获取淬炼材料");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.runRift(context, score);
    }
}
