package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class ClaimQuestRewardRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public ClaimQuestRewardRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "quest_claim";
    }

    @Override
    public int priority() {
        return 95;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.claimableQuestCount() > 0 && context.firstClaimableQuestId() != null;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("没有可领取任务奖励");
        }
        double value = 82 + Math.min(18, context.claimableQuestCount() * 4.0);
        if (context.isCurrentKind("quest_claim")) {
            value -= 10;
        }
        return new RobotActionScore(value, "优先领取已完成任务，转化金币、经验和道具奖励");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.claimQuestReward(context, score);
    }
}
