package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class ReforgeEquipmentRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public ReforgeEquipmentRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "processing_reforge";
    }

    @Override
    public int priority() {
        return 73;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.riftUnlocked() && context.riftOrbs() >= 1 && context.riftEssence() >= 18;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("重铸材料不足");
        }
        double value = 28 + Math.min(16, context.riftOrbs() * 4.0) + Math.min(10, context.riftEssence() / 12.0);
        value += Math.min(10, context.powerGapToProgression() / 300.0);
        if (context.affixLockCount() > 0) {
            value += 6;
        }
        if (context.isCurrentKind("processing_reforge")) {
            value -= 12;
        }
        return new RobotActionScore(value, "使用重铸宝珠洗核心装备词条，高风险换高上限");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.reforgeEquipment(context, score);
    }
}
