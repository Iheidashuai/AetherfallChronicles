package com.mythicrealm.api.gameplay.arena;

import com.mythicrealm.api.gameplay.combat.CombatEngine;
import com.mythicrealm.api.gameplay.combat.CombatSkill;
import com.mythicrealm.api.gameplay.combat.CombatStats;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.combat.Combatant;
import com.mythicrealm.api.gameplay.build.BuildCombatPlanService;
import com.mythicrealm.api.gameplay.endgame.combat.BuildCombatPlan;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import com.mythicrealm.api.gameplay.robot.RobotActivityLogService;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaBattleEvent;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaBattleResult;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaFighter;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaPlayer;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaSkill;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaStats;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaActivityPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaCombatPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaLoadoutPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaPlayerPort;
import com.mythicrealm.domain.arena.port.ArenaPorts.ArenaRewardPort;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

public final class ArenaAdapters {
    private ArenaAdapters() {
    }

    @Component
    static class JdbcArenaPlayerAdapter implements ArenaPlayerPort {
        private final JdbcTemplate jdbcTemplate;
        private final PlayerService playerService;

        JdbcArenaPlayerAdapter(JdbcTemplate jdbcTemplate, PlayerService playerService) {
            this.jdbcTemplate = jdbcTemplate;
            this.playerService = playerService;
        }

        @Override
        public ArenaPlayer requirePlayer(long playerId) {
            return findPlayer(playerId).orElseGet(() -> toArenaPlayer(playerService.requireById(playerId), controllerType(playerId)));
        }

        @Override
        public Optional<ArenaPlayer> findPlayer(long playerId) {
            return jdbcTemplate.query(
                "SELECT id, name, profession, level, controller_type FROM player WHERE id = ?",
                (rs, rowNum) -> new ArenaPlayer(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("profession"),
                    rs.getInt("level"),
                    rs.getString("controller_type")
                ),
                playerId
            ).stream().findFirst();
        }

