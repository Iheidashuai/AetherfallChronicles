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
        // Raised baseline so the world channel isn't perpetually outscored by guild chat.
        double value = 22 + context.random().nextDouble() * 10;
        value += 8 * context.archetype().socialBias();
        value -= context.repeatPenalty("chat", 8.0);
        return new RobotActionScore(value, "需要保持世界频道活跃，并同步自己的短期目标");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.socialChat(context, score);
    }
}
