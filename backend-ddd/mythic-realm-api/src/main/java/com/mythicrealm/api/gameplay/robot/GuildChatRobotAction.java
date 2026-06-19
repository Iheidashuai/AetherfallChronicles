package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

/**
 * Guild social ecosystem - P1: a bot occasionally chats in its own guild channel.
 * Scored a little below world chat so it stays texture, not spam. Event-driven banter
 * (reacting to boss/donation/MVP events) is layered on in later phases.
 */
@Component
public class GuildChatRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public GuildChatRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "guild_chat";
    }

    @Override
    public int priority() {
        return 18;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        double value = 34 + context.random().nextDouble() * 14;
        if (context.isCurrentKind("guild_chat")) {
            value -= 12;
        }
        if (context.personalityContains("频道") || context.personalityContains("公会") || context.personalityContains("聊天")) {
            value += 8;
        }
        return new RobotActionScore(value, "维持公会频道的活跃氛围");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.guildChat(context, score);
    }
}
