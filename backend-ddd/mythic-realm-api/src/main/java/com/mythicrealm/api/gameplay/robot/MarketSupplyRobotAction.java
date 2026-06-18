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
        if (context.stamina() != null && context.stamina().current() >= 180) {
            value += 8;
        }
        if (context.personalityContains("商会") || context.personalityContains("掉落")) {
            value += 8;
        }
        if (context.isCurrentKind("market_list")) {
            value -= 10;
        }
        String reason = "个人寄售少于 4 件，刷本补货能给市场增加装备流动";
        return new RobotActionScore(value, reason);
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.supplyMarket(context, score);
    }
}
