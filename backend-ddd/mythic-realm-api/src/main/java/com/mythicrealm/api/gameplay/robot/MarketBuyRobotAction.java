package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class MarketBuyRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public MarketBuyRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "market_buy";
    }

    @Override
    public int priority() {
        return 65;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.marketOpportunities() > 0 && context.goldAfterPossibleRecharge() > 0;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("商会暂无可承担的成长商品");
        }
        double value = 28;
        value += Math.min(18, context.marketOpportunities() * 3.0);
        // Quadratic: small power gaps barely tempt buying, a big gap makes shopping urgent.
        value += 22 * RobotResponseCurves.quadratic(context.powerGapToProgression(), 0, 5000);
        value += Math.min(18, context.actor().wealthTierLevel() * 1.1);
        value += 12 * context.archetype().marketBias();
        value -= context.repeatPenalty("market_buy", 8.0);
        String reason = "商会有 " + context.marketOpportunities() + " 单可承担的装备或成长物资，采购可能比硬刷更快补齐缺口";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.buyFromMarket(context, score);
    }
}
