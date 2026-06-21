package com.mythicrealm.api.gameplay.robot;

public record RobotIntent(
    long robotId,
    String actionKey,
    RobotIntentCategory category,
    RobotAgent actor,
    RobotAgent target,
    RobotDecisionAction action,
    RobotActionScore score,
    RobotDecisionContext context,
    String reason
) {
    public static RobotIntent of(
        RobotAgent actor,
        RobotAgent target,
        RobotDecisionAction action,
        RobotActionScore score,
        RobotDecisionContext context
    ) {
        String key = action.key();
        return new RobotIntent(
            actor.id(),
            key,
            RobotIntentCategory.fromActionKey(key),
            actor,
            target,
            action,
            score,
            context,
            score.reason()
        );
    }

    public static RobotIntent rest(RobotAgent actor, RobotAgent target, String reason) {
        return new RobotIntent(
            actor.id(),
            "rest",
            RobotIntentCategory.LIGHT,
            actor,
            target,
            null,
            RobotActionScore.zero(reason),
            null,
            reason
        );
    }

    public static RobotIntent lightweight(RobotAgent actor, RobotAgent target, String actionKey, RobotIntentCategory category, String reason) {
        return new RobotIntent(
            actor.id(),
            actionKey,
            category == null ? RobotIntentCategory.LIGHT : category,
            actor,
            target,
            null,
            RobotActionScore.zero(reason),
            null,
            reason
        );
    }
}
