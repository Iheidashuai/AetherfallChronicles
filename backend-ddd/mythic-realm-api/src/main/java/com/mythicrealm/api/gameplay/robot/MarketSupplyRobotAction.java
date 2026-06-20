package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class MarketSupplyRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public MarketSupplyRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "market_supply";
    }

    @Override
    public int priority() {
        return 45;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.runnableDungeon() != null
            && context.activeListings() < 4
            && context.inventoryCount() < 970
            && context.stamina() != null
            && context.stamina().current() > 0;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("当前不需要补商会货源");
        }
        double value = 22 + Math.max(0, 4 - context.activeListings()) * 5.0;
        if (context.marketOpportunities() < 3) {
            value += 10;
        }
        if (context.stamina() != null
            && context.stamina().current() >= Math.max(1, context.stamina().max()) * 0.9) {
            value += 8;
        }
        value += 8 * context.archetype().marketBias();
        value -= context.repeatPenalty("market_list", 6.0);
        String reason = "个人寄售少于 4 单，刷本补货能给市场增加装备和成长材料流动";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.supplyMarket(context, score);
    }
}
