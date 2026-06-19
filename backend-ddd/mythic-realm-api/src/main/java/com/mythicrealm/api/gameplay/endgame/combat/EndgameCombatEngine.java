package com.mythicrealm.api.gameplay.endgame.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class EndgameCombatEngine {
    private static final int MAX_ACTIONS = 180;

    public BattleResult run(BattleRequest request) {
        Random random = new Random(request.seed());
        var events = new ArrayList<BattleEvent>();
        UnitState player = UnitState.player(request.playerName(), request.profession(), applyPlayerModifiers(request.playerStats(), request.modifierIds()));
        int monstersKilled = 0;
        int actionCount = 0;

        BuildCombatPlan plan = request.combatPlan() == null ? BuildCombatPlan.empty() : request.combatPlan();
        String planText = plan.buildId() > 0 ? "，构筑：" + plan.name() + " / " + plan.strategy() : "";
        addEvent(events, 0, 0, "start", player.name, null, "进入深渊裂隙 T" + request.tier() + "，词缀：" + modifierLabel(request.modifierIds()) + planText, 0, player.hp, 0, "system");

        for (int roomIndex = 0; roomIndex < request.rooms().size(); roomIndex++) {
            BattleRoom room = request.rooms().get(roomIndex);
            addEvent(events, actionCount, roomIndex + 1, "room", null, null, room.label() + " 开始。", 0, player.hp, 0, "system");
            for (UnitSpec enemySpec : room.enemies()) {
                UnitState enemy = UnitState.enemy(enemySpec.name(), enemySpec.archetype(), applyEnemyModifiers(enemySpec.stats(), enemySpec.boss(), request.modifierIds()), enemySpec.boss());
                if (enemy.boss && request.modifierIds().contains("voidlord")) {
                    enemy.shield = Math.max(enemy.shield, (int) Math.round(enemy.maxHp * 0.16));
                    addEvent(events, actionCount, roomIndex + 1, "phase", enemy.name, player.name, enemy.name + " 获得虚空护盾。", enemy.shield, enemy.hp, player.hp, "danger");
                }

                while (player.alive() && enemy.alive() && actionCount < MAX_ACTIONS) {
                    UnitState actor = player.nextActionAt <= enemy.nextActionAt ? player : enemy;
                    UnitState target = actor == player ? enemy : player;
                    actionCount++;
                    actor.actionSerial++;
                    resolveStatus(actor, events, actionCount, roomIndex + 1);
                    if (!actor.alive()) {
                        break;
                    }
                    Skill skill = chooseSkill(actor, target, request.profession(), request.modifierIds(), plan);
                    if (actor.player && skill.healing()) {
                        int heal = Math.max(1, (int) Math.round(actor.maxHp * skill.effectPower()));
                        actor.hp = Math.min(actor.maxHp, actor.hp + heal);
                        addEvent(events, actionCount, roomIndex + 1, skill.id(), actor.name, actor.name, actor.name + " 使用 " + skill.name() + "，恢复 " + heal + " 生命。", heal, actor.hp, target.hp, "heal");
                        actor.nextActionAt += Math.max(30, 10000 / Math.max(20, actor.speed));
                        continue;
                    }
                    if (actor.player && skill.shielding()) {
                        int shield = Math.max(1, (int) Math.round(actor.maxHp * skill.effectPower()));
                        actor.shield = Math.max(actor.shield, shield);
                        addEvent(events, actionCount, roomIndex + 1, skill.id(), actor.name, actor.name, actor.name + " 使用 " + skill.name() + "，获得 " + shield + " 护盾。", shield, actor.hp, target.hp, "player");
                        actor.nextActionAt += Math.max(30, 10000 / Math.max(20, actor.speed));
                        continue;
                    }
                    int damage = damage(actor, target, skill, random, request.modifierIds());
                    target.takeDamage(damage);
                    if (skill.appliesBleed()) {
                        target.bleedTurns = Math.max(target.bleedTurns, 3);
                    }
                    addEvent(
                        events,
                        actionCount,
                        roomIndex + 1,
                        skill.id(),
                        actor.name,
                        target.name,
                        actor.name + " 使用 " + skill.name() + "，造成 " + damage + " 点伤害。",
                        damage,
                        actor.hp,
                        target.hp,
                        actor.player ? "player" : actor.boss ? "danger" : "enemy"
                    );
                    if (!target.alive()) {
                        if (!target.player) {
                            monstersKilled++;
                        }
                        addEvent(events, actionCount, roomIndex + 1, "death", target.name, null, target.name + " 被击败。", 0, actor.hp, 0, "victory");
                        break;
                    }
                    if (!actor.player && request.modifierIds().contains("bloodthirst") && damage > 0) {
                        int heal = Math.max(1, (int) Math.round(damage * 0.14));
                        actor.hp = Math.min(actor.maxHp, actor.hp + heal);
                        addEvent(events, actionCount, roomIndex + 1, "heal", actor.name, actor.name, actor.name + " 因嗜血恢复 " + heal + " 生命。", heal, actor.hp, target.hp, "heal");
                    }
                    if (target.boss && target.hp <= target.maxHp * 0.45 && !target.enraged) {
                        target.enraged = true;
                        target.attack = (int) Math.round(target.attack * 1.22);
                        addEvent(events, actionCount, roomIndex + 1, "phase", target.name, actor.name, target.name + " 进入深渊狂暴阶段。", 0, target.hp, actor.hp, "danger");
                    }
                    actor.nextActionAt += Math.max(30, 10000 / Math.max(20, actor.speed));
                }
                if (!player.alive() || actionCount >= MAX_ACTIONS) {
                    break;
                }
            }
            if (!player.alive() || actionCount >= MAX_ACTIONS) {
                break;
            }
            if (roomIndex < request.rooms().size() - 1) {
                double recoveryRate = request.modifierIds().contains("withered") ? 0.035 : 0.085;
                int recover = Math.max(1, (int) Math.round(player.maxHp * recoveryRate));
                player.hp = Math.min(player.maxHp, player.hp + recover);
                addEvent(events, actionCount, roomIndex + 1, "recover", player.name, player.name, "短暂整备，恢复 " + recover + " 生命。", recover, player.hp, 0, "heal");
            }
        }

        boolean success = player.alive() && actionCount < MAX_ACTIONS;
        String rating = rating(success, player.hp, player.maxHp, actionCount, request.modifierIds().size());
        int score = score(request.tier(), success, rating, player.hp, player.maxHp, actionCount, request.modifierIds());
        addEvent(events, actionCount, request.rooms().size(), success ? "clear" : "fail", player.name, null, success ? "深渊裂隙通关。" : "深渊挑战失败。", score, player.hp, 0, success ? "victory" : "danger");
        return new BattleResult(success, rating, score, actionCount, monstersKilled, player.hp, player.maxHp, events);
    }

    private BattleStats applyPlayerModifiers(BattleStats stats, List<String> modifiers) {
        return stats;
    }

    private BattleStats applyEnemyModifiers(BattleStats stats, boolean boss, List<String> modifiers) {
        double hp = 1.0;
        double attack = 1.0;
        double armor = 1.0;
        double speed = 1.0;
        if (modifiers.contains("ironwall")) {
            armor += 0.18;
        }
        if (modifiers.contains("swift")) {
            speed += 0.18;
        }
        if (boss && modifiers.contains("greed")) {
            hp += 0.20;
            attack += 0.16;
        }
        if (boss && modifiers.contains("voidlord")) {
            hp += 0.22;
        }
        return new BattleStats(
            stats.level(),
            scale(stats.maxHp(), hp),
            scale(stats.attack(), attack),
            scale(stats.armor(), armor),
            scale(stats.resistance(), armor),
            scale(stats.speed(), speed),
            stats.accuracy(),
            stats.evasion(),
            stats.critChance(),
            stats.critResist(),
            stats.damageType()
        );
    }

    private int scale(int value, double multiplier) {
        return Math.max(1, (int) Math.round(value * multiplier));
    }

    private Skill chooseSkill(UnitState actor, UnitState target, String profession, List<String> modifiers, BuildCombatPlan plan) {
        if (!actor.player && actor.actionSerial % 4 == 0) {
            return new Skill("heavy", "深渊重击", 1.38, false);
        }
        if (actor.player && plan != null && plan.skills() != null && !plan.skills().isEmpty()) {
            Skill planned = choosePlannedSkill(actor, target, plan);
            if (planned != null) {
                return planned;
            }
        }
        if (actor.player && actor.actionSerial % 3 == 0) {
            return switch (profession) {
                case "mage" -> new Skill("starfire", "星火术", 1.48, modifiers.contains("greed"));
                case "ranger" -> new Skill("pierce", "穿刺箭", 1.36, modifiers.contains("greed"));
                default -> new Skill("sunder", "裂甲斩", 1.40, modifiers.contains("greed"));
            };
        }
        return new Skill("strike", actor.player ? "精准打击" : "爪击", 1.0, false);
    }

    private Skill choosePlannedSkill(UnitState actor, UnitState target, BuildCombatPlan plan) {
        double hpRatio = actor.hp / (double) Math.max(1, actor.maxHp);
        double targetRatio = target.hp / (double) Math.max(1, target.maxHp);
        if (hpRatio <= ("survival".equals(plan.strategy()) ? 0.55 : 0.38)) {
            Skill support = firstPlanned(plan, "heal", actor).orElse(firstPlanned(plan, "defensive", actor).orElse(null));
            if (support != null) {
                return support;
            }
        }
        if (targetRatio <= 0.30) {
            Skill execute = firstPlanned(plan, "execute", actor).orElse(null);
            if (execute != null) {
                return execute;
            }
        }
        if (actor.actionSerial == 1) {
            Skill opener = firstPlanned(plan, "opener", actor).orElse(null);
            if (opener != null) {
                return opener;
            }
        }
        if (actor.actionSerial % ("aggressive".equals(plan.strategy()) ? 2 : 3) == 0) {
            Skill burst = firstPlanned(plan, "burst", actor).orElse(null);
            if (burst != null) {
                return burst;
            }
        }
        return firstPlanned(plan, "default", actor)
            .orElse(firstDamagePlan(plan, actor).orElse(null));
    }

    private java.util.Optional<Skill> firstPlanned(BuildCombatPlan plan, String trigger, UnitState actor) {
        return plan.skills().stream()
            .filter(skill -> trigger.equals(skill.triggerKind()))
            .filter(skill -> skillAvailable(skill, actor))
            .findFirst()
            .map(skill -> toSkill(skill, plan));
    }

    private java.util.Optional<Skill> firstDamagePlan(BuildCombatPlan plan, UnitState actor) {
        return plan.skills().stream()
            .filter(BuildCombatPlan.PlannedSkill::damaging)
            .filter(skill -> skillAvailable(skill, actor))
            .findFirst()
            .map(skill -> toSkill(skill, plan));
    }

    private boolean skillAvailable(BuildCombatPlan.PlannedSkill skill, UnitState actor) {
        int cooldown = Math.max(1, skill.cooldown());
        return actor.actionSerial == 1 || actor.actionSerial % cooldown == 0 || "default".equals(skill.triggerKind());
    }

    private Skill toSkill(BuildCombatPlan.PlannedSkill planned, BuildCombatPlan plan) {
        double multiplier = planned.multiplier();
        if (planned.damaging()) {
            multiplier *= 1 + plan.bonus("damage");
            if ("execute".equals(planned.triggerKind())) {
                multiplier *= 1 + plan.bonus("execute");
            }
            multiplier *= switch (plan.strategy()) {
                case "aggressive" -> 1.06;
                case "speed" -> 1.02;
                default -> 1.0;
            };
        }
        return new Skill(planned.skillId(), planned.name(), multiplier, "burst".equals(planned.triggerKind()), planned.effectType(), planned.effectPower());
    }

    private int damage(UnitState actor, UnitState target, Skill skill, Random random, List<String> modifiers) {
        double hitChance = clamp(actor.accuracy - target.evasion + 0.08, 0.18, 0.98);
        if (random.nextDouble() > hitChance) {
            return 0;
        }
        double critChance = clamp(actor.critChance - target.critResist, 0.02, 0.55);
        boolean critical = random.nextDouble() <= critChance;
        double mitigation = "magic".equals(actor.damageType) ? target.resistance * 0.56 : target.armor * 0.56;
        double raw = Math.max(1, actor.attack * skill.multiplier() - mitigation);
        if (target.bleedTurns > 0) {
            raw *= 1.08;
        }
        if (critical) {
            raw *= 1.55;
        }
        return Math.max(1, (int) Math.round(raw));
    }

    private void resolveStatus(UnitState actor, List<BattleEvent> events, int actionIndex, int roomIndex) {
        if (actor.bleedTurns <= 0 || !actor.alive()) {
            return;
        }
        int damage = Math.max(1, (int) Math.round(actor.maxHp * 0.025));
        actor.takeDamage(damage);
        actor.bleedTurns--;
        addEvent(events, actionIndex, roomIndex, "bleed", actor.name, actor.name, actor.name + " 受到流血 " + damage + " 点伤害。", damage, actor.hp, actor.hp, "danger");
    }

    private String rating(boolean success, int hp, int maxHp, int actions, int modifierCount) {
        if (!success) {
            return "F";
        }
        double hpRatio = hp / (double) Math.max(1, maxHp);
        if (hpRatio >= 0.72 && actions <= 54 - modifierCount * 3) {
            return "S";
        }
        if (hpRatio >= 0.45 && actions <= 74) {
            return "A";
        }
        if (hpRatio >= 0.20) {
            return "B";
        }
        return "C";
    }

    private int score(int tier, boolean success, String rating, int hp, int maxHp, int actions, List<String> modifiers) {
        if (!success) {
            return Math.max(1, tier * 35 + modifiers.size() * 15);
        }
        int ratingBonus = switch (rating) {
            case "S" -> 420;
            case "A" -> 280;
            case "B" -> 160;
            default -> 80;
        };
        int hpBonus = (int) Math.round((hp / (double) Math.max(1, maxHp)) * 260);
        int speedBonus = Math.max(0, 180 - actions);
        return tier * 100 + modifiers.size() * 90 + ratingBonus + hpBonus + speedBonus;
    }

    private String modifierLabel(List<String> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return "无";
        }
        return String.join(" / ", modifiers);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void addEvent(List<BattleEvent> events, int turn, int roomIndex, String type, String actor, String target, String text, int value, int actorHp, int targetHp, String tone) {
        events.add(new BattleEvent(events.size(), turn, roomIndex, type, actor, target, text, value, actorHp, targetHp, tone));
    }

    private static final class UnitState {
        private final String name;
        private final boolean player;
        private final boolean boss;
        private final int maxHp;
        private int hp;
        private int attack;
        private final int armor;
        private final int resistance;
        private final int speed;
        private final double accuracy;
        private final double evasion;
        private final double critChance;
        private final double critResist;
        private final String damageType;
        private int actionSerial;
        private int nextActionAt;
        private int shield;
        private int bleedTurns;
        private boolean enraged;

        private UnitState(String name, boolean player, boolean boss, BattleStats stats) {
            this.name = name;
            this.player = player;
            this.boss = boss;
            this.maxHp = stats.maxHp();
            this.hp = stats.maxHp();
            this.attack = stats.attack();
            this.armor = stats.armor();
            this.resistance = stats.resistance();
            this.speed = stats.speed();
            this.accuracy = stats.accuracy();
            this.evasion = stats.evasion();
            this.critChance = stats.critChance();
            this.critResist = stats.critResist();
            this.damageType = stats.damageType();
            this.nextActionAt = Math.max(30, 10000 / Math.max(20, speed));
        }

        private static UnitState player(String name, String profession, BattleStats stats) {
            return new UnitState(name, true, false, stats);
        }

        private static UnitState enemy(String name, String archetype, BattleStats stats, boolean boss) {
            return new UnitState(name, false, boss, stats);
        }

        private boolean alive() {
            return hp > 0;
        }

        private void takeDamage(int damage) {
            int remaining = Math.max(0, damage);
            if (shield > 0) {
                int absorbed = Math.min(shield, remaining);
                shield -= absorbed;
                remaining -= absorbed;
            }
            hp = Math.max(0, hp - remaining);
        }
    }

    private record Skill(String id, String name, double multiplier, boolean appliesBleed, String effectType, double effectPower) {
        private Skill(String id, String name, double multiplier, boolean appliesBleed) {
            this(id, name, multiplier, appliesBleed, "damage", 0);
        }

        private boolean healing() {
            return "heal".equals(effectType);
        }

        private boolean shielding() {
            return "shield".equals(effectType);
        }
    }

    public record BattleStats(
        int level,
        int maxHp,
        int attack,
        int armor,
        int resistance,
        int speed,
        double accuracy,
        double evasion,
        double critChance,
        double critResist,
        String damageType
    ) {
    }

    public record UnitSpec(String name, String archetype, boolean boss, BattleStats stats) {
    }

    public record BattleRoom(String label, List<UnitSpec> enemies) {
    }

    public record BattleRequest(String playerName, String profession, BattleStats playerStats, int tier, List<String> modifierIds, List<BattleRoom> rooms, long seed, BuildCombatPlan combatPlan) {
    }

    public record BattleResult(boolean success, String rating, int score, int turnsTaken, int monstersKilled, int playerFinalHp, int playerMaxHp, List<BattleEvent> events) {
    }

    public record BattleEvent(int index, int turn, int roomIndex, String eventType, String actorName, String targetName, String text, int value, int actorHp, int targetHp, String tone) {
    }
}
