package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

/**
 * Guild social ecosystem - P3: a bot donates spare gold to its guild, feeding the
 * guild fund + contribution (which raises guild level and everyone's perks).
 * Auto-registered into the unified RobotBrainService decision set.
 */
@Component
public class GuildDonateRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public GuildDonateRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "guild_donate";
    }

    @Override
    public int priority() {
        return 21;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.inGuild();
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        // Wealthier bots are more inclined to donate; kept below boss so contribution
        // mostly comes from fighting, with donations as a steady secondary stream.
        double value = 26 + context.random().nextDouble() * 14;
        value += Math.min(16, context.actor().wealthTierLevel() * 1.2);
        value += 6 * context.archetype().socialBias();
        value -= context.repeatPenalty("guild_donate", 8.0);
        return new RobotActionScore(value, "向公会捐献金币，推动公会等级与全员增益");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.guildDonate(context, score);
    }
}
