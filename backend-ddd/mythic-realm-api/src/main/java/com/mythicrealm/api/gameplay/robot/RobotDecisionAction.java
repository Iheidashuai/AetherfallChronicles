package com.mythicrealm.api.gameplay.robot;

/**
 * Extension point for robot decisions.
 *
 * When a new gameplay feature is added, check whether robots should perceive it
 * through RobotDecisionContext and whether it needs a new RobotDecisionAction.
 */
public interface RobotDecisionAction {
    String key();

    int priority();

    default boolean canRun(RobotDecisionContext context) {
        return true;
    }

    RobotActionScore score(RobotDecisionContext context);

    RobotActionResult execute(RobotDecisionContext context, RobotActionScore score);
}
