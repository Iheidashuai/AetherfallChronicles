package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class EnhanceEquipmentRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public EnhanceEquipmentRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "enhance";
    }

    @Override
    public int priority() {
        return 70;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.enhancementOpportunity() != null && context.enhancementOpportunity().affordableWithRecharge();
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        var opportunity = context.enhancementOpportunity();
        if (opportunity == null) {
            return RobotActionScore.zero("没有可强化装备");
        }
        if (!opportunity.affordableWithRecharge()) {
            return RobotActionScore.zero("余额不足以支撑下一次强化");
        }

        double value = 34;
        // Quadratic: closing a big power gap is what really drives an enhancement push.
        value += 24 * RobotResponseCurves.quadratic(context.powerGapToProgression(), 0, 4500);
        value += Math.max(0, 10 - opportunity.currentLevel()) * 2.0;
        value += Math.min(14, context.actor().wealthTierLevel() * 0.8);
        value += opportunity.affordableNow() ? 8 : 4;
        value += Math.min(12, context.usableEnhancementStoneCount() * 4.0);
        value += 10 * context.archetype().growthBias();
        value -= context.repeatPenalty("enhance", 7.0);
        String reason = "强化" + opportunity.slotName() + "【" + opportunity.itemName() + "】到 +" + opportunity.nextLevel()
            + "，成本 " + opportunity.cost() + " 金，可补足下一阶段战力";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.enhanceEquipment(context, score);
    }
}
