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
    public RobotActionScore score(RobotDecisionContext context) {
        // Tentpole: scored in the top tier (near quest-claim ~82) so bots regularly chip
        // the shared boss — that visible bar movement from guildmates is the "alive world"
        // payoff. Tunable; a later balance pass can dial this down with the rubber-band.
        double value = 60 + context.random().nextDouble() * 22;
        if (context.isCurrentKind("guild_boss")) {
            value -= 15;
        }
        if (context.personalityContains("副本") || context.personalityContains("公会") || context.personalityContains("推进")) {
            value += 8;
        }
        return new RobotActionScore(value, "为公会 Boss 贡献伤害，冲公会榜");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.guildBoss(context, score);
    }
}
