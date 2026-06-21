package com.mythicrealm.api.gameplay.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RobotActionBudgetPlanTest {
    @Test
    void budgetScalesWithMultiplierAndCaps() {
        RobotSimulationProperties properties = new RobotSimulationProperties();

        RobotActionBudgetPlan plan = RobotActionBudgetPlan.from(properties, 10, 1.0);

        assertEquals(240, plan.limit(RobotIntentCategory.COMBAT));
        assertEquals(200, plan.limit(RobotIntentCategory.EQUIPMENT));
        assertEquals(220, plan.limit(RobotIntentCategory.MARKET));
        assertEquals(320, plan.limit(RobotIntentCategory.LIGHT));
        assertEquals(12, plan.limit(RobotIntentCategory.WORLD_CHAT));
        assertEquals(320, plan.activityLogLimit());
    }

    @Test
    void budgetFactorReducesExecutionSlots() {
        RobotSimulationProperties properties = new RobotSimulationProperties();

        RobotActionBudgetPlan plan = RobotActionBudgetPlan.from(properties, 1, 0.5);

        assertEquals(50, plan.limit(RobotIntentCategory.COMBAT));
        assertTrue(plan.tryConsume(RobotIntentCategory.WORLD_CHAT));
        assertTrue(plan.tryConsume(RobotIntentCategory.WORLD_CHAT));
        assertTrue(plan.tryConsume(RobotIntentCategory.WORLD_CHAT));
        assertFalse(plan.tryConsume(RobotIntentCategory.WORLD_CHAT));
    }
}
