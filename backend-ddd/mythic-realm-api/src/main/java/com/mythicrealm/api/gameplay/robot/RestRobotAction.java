package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class RestRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public RestRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "rest";
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        return new RobotActionScore(4, "没有更高收益动作，短暂整理状态");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.rest(context.actor(), "在公会大厅整理背包和下一步路线。", score.reason());
    }
}
