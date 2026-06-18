package com.mythicrealm.api.gameplay.combat;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.RoomMonster;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CombatSimulationCalibrator {
    private final CombatStatsService combatStatsService;
    private final CombatEngine combatEngine;

    public CombatSimulationCalibrator(CombatStatsService combatStatsService, CombatEngine combatEngine) {
        this.combatStatsService = combatStatsService;
        this.combatEngine = combatEngine;
    }

    public DungeonCalibrationReport simulateDungeon(
        DungeonConfig dungeon,
        List<MonsterConfig> monsters,
        CombatStats playerStats,
        int samples,
        long seed
    ) {
        Map<String, MonsterConfig> monsterById = monsters.stream()
            .collect(Collectors.toMap(MonsterConfig::id, Function.identity()));
        Combatant player = new Combatant("simulation", "Simulation Hero", "player", "simulation", "none", false, playerStats);
        List<RunSample> runs = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            runs.add(simulateRun(dungeon, monsterById, player, new Random(seed + i * 31L)));
        }
        return DungeonCalibrationReport.from(dungeon, playerStats, runs);
    }

    public CombatStats scaledPlayerStats(CombatStats base, double targetPowerRatio, CombatPowerService powerService) {
        int basePower = Math.max(1, powerService.combatPower(base));
        double targetPower = basePower * Math.max(0.10, targetPowerRatio);
        double scale = Math.sqrt(targetPower / basePower);
        return scaleStats(base, scale);
    }

    public CombatStats scaleStats(CombatStats base, double scale) {
        double safeScale = Math.max(0.10, scale);
        return new CombatStats(
            base.level(),
            scaled(base.maxHp(), safeScale),
            scaled(base.maxMp(), safeScale),
            scaled(base.attackPower(), safeScale),
            scaled(base.armor(), safeScale),
            scaled(base.resistance(), safeScale),
            Math.max(50, (int) Math.round(base.speed() * Math.sqrt(safeScale))),
            DamageCalculator.clamp(base.accuracy(), 0.70, 0.98),
            DamageCalculator.clamp(base.evasion(), 0.01, 0.35),
            DamageCalculator.clamp(base.critChance(), 0.02, 0.50),
            base.critDamage(),
            DamageCalculator.clamp(base.critResist(), 0.00, 0.30),
            base.damageType()
        );
    }

    public CombatStats syntheticPlayerForDungeon(DungeonConfig dungeon, String profession, double powerRatio, CombatPowerService powerService) {
        CombatStats base = syntheticBaseStats(dungeon.recommendedLevel(), profession);
        int targetPower = Math.max(1, (int) Math.round(dungeon.minimumPower() * powerRatio));
        for (int i = 0; i < 16; i++) {
            int currentPower = powerService.combatPower(base);
            if (Math.abs(currentPower - targetPower) <= Math.max(20, targetPower * 0.015)) {
                return base;
            }
            double scale = Math.sqrt(targetPower / (double) Math.max(1, currentPower));
            base = scaleStats(base, scale);
        }
        return base;
    }

    public CombatStats syntheticEquippedPlayerForDungeon(
        DungeonConfig dungeon,
        String profession,
        double powerRatio,
        CombatPowerService powerService
    ) {
        int targetPower = Math.max(1, (int) Math.round(dungeon.minimumPower() * powerRatio));
        return syntheticEquippedPlayerForPower(dungeon.recommendedLevel(), profession, targetPower, powerService);
    }

    public CombatStats syntheticEquippedPlayerForPower(
        int level,
        String profession,
        int targetPower,
        CombatPowerService powerService
    ) {
        CombatStats base = syntheticBaseStats(level, profession);
        return tuneTowardPower(withExpectedEquipment(base), Math.max(1, targetPower), powerService);
    }

    public CombatStats syntheticBaseStats(int level, String profession) {
        PlayerRecord player = switch (profession) {
            case "ranger" -> new PlayerRecord(1, 1, "Simulation Ranger", "ranger", level, 0, 0, 6 + level, 10 + level * 2, 5 + level, 4 + level, 5 + level, 0);
            case "mage" -> new PlayerRecord(1, 1, "Simulation Mage", "mage", level, 0, 0, 3 + level, 4 + level, 4 + level, 10 + level * 2, 9 + level, 0);
            default -> new PlayerRecord(1, 1, "Simulation Warrior", "warrior", level, 0, 0, 10 + level * 2, 5 + level, 8 + level * 2, 3 + level, 4 + level, 0);
        };
        return combatStatsService.playerStats(player, List.of());
    }

    private CombatStats tuneTowardPower(CombatStats base, int targetPower, CombatPowerService powerService) {
        CombatStats tuned = base;
        for (int i = 0; i < 18; i++) {
            int currentPower = powerService.combatPower(tuned);
            if (Math.abs(currentPower - targetPower) <= Math.max(20, targetPower * 0.015)) {
                return tuned;
            }
            double scale = Math.sqrt(targetPower / (double) Math.max(1, currentPower));
            tuned = equippedShape(tuned, scale);
        }
        return tuned;
    }

    private CombatStats withExpectedEquipment(CombatStats base) {
        int level = Math.max(1, base.level());
        return new CombatStats(
            base.level(),
            base.maxHp() + 170 + level * 40,
            base.maxMp() + 10 + level * 3,
            base.attackPower() + 20 + level * 7,
            base.armor() + 22 + level * 6,
            base.resistance() + 12 + level * 5,
            base.speed(),
            base.accuracy(),
            base.evasion(),
            DamageCalculator.clamp(base.critChance() + 0.025 + level * 0.001, 0.02, 0.50),
            base.critDamage(),
            base.critResist(),
            base.damageType()
        );
    }

    private CombatStats equippedShape(CombatStats base, double scale) {
        double safeScale = Math.max(0.10, scale);
        return new CombatStats(
            base.level(),
            scaled(base.maxHp(), 1.0 + (safeScale - 1.0) * 1.08),
            scaled(base.maxMp(), 1.0 + (safeScale - 1.0) * 0.72),
            scaled(base.attackPower(), 1.0 + (safeScale - 1.0) * 0.95),
            scaled(base.armor(), 1.0 + (safeScale - 1.0) * 1.02),
            scaled(base.resistance(), 1.0 + (safeScale - 1.0) * 1.00),
            Math.max(50, (int) Math.round(base.speed() * (1.0 + (Math.sqrt(safeScale) - 1.0) * 0.35))),
            DamageCalculator.clamp(base.accuracy(), 0.70, 0.98),
            DamageCalculator.clamp(base.evasion(), 0.01, 0.35),
            DamageCalculator.clamp(base.critChance(), 0.02, 0.50),
            base.critDamage(),
            DamageCalculator.clamp(base.critResist(), 0.00, 0.30),
            base.damageType()
        );
    }

    private RunSample simulateRun(
        DungeonConfig dungeon,
        Map<String, MonsterConfig> monsterById,
        Combatant player,
        Random random
    ) {
        int playerHp = player.stats().maxHp();
        int playerMaxHp = player.stats().maxHp();
        int totalRounds = 0;
        int normalEncounters = 0;
        int normalRounds = 0;
        int bossEncounters = 0;
        int bossRounds = 0;
        int bossPhaseEvents = 0;
        boolean success = true;
        for (int roomIndex = 0; roomIndex < dungeon.rooms().size() && success; roomIndex++) {
            var room = dungeon.rooms().get(roomIndex);
            for (RoomMonster roomMonster : room.monsters()) {
                MonsterConfig monster = monsterById.get(roomMonster.monsterId());
                if (monster == null) {
                    throw new IllegalArgumentException("Missing monster config: " + roomMonster.monsterId());
                }
                for (int i = 0; i < roomMonster.count(); i++) {
                    CombatEngine.EncounterOutcome outcome = combatEngine.fight(
                        player,
                        combatStatsService.monsterCombatant(monster),
                        playerHp,
                        roundLimit(),
                        random
                    );
                    int encounterRounds = encounterRounds(outcome);
                    totalRounds += encounterRounds;
                    if (monster.isBoss()) {
                        bossEncounters++;
                        bossRounds += encounterRounds;
                        bossPhaseEvents += (int) outcome.events().stream()
                            .filter(event -> "phase".equals(event.eventType()))
                            .count();
                    } else {
                        normalEncounters++;
                        normalRounds += encounterRounds;
                    }
                    playerHp = outcome.playerHp();
                    if (outcome.playerDefeated() || !outcome.enemyDefeated()) {
                        success = false;
                        break;
                    }
                }
                if (!success) {
                    break;
                }
            }
            if (success && playerHp > 0 && roomIndex < dungeon.rooms().size() - 1) {
                int recover = Math.max(1, (int) Math.round(playerMaxHp * roomRecoveryRate(player.stats())));
                playerHp = Math.min(playerMaxHp, playerHp + recover);
            }
        }
        return new RunSample(
            success,
            playerHp,
            playerMaxHp,
            totalRounds,
            normalEncounters,
            normalRounds,
            bossEncounters,
            bossRounds,
            bossPhaseEvents
        );
    }

    private int roundLimit() {
        return CombatEngine.DEFAULT_ROUND_LIMIT;
    }

    private double roomRecoveryRate(CombatStats stats) {
        return DamageCalculator.clamp(0.04 + stats.critResist() * 0.75, 0.04, 0.10);
    }

    private int encounterRounds(CombatEngine.EncounterOutcome outcome) {
        return (int) outcome.events().stream()
            .filter(event -> !"system".equals(event.actor()))
            .filter(event -> "player".equals(event.actor()))
            .count();
    }

    private int scaled(int value, double scale) {
        return Math.max(1, (int) Math.round(value * scale));
    }

    private record RunSample(
        boolean success,
        int finalHp,
        int maxHp,
        int totalRounds,
        int normalEncounters,
        int normalRounds,
        int bossEncounters,
        int bossRounds,
        int bossPhaseEvents
    ) {
    }

    public record DungeonCalibrationReport(
        String dungeonId,
        int samples,
        double winRate,
        double averageFinalHpRatio,
        double averageTotalRounds,
        double averageNormalRounds,
        double averageBossRounds,
        double bossMechanicCoverage,
        int playerPower,
        int playerLevel
    ) {
        static DungeonCalibrationReport from(DungeonConfig dungeon, CombatStats playerStats, List<RunSample> runs) {
            int sampleCount = Math.max(1, runs.size());
            double wins = runs.stream().filter(RunSample::success).count();
            DoubleSummaryStatistics finalHpRatio = runs.stream()
                .filter(RunSample::success)
                .mapToDouble(run -> run.finalHp() / (double) Math.max(1, run.maxHp()))
                .summaryStatistics();
            int normalEncounterCount = runs.stream().mapToInt(RunSample::normalEncounters).sum();
            int bossEncounterCount = runs.stream().mapToInt(RunSample::bossEncounters).sum();
            int bossPhaseEvents = runs.stream().mapToInt(RunSample::bossPhaseEvents).sum();
            return new DungeonCalibrationReport(
                dungeon.id(),
                sampleCount,
                wins / sampleCount,
                finalHpRatio.getCount() == 0 ? 0 : finalHpRatio.getAverage(),
                runs.stream().mapToInt(RunSample::totalRounds).average().orElse(0),
                normalEncounterCount == 0 ? 0 : runs.stream().mapToInt(RunSample::normalRounds).sum() / (double) normalEncounterCount,
                bossEncounterCount == 0 ? 0 : runs.stream().mapToInt(RunSample::bossRounds).sum() / (double) bossEncounterCount,
                bossEncounterCount == 0 ? 0 : bossPhaseEvents / (double) bossEncounterCount,
                0,
                playerStats.level()
            );
        }

        public DungeonCalibrationReport withPlayerPower(int power) {
            return new DungeonCalibrationReport(
                dungeonId,
                samples,
                winRate,
                averageFinalHpRatio,
                averageTotalRounds,
                averageNormalRounds,
                averageBossRounds,
                bossMechanicCoverage,
                power,
                playerLevel
            );
        }
    }
}
