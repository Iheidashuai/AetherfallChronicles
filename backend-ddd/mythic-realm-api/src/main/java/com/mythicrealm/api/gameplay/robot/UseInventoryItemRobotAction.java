package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class UseInventoryItemRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public UseInventoryItemRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "item_use";
    }

    @Override
    public int priority() {
        return 86;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        boolean lowStaminaPotion = context.stamina() != null
            && context.stamina().current() < Math.max(1, context.stamina().max()) * 0.8
            && context.staminaPotionCount() > 0;
        return lowStaminaPotion
            || context.attributePotionCount() > 0
            || context.chestCount() > 0
            || context.legendaryFragmentCount() >= 20
            || context.immortalFragmentCount() >= 30;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("背包里没有适合当前使用的道具");
        }
        double value = 42;
        if (context.stamina() != null && context.staminaPotionCount() > 0) {
            int missing = Math.max(0, context.stamina().max() - context.stamina().current());
            if (context.stamina().current() == 0) {
                value += 46;
            } else if (context.stamina().current() <= Math.max(1, context.stamina().max()) * 0.15) {
                value += 32;
            } else {
                value += Math.min(18, missing / 10.0);
            }
        }
        if (context.immortalFragmentCount() >= 30 || context.legendaryFragmentCount() >= 20) {
            value += 28;
        }
        if (context.chestCount() > 0) {
            value += Math.min(18, context.chestCount() * 4.0);
        }
        if (context.attributePotionCount() > 0) {
            value += 12;
        }
        if (context.isCurrentKind("item_stamina") || context.isCurrentKind("item_chest") || context.isCurrentKind("item_craft")) {
            value -= 8;
        }
        return new RobotActionScore(value, "处理背包道具，把疲劳、宝箱、碎片和属性药转为成长收益");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.useInventoryItemOrCraft(context, score);
    }
}
