package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class RiftRefineRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public RiftRefineRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "rift_refine";
    }

    @Override
    public int priority() {
        return 76;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.riftUnlocked() && context.riftEssence() >= 12;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("深渊淬炼材料不足");
        }
        double value = 38;
        value += Math.min(18, context.riftEssence() / 8.0);
        value += Math.min(12, context.powerGapToProgression() / 240.0);
        if (context.isCurrentKind("rift_refine")) {
            value -= 12;
        }
        return new RobotActionScore(value, "消耗深渊精华淬炼核心装备，提升后续冲层效率");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.refineRiftEquipment(context, score);
    }
}
