package com.mythicrealm.api.gameplay.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class CombatEngine {
    public static final int DEFAULT_ROUND_LIMIT = 30;

    private final DamageCalculator damageCalculator;

    public CombatEngine(DamageCalculator damageCalculator) {
        this.damageCalculator = damageCalculator;
    }

    public EncounterOutcome fight(
        Combatant player,
        Combatant enemy,
        int playerHp,
        int roundLimit,
        Random random
    ) {
        int enemyHp = enemy.stats().maxHp();
        int playerMaxHp = player.stats().maxHp();
        int enemyMaxHp = enemy.stats().maxHp();
        boolean shieldUsed = false;
        int shieldRounds = 0;
        boolean enrageAnnounced = false;
        List<CombatEvent> events = new ArrayList<>();

        for (int round = 1; round <= roundLimit && playerHp > 0 && enemyHp > 0; round++) {
            DamageResult playerHit = damageCalculator.basicAttack(player.stats(), adjustedEnemyStats(enemy.stats(), shieldRounds), random);
            enemyHp = Math.max(0, enemyHp - playerHit.damage());
            events.add(CombatEvent.attack(round, player, enemy, playerHit, playerHp, playerMaxHp, enemyHp, enemyMaxHp, playerText(round, enemy.name(), playerHit, enemyHp)));
            if (enemyHp <= 0) {
                events.add(CombatEvent.system(round, "death", enemy.name() + " 被击败。", playerHp, playerMaxHp, 0, enemyMaxHp, enemy.name(), 0));
                break;
            }

            if ("shield_phase".equals(enemy.mechanic()) && enemyHp <= enemyMaxHp * 0.35 && !shieldUsed) {
                shieldUsed = true;
                shieldRounds = 3;
                events.add(CombatEvent.system(round, "phase", enemy.name() + " 进入护盾阶段，护甲和抗性提升 25%，持续 3 回合。", playerHp, playerMaxHp, enemyHp, enemyMaxHp, enemy.name(), 0));
            }
            if ("enrage_50".equals(enemy.mechanic()) && enemyHp <= enemyMaxHp * 0.50 && !enrageAnnounced) {
                enrageAnnounced = true;
                events.add(CombatEvent.system(round, "phase", enemy.name() + " 生命低于 50%，进入狂暴，伤害提升 20%。", playerHp, playerMaxHp, enemyHp, enemyMaxHp, enemy.name(), 0));
            }

            double mechanicMultiplier = enemyMechanicMultiplier(enemy, enemyHp, enemyMaxHp, round);
            if ("heavy_every_3".equals(enemy.mechanic()) && round % 3 == 0) {
                events.add(CombatEvent.system(round, "phase", enemy.name() + " 蓄力重击，本次攻击伤害倍率 1.45。", playerHp, playerMaxHp, enemyHp, enemyMaxHp, enemy.name(), 0));
            }
            DamageResult enemyHit = damageCalculator.attack(enemy.stats(), player.stats(), DamageCalculator.BASIC_ATTACK_MULTIPLIER, mechanicMultiplier, random);
            playerHp = Math.max(0, playerHp - enemyHit.damage());
            events.add(CombatEvent.attack(round, enemy, player, enemyHit, playerHp, playerMaxHp, enemyHp, enemyMaxHp, enemyText(round, enemy.name(), enemyHit, playerHp, playerMaxHp)));
            if (playerHp <= 0) {
                events.add(CombatEvent.system(round, "death", "你在 " + enemy.name() + " 面前倒下，副本推进中止。", playerHp, playerMaxHp, enemyHp, enemyMaxHp, enemy.name(), 0));
                break;
            }
            if (shieldRounds > 0) {
                shieldRounds--;
            }
        }

        boolean roundLimitReached = playerHp > 0 && enemyHp > 0;
        if (roundLimitReached) {
            events.add(CombatEvent.system(roundLimit, "phase", "回合上限已到，输出不足以击破 " + enemy.name() + "。", playerHp, playerMaxHp, enemyHp, enemyMaxHp, enemy.name(), 0));
        }
        return new EncounterOutcome(playerHp, enemyHp, enemyMaxHp, enemyHp <= 0, playerHp <= 0, roundLimitReached, events);
    }

    private CombatStats adjustedEnemyStats(CombatStats base, int shieldRounds) {
        if (shieldRounds <= 0) {
            return base;
        }
        return new CombatStats(
            base.level(),
            base.maxHp(),
            base.maxMp(),
            base.attackPower(),
            (int) Math.round(base.armor() * 1.25),
            (int) Math.round(base.resistance() * 1.25),
            base.speed(),
            base.accuracy(),
            base.evasion(),
            base.critChance(),
            base.critDamage(),
            base.critResist(),
            base.damageType()
        );
    }

    private double enemyMechanicMultiplier(Combatant enemy, int enemyHp, int enemyMaxHp, int round) {
        double multiplier = 1.0;
        if ("heavy_every_3".equals(enemy.mechanic()) && round % 3 == 0) {
            multiplier *= 1.45;
        }
        if ("enrage_50".equals(enemy.mechanic()) && enemyHp <= enemyMaxHp * 0.50) {
            multiplier *= 1.20;
        }
        return multiplier;
    }

    private String playerText(int round, String enemyName, DamageResult hit, int enemyHp) {
        if (!hit.hit()) {
            return "第 " + round + " 回合：你的攻击被 " + enemyName + " 闪避。";
        }
        return "第 " + round + " 回合：你" + (hit.critical() ? "打出暴击 " : "造成 ") + hit.damage() + " 伤害，" + enemyName + " 剩余 " + enemyHp + "。";
    }

    private String enemyText(int round, String enemyName, DamageResult hit, int playerHp, int playerMaxHp) {
        if (!hit.hit()) {
            return "第 " + round + " 回合：" + enemyName + " 的攻击落空。";
        }
        return "第 " + round + " 回合：" + enemyName + (hit.critical() ? "暴击 " : "反击 ") + hit.damage() + " 伤害，你剩余 " + playerHp + "/" + playerMaxHp + "。";
    }

    public record EncounterOutcome(
        int playerHp,
        int enemyHp,
        int enemyMaxHp,
        boolean enemyDefeated,
        boolean playerDefeated,
        boolean roundLimitReached,
        List<CombatEvent> events
    ) {
    }

    public record CombatEvent(
        String actor,
        String eventType,
        String text,
        String tone,
        String enemyName,
        int playerHp,
        int playerMaxHp,
        int enemyHp,
        int enemyMaxHp,
        int damage,
        boolean critical,
        boolean missed
    ) {
        static CombatEvent attack(
            int round,
            Combatant actor,
            Combatant target,
            DamageResult result,
            int playerHp,
            int playerMaxHp,
            int enemyHp,
            int enemyMaxHp,
            String text
        ) {
            return new CombatEvent(
                actor.side(),
                result.eventType(),
                text,
                result.critical() ? "critical" : result.hit() ? actor.player() ? "hit" : "enemy" : "miss",
                actor.player() ? target.name() : actor.name(),
                playerHp,
                playerMaxHp,
                enemyHp,
                enemyMaxHp,
                result.damage(),
                result.critical(),
                !result.hit()
            );
        }

        static CombatEvent system(
            int round,
            String eventType,
            String text,
            int playerHp,
            int playerMaxHp,
            int enemyHp,
            int enemyMaxHp,
            String enemyName,
            int damage
        ) {
            return new CombatEvent(
                "system",
                eventType,
                text,
                switch (eventType) {
                    case "death" -> enemyHp <= 0 ? "victory" : "danger";
                    case "heal" -> "heal";
                    default -> "danger";
                },
                enemyName,
                playerHp,
                playerMaxHp,
                enemyHp,
                enemyMaxHp,
                damage,
                false,
                false
            );
        }
    }
}
