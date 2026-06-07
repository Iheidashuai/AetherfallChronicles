package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class SocialChatRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public SocialChatRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "chat";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        double value = 15 + context.random().nextDouble() * 14;
        if (context.isCurrentKind("chat")) {
            value -= 10;
        }
        if (context.personalityContains("频道") || context.personalityContains("比较") || context.personalityContains("提醒")) {
            value += 8;
        }
        return new RobotActionScore(value, "需要保持世界频道活跃，并同步自己的短期目标");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.socialChat(context, score);
    }
}
