package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

/**
 * Guild social ecosystem - P2: a bot contributes damage to its guild boss.
 * Damage uses the cheap combatPower formula (design 对策 3), so 200 bots can all
 * chip real, per-member contribution at near-zero compute. Auto-registered into the
 * unified RobotBrainService decision set, competing against dungeon/market/etc.
 */
@Component
public class GuildBossRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public GuildBossRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "guild_boss";
    }

    @Override
    public int priority() {
        return 22;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        // Only guild members can chip the boss; without this the action used to "win",
        // execute, then fall back to rest — a wasted tick.
        return context.inGuild();
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        // Sits in the same band as dungeon/rift so guildmates regularly move the shared
        // boss bar (the "alive world" payoff) without crowding out everything else.
        double value = 46 + context.random().nextDouble() * 10;
        value += 8 * context.archetype().pveBias();
        value -= context.repeatPenalty("guild_boss", 8.0);
        return new RobotActionScore(value, "为公会 Boss 贡献伤害，冲公会榜");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.guildBoss(context, score);
    }
}