        @Override
        public List<ArenaPlayer> activePlayers(int limit) {
            return jdbcTemplate.query(
                """
                SELECT id, name, profession, level, controller_type
                FROM player
                ORDER BY level DESC, updated_at DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new ArenaPlayer(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("profession"),
                    rs.getInt("level"),
                    rs.getString("controller_type")
                ),
                limit
            );
        }

        private ArenaPlayer toArenaPlayer(PlayerRecord player, String controllerType) {
            return new ArenaPlayer(player.id(), player.name(), player.profession(), player.level(), controllerType);
        }

        private String controllerType(long playerId) {
            return jdbcTemplate.query(
                "SELECT controller_type FROM player WHERE id = ?",
                (rs, rowNum) -> rs.getString("controller_type"),
                playerId
            ).stream().findFirst().orElse("player");
        }
    }

    @Component
    static class CurrentArenaLoadoutAdapter implements ArenaLoadoutPort {
        private final PlayerService playerService;
        private final InventoryService inventoryService;
        private final CombatStatsService combatStatsService;
        private final BuildCombatPlanService buildCombatPlanService;

        CurrentArenaLoadoutAdapter(
            PlayerService playerService,
            InventoryService inventoryService,
            CombatStatsService combatStatsService,
            BuildCombatPlanService buildCombatPlanService
        ) {
            this.playerService = playerService;
            this.inventoryService = inventoryService;
            this.combatStatsService = combatStatsService;
            this.buildCombatPlanService = buildCombatPlanService;
        }

        @Override
        public ArenaFighter fighter(long playerId) {
            PlayerRecord player = playerService.requireById(playerId);
            var equipment = inventoryService.equippedItems(player.id()).values();
            CombatStats stats = combatStatsService.playerStats(player, equipment);
            BuildCombatPlan plan = buildCombatPlanService.activePlan(player);
            List<String> equipmentSummary = equipment.stream()
                .sorted((left, right) -> left.itemType().compareTo(right.itemType()))
                .map(item -> item.displayName() + " · " + item.requiredLevel() + "级")
                .toList();
            return new ArenaFighter(
                player.id(),
                player.name(),
                player.profession(),
                player.level(),
                inventoryService.combatPower(player),
                new ArenaStats(
                    stats.level(),
                    stats.maxHp(),
                    stats.maxMp(),
                    stats.attackPower(),
                    stats.armor(),
                    stats.resistance(),
                    stats.speed(),
                    stats.accuracy(),
                    stats.evasion(),
                    stats.critChance(),
                    stats.critDamage(),
                    stats.critResist(),
                    stats.damageType()
                ),
                plan.skills().stream()
                    .map(skill -> new ArenaSkill(
                        skill.skillId(),
                        skill.name(),
                        skill.triggerKind(),
                        skill.damageType(),
                        skill.multiplier(),
                        skill.cooldown(),
                        skill.mpCost(),
                        skill.effectType(),
                        skill.effectPower(),
                        skill.durationRounds(),
                        skill.priority()
                    ))
                    .toList(),
                equipmentSummary,
                plan.name(),
                plan.strategy()
            );
        }
    }

    @Component
    static class ExistingCombatEngineArenaAdapter implements ArenaCombatPort {
        private final CombatEngine combatEngine;

        ExistingCombatEngineArenaAdapter(CombatEngine combatEngine) {
            this.combatEngine = combatEngine;
        }

        @Override
        public ArenaBattleResult fight(ArenaFighter attacker, ArenaFighter defender, Random random) {
            Combatant attackSide = combatant(attacker, "player");
            Combatant defenseSide = combatant(defender, "enemy");
            var result = combatEngine.fight(
                attackSide,
                defenseSide,
                attacker.stats().maxHp(),
                skills(attacker.skills()),
                skills(defender.skills()),
                CombatEngine.DEFAULT_ROUND_LIMIT,
                random
            );
            AtomicInteger sequence = new AtomicInteger(1);
            List<ArenaBattleEvent> events = result.events().stream()
                .map(event -> new ArenaBattleEvent(
                    sequence.getAndIncrement(),
                    "enemy".equals(event.actor()) ? "defender" : event.actor(),
                    event.eventType(),
                    event.tone(),
                    event.text(),
                    event.playerHp(),
                    event.enemyHp(),
                    event.damage(),
                    event.critical(),
                    event.missed(),
                    event.skillName()
                ))
                .toList();
            return new ArenaBattleResult(result.enemyDefeated(), result.roundLimitReached(), result.playerHp(), result.enemyHp(), events);
        }

        private Combatant combatant(ArenaFighter fighter, String side) {
            ArenaStats stats = fighter.stats();
            return new Combatant(
                Long.toString(fighter.playerId()),
                fighter.name(),
                side,
                fighter.profession(),
                "arena_duel",
                false,
                new CombatStats(
                    stats.level(),
                    stats.maxHp(),
                    stats.maxMp(),
                    stats.attackPower(),
                    stats.armor(),
                    stats.resistance(),
                    stats.speed(),
                    stats.accuracy(),
                    stats.evasion(),
                    stats.critChance(),
                    stats.critDamage(),
                    stats.critResist(),
                    stats.damageType()
                )
            );
        }

        private List<CombatSkill> skills(List<ArenaSkill> skills) {
            return skills.stream()
                .map(skill -> new CombatSkill(
                    skill.id(),
                    skill.name(),
                    1,
                    1,
                    1,
                    "arena",
                    "enemy",
                    skill.damageType(),
                    skill.multiplier(),
                    skill.cooldown(),
                    skill.mpCost(),
                    skill.effectType(),
                    skill.effectPower(),
                    skill.durationRounds(),
                    skill.triggerKind(),
                    skill.priority(),
                    skill.id()
                ))
                .toList();
        }
    }

    @Component
    static class InventoryArenaRewardAdapter implements ArenaRewardPort {
        private final InventoryService inventoryService;

        InventoryArenaRewardAdapter(InventoryService inventoryService) {
            this.inventoryService = inventoryService;
        }

        @Override
        public List<String> grantItem(long playerId, String itemTemplateId, int quantity, Random random) {
            return inventoryService.grantItem(playerId, itemTemplateId, Math.max(1, quantity), random).stream()
                .map(item -> item.displayName() + " x" + Math.max(1, item.quantity()))
                .toList();
        }
    }

    @Component
    static class ArenaActivityAdapter implements ArenaActivityPort {
        private final RobotActivityLogService robotActivityLogService;
        private final QuestService questService;

        ArenaActivityAdapter(RobotActivityLogService robotActivityLogService, QuestService questService) {
            this.robotActivityLogService = robotActivityLogService;
            this.questService = questService;
        }

        @Override
        public void recordActivity(long playerId, String kind, String text) {
            robotActivityLogService.record(playerId, kind, text);
        }

        @Override
        public void recordQuestEvent(long playerId, String type, int amount) {
            questService.recordEvent(playerId, new QuestEvent(type, null, Math.max(1, amount)));
        }
    }
}
