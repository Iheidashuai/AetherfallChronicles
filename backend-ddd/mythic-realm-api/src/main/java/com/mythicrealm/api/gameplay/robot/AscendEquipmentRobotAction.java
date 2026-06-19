package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class AscendEquipmentRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public AscendEquipmentRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "processing_ascend";
    }

    @Override
    public int priority() {
        return 72;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.riftUnlocked()
            && context.ascensionCoreCount() > 0
            && context.riftEssence() >= 24
            && context.riftShards() >= 1;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("升阶材料不足");
        }
        double value = 30 + Math.min(18, context.ascensionCoreCount() * 5.0);
        value += Math.min(12, context.powerGapToProgression() / 260.0);
        if (context.ascensionGuardCount() > 0) {
            value += 5;
        }
        if (context.isCurrentKind("processing_ascend")) {
            value -= 12;
        }
        return new RobotActionScore(value, "升阶核心装备，打开宝石孔和词条上限");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.ascendEquipment(context, score);
    }
}
