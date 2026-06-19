package com.mythicrealm.api.gameplay.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class CombatEngine {
    public static final int DEFAULT_ROUND_LIMIT = 30;

    private static final CombatSkill BASIC_ATTACK = new CombatSkill(
        "basic_attack",
        "普通攻击",
        1,
        1,
        1,
        "basic",
        "enemy",
        "",
        DamageCalculator.BASIC_ATTACK_MULTIPLIER,
        0,
        0,
        "damage",
        0,
        0,
        "default",
        0,
        "basic-slash"
    );

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
        return fight(player, enemy, playerHp, List.of(), List.of(), roundLimit, random);
    }

    public EncounterOutcome fight(
        Combatant player,
        Combatant enemy,
        int playerHp,
        List<CombatSkill> playerSkills,
        List<CombatSkill> enemySkills,
        int roundLimit,
        Random random
    ) {
        CombatState playerState = new CombatState(player, playerHp, Math.max(0, player.stats().maxMp()));
        CombatState enemyState = new CombatState(enemy, enemy.stats().maxHp(), Math.max(enemy.stats().maxMp(), 60 + enemy.stats().level() * 5));
        boolean shieldUsed = false;
        int shieldRounds = 0;
        boolean enrageAnnounced = false;
        List<CombatEvent> events = new ArrayList<>();

        for (int round = 1; round <= roundLimit && playerState.alive() && enemyState.alive(); round++) {
            playerState.tickCooldowns();
            enemyState.tickCooldowns();

            CombatEvent playerEvent = performAction(
                round,
                playerState,
                enemyState,
                playerSkills,
                adjustedEnemyStats(enemy.stats(), shieldRounds),
                1.0,
                random
            );
            events.add(playerEvent);
            if (!enemyState.alive()) {
                events.add(CombatEvent.system(round, "death", enemy.name() + " 被击败。", playerState, enemyState, enemy.name(), 0));
                break;
            }

            if ("shield_phase".equals(enemy.mechanic()) && enemyState.hp <= enemyState.maxHp() * 0.35 && !shieldUsed) {
                shieldUsed = true;
                shieldRounds = 3;
                events.add(CombatEvent.system(round, "phase", enemy.name() + " 进入护盾阶段，护甲和抗性提升 25%，持续 3 回合。", playerState, enemyState, enemy.name(), 0));
            }
            if ("enrage_50".equals(enemy.mechanic()) && enemyState.hp <= enemyState.maxHp() * 0.50 && !enrageAnnounced) {
                enrageAnnounced = true;
                events.add(CombatEvent.system(round, "phase", enemy.name() + " 生命低于 50%，进入狂暴，伤害提升 20%。", playerState, enemyState, enemy.name(), 0));
            }

            double mechanicMultiplier = enemyMechanicMultiplier(enemy, enemyState.hp, enemyState.maxHp(), round);
            if ("heavy_every_3".equals(enemy.mechanic()) && round % 3 == 0) {
                events.add(CombatEvent.system(round, "phase", enemy.name() + " 蓄力重击，本次攻击伤害倍率 1.45。", playerState, enemyState, enemy.name(), 0));
            }
            CombatEvent enemyEvent = performAction(round, enemyState, playerState, enemySkills, player.stats(), mechanicMultiplier, random);
            events.add(enemyEvent);
            if (!playerState.alive()) {
                events.add(CombatEvent.system(round, "death", "你在 " + enemy.name() + " 面前倒下，副本推进中止。", playerState, enemyState, enemy.name(), 0));
                break;
            }
            if (shieldRounds > 0) {
                shieldRounds--;
            }
            playerState.tickEffects();
            enemyState.tickEffects();
        }

        boolean roundLimitReached = playerState.alive() && enemyState.alive();
        if (roundLimitReached) {
            events.add(CombatEvent.system(roundLimit, "phase", "回合上限已到，输出不足以击破 " + enemy.name() + "。", playerState, enemyState, enemy.name(), 0));
        }
        return new EncounterOutcome(playerState.hp, enemyState.hp, enemyState.maxHp(), enemyState.hp <= 0, playerState.hp <= 0, roundLimitReached, events);
    }

    private CombatEvent performAction(
        int round,
        CombatState actor,
        CombatState target,
        List<CombatSkill> skills,
        CombatStats targetStats,
        double mechanicMultiplier,
        Random random
    ) {
        CombatSkill skill = chooseSkill(actor, target, skills, round);
        actor.spendMp(skill.mpCost());
        if (skill.cooldown() > 0) {
            actor.cooldowns.put(skill.id(), skill.cooldown());
        }

        if (skill.healing()) {
            int heal = Math.max(1, (int) Math.round(actor.maxHp() * skill.effectPower()));
            actor.hp = Math.min(actor.maxHp(), actor.hp + heal);
            return CombatEvent.skill(round, actor, target, skill, "heal", heal, false, false, healingText(round, actor.combatant, skill, heal), actor.combatant.side(), heal);
        }
        if (skill.shielding()) {
            actor.shieldReduction = Math.max(actor.shieldReduction, skill.effectPower());
            actor.shieldRounds = Math.max(actor.shieldRounds, Math.max(1, skill.durationRounds()));
            int shieldValue = Math.max(1, (int) Math.round(actor.maxHp() * skill.effectPower()));
            return CombatEvent.skill(round, actor, target, skill, "shield", 0, false, false, shieldText(round, actor.combatant, skill, shieldValue), actor.combatant.side(), shieldValue);
        }

        String damageType = skill.damageType() == null || skill.damageType().isBlank()
            ? actor.combatant.stats().damageType()
            : skill.damageType();
        DamageResult hit = damageCalculator.attack(actor.combatant.stats(), targetStats, skill.multiplier(), mechanicMultiplier, damageType, random);
        DamageResult finalHit = applyShield(hit, target);
        target.hp = Math.max(0, target.hp - finalHit.damage());
        return CombatEvent.attack(round, actor, target, skill, finalHit, attackText(round, actor.combatant, target.combatant, skill, finalHit, target.hp, target.maxHp()));
    }

    private CombatSkill chooseSkill(CombatState actor, CombatState target, List<CombatSkill> skills, int round) {
        List<CombatSkill> usable = skills == null ? List.of() : skills.stream()
            .filter(skill -> actor.cooldowns.getOrDefault(skill.id(), 0) <= 0)
            .filter(skill -> actor.mp >= Math.max(0, skill.mpCost()))
            .sorted(Comparator.comparingInt(CombatSkill::priority).reversed())
            .toList();
        double actorHpRatio = actor.hp / (double) Math.max(1, actor.maxHp());
        double targetHpRatio = target.hp / (double) Math.max(1, target.maxHp());
        for (CombatSkill skill : usable) {
            if (skill.healing() && actorHpRatio <= 0.45) {
                return skill;
            }
            if (skill.shielding() && actorHpRatio <= 0.62) {
                return skill;
            }
            if ("execute".equals(skill.triggerKind()) && targetHpRatio <= 0.35) {
                return skill;
            }
            if ("opener".equals(skill.triggerKind()) && round <= 2) {
                return skill;
            }
            if ("burst".equals(skill.triggerKind()) && targetHpRatio <= 0.72) {
                return skill;
            }
        }
        return usable.stream()
            .filter(CombatSkill::damaging)
            .findFirst()
            .orElse(BASIC_ATTACK);
    }

    private DamageResult applyShield(DamageResult hit, CombatState target) {
        if (!hit.hit() || hit.damage() <= 0 || target.shieldReduction <= 0) {
            return hit;
        }
        int reduced = Math.max(1, (int) Math.round(hit.damage() * (1 - target.shieldReduction)));
        return new DamageResult(hit.hit(), hit.critical(), reduced, hit.hitChance(), hit.reduction(), hit.multiplier());
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

    private String attackText(int round, Combatant actor, Combatant target, CombatSkill skill, DamageResult hit, int targetHp, int targetMaxHp) {
        String actorName = actor.player() ? "你" : actor.name();
        String targetName = target.player() ? "你" : target.name();
        if (!hit.hit()) {
            return "第 " + round + " 回合：" + actorName + "施放【" + skill.name() + "】，被 " + targetName + " 闪避。";
        }
        String verb = hit.critical() ? "暴击造成 " : "造成 ";
        String remain = target.player() ? "，你剩余 " + targetHp + "/" + targetMaxHp + "。" : "，" + targetName + " 剩余 " + targetHp + "。";
        return "第 " + round + " 回合：" + actorName + "施放【" + skill.name() + "】" + verb + hit.damage() + " 伤害" + remain;
    }

    private String healingText(int round, Combatant actor, CombatSkill skill, int heal) {
        String actorName = actor.player() ? "你" : actor.name();
        return "第 " + round + " 回合：" + actorName + "施放【" + skill.name() + "】，恢复 " + heal + " 生命。";
    }

    private String shieldText(int round, Combatant actor, CombatSkill skill, int shieldValue) {
        String actorName = actor.player() ? "你" : actor.name();
        return "第 " + round + " 回合：" + actorName + "施放【" + skill.name() + "】，获得约 " + shieldValue + " 护盾强度。";
    }

    private static final class CombatState {
        private final Combatant combatant;
        private int hp;
        private int mp;
        private int shieldRounds;
        private double shieldReduction;
        private final Map<String, Integer> cooldowns = new HashMap<>();

        private CombatState(Combatant combatant, int hp, int mp) {
            this.combatant = combatant;
            this.hp = Math.max(1, hp);
            this.mp = Math.max(0, mp);
        }

        private boolean alive() {
            return hp > 0;
        }

        private int maxHp() {
            return combatant.stats().maxHp();
        }

        private void spendMp(int value) {
            mp = Math.max(0, mp - Math.max(0, value));
        }

        private void tickCooldowns() {
            cooldowns.replaceAll((id, value) -> Math.max(0, value - 1));
        }

        private void tickEffects() {
            if (shieldRounds > 0) {
                shieldRounds--;
                if (shieldRounds == 0) {
                    shieldReduction = 0;
                }
            }
        }
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
        boolean missed,
        String skillId,
        String skillName,
        String visualKey,
        String targetSide,
        int effectValue
    ) {
        static CombatEvent attack(
            int round,
            CombatState actor,
            CombatState target,
            CombatSkill skill,
            DamageResult result,
            String text
        ) {
            return skill(
                round,
                actor,
                target,
                skill,
                result.eventType(),
                result.damage(),
                result.critical(),
                !result.hit(),
                text,
                target.combatant.side(),
                0
            );
        }

        static CombatEvent skill(
            int round,
            CombatState actor,
            CombatState target,
            CombatSkill skill,
            String eventType,
            int damage,
            boolean critical,
            boolean missed,
            String text,
            String targetSide,
            int effectValue
        ) {
            return new CombatEvent(
                actor.combatant.side(),
                eventType,
                text,
                tone(actor.combatant, eventType, critical, missed),
                actor.combatant.player() ? target.combatant.name() : actor.combatant.name(),
                actor.combatant.player() ? actor.hp : target.hp,
                actor.combatant.player() ? actor.maxHp() : target.maxHp(),
                actor.combatant.player() ? target.hp : actor.hp,
                actor.combatant.player() ? target.maxHp() : actor.maxHp(),
                damage,
                critical,
                missed,
                skill.id(),
                skill.name(),
                skill.visualKey(),
                targetSide,
                effectValue
            );
        }

        static CombatEvent system(
            int round,
            String eventType,
            String text,
            CombatState player,
            CombatState enemy,
            String enemyName,
            int damage
        ) {
            return new CombatEvent(
                "system",
                eventType,
                text,
                switch (eventType) {
                    case "death" -> enemy.hp <= 0 ? "victory" : "danger";
                    case "heal" -> "heal";
                    default -> "danger";
                },
                enemyName,
                player.hp,
                player.maxHp(),
                enemy.hp,
                enemy.maxHp(),
                damage,
                false,
                false,
                null,
                null,
                eventType,
                "system",
                0
            );
        }

        private static String tone(Combatant actor, String eventType, boolean critical, boolean missed) {
            if (missed) {
                return "miss";
            }
            if (critical) {
                return "critical";
            }
            return switch (eventType) {
                case "heal" -> "heal";
                case "shield" -> "shield";
                default -> actor.player() ? "hit" : "enemy";
            };
        }
    }
}
