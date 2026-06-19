package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.recharge.RechargeService;
import org.springframework.stereotype.Component;

@Component
public class GoldExchangeRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public GoldExchangeRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "gold_exchange";
    }

    @Override
    public int priority() {
        return 74;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.player().realMoney() > 0
            && context.tacticalGoldReserveDeficit() >= RechargeService.GOLD_PER_RMB;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("金币储备充足或余额不足");
        }
        long deficit = context.tacticalGoldReserveDeficit();
        double value = 30 + Math.min(30, deficit / 2_500.0);
        value += Math.min(14, context.actor().wealthTierLevel() * 0.9);
        if (context.enhancementOpportunity() != null && !context.enhancementOpportunity().affordableNow()) {
            value += 14;
        }
        if (context.marketOpportunities() > 0) {
            value += Math.min(12, context.marketOpportunities() * 2.0);
        }
        if (context.isCurrentKind("recharge")) {
            value -= 18;
        }
        String reason = "金币低于战术储备线，缺口约 " + deficit + " 金，先把余额换成可消费金币";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.exchangeGold(context, score);
    }
}
