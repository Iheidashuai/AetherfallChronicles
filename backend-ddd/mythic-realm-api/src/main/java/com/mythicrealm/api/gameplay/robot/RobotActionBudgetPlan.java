package com.mythicrealm.api.gameplay.robot;

import java.util.EnumMap;
import java.util.Map;

public class RobotActionBudgetPlan {
    private final Map<RobotIntentCategory, Integer> remaining;
    private final Map<RobotIntentCategory, Integer> limits;
    private final int activityLogLimit;
    private final double budgetFactor;

    private RobotActionBudgetPlan(
        Map<RobotIntentCategory, Integer> limits,
        int activityLogLimit,
        double budgetFactor
    ) {
        this.limits = Map.copyOf(limits);
        this.remaining = new EnumMap<>(limits);
        this.activityLogLimit = activityLogLimit;
        this.budgetFactor = budgetFactor;
    }

    public static RobotActionBudgetPlan from(RobotSimulationProperties properties, int multiplier, double budgetFactor) {
        RobotSimulationProperties.ActionBudgets budgets = properties.getActionBudgets();
        double factor = Math.max(0.03, Math.min(1.0, budgetFactor));
        Map<RobotIntentCategory, Integer> limits = new EnumMap<>(RobotIntentCategory.class);
        limits.put(RobotIntentCategory.COMBAT, limit(budgets.getCombatBase(), budgets.getCombatCap(), multiplier, factor));
        limits.put(RobotIntentCategory.EQUIPMENT, limit(budgets.getEquipmentBase(), budgets.getEquipmentCap(), multiplier, factor));
        limits.put(RobotIntentCategory.MARKET, limit(budgets.getMarketBase(), budgets.getMarketCap(), multiplier, factor));
        limits.put(RobotIntentCategory.LIGHT, limit(budgets.getLightBase(), budgets.getLightCap(), multiplier, factor));
        limits.put(RobotIntentCategory.WORLD_CHAT, limit(budgets.getWorldChatBase(), budgets.getWorldChatCap(), multiplier, factor));
        int activityLogLimit = limit(budgets.getActivityLogBase(), budgets.getActivityLogCap(), multiplier, factor);
        return new RobotActionBudgetPlan(limits, activityLogLimit, factor);
    }

    public boolean tryConsume(RobotIntentCategory category) {
        RobotIntentCategory safeCategory = category == null ? RobotIntentCategory.LIGHT : category;
        int left = remaining.getOrDefault(safeCategory, 0);
        if (left <= 0) {
            return false;
        }
        remaining.put(safeCategory, left - 1);
        return true;
    }

    public int limit(RobotIntentCategory category) {
        return limits.getOrDefault(category, 0);
    }

    public int activityLogLimit() {
        return activityLogLimit;
    }

    public double budgetFactor() {
        return budgetFactor;
    }

    public Map<String, Integer> wireLimits() {
        Map<String, Integer> values = new java.util.LinkedHashMap<>();
        for (RobotIntentCategory category : RobotIntentCategory.values()) {
            values.put(category.wireName(), limit(category));
        }
        values.put("activity-log", activityLogLimit);
        return values;
    }

    private static int limit(int base, int cap, int multiplier, double budgetFactor) {
        int safeMultiplier = Math.max(1, multiplier);
        int raw = Math.min(Math.max(0, base) * safeMultiplier, Math.max(0, cap));
        return Math.max(0, (int) Math.floor(raw * budgetFactor));
    }
}
