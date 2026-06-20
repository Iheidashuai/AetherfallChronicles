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
    public boolean canRun(RobotDecisionContext context) {
        return context.inGuild();
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        // Kept on par with world chat (not above it) so both channels stay lively.
        double value = 20 + context.random().nextDouble() * 8;
        value += 8 * context.archetype().socialBias();
        value -= context.repeatPenalty("guild_chat", 8.0);
        return new RobotActionScore(value, "维持公会频道的活跃氛围");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.guildChat(context, score);
    }
}
