package com.mythicrealm.api.gameplay.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonRoom;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.LootEntry;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.RoomMonster;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatSimulationCalibratorTest {
    private static final int SAMPLES = 240;

    private final DamageCalculator damageCalculator = new DamageCalculator();
    private final CombatPowerService powerService = new CombatPowerService(damageCalculator);
    private final CombatSimulationCalibrator calibrator = new CombatSimulationCalibrator(
        new CombatStatsService(),
        new CombatEngine(damageCalculator)
    );

    @Test
    void tutorialDungeonHasVisibleButForgivingRiskAtGate() {
        DungeonConfig dungeon = spiderOuterDungeon();
        List<MonsterConfig> monsters = spiderOuterMonsters();

        CombatStats gateStats = calibrator.syntheticEquippedPlayerForDungeon(dungeon, "warrior", 1.00, powerService);
        var gateReport = calibrator.simulateDungeon(dungeon, monsters, gateStats, SAMPLES, 2026061401L)
            .withPlayerPower(powerService.combatPower(gateStats));

        CombatStats advantageStats = calibrator.syntheticEquippedPlayerForPower(
            dungeon.recommendedLevel(),
            "warrior",
            (int) Math.round(dungeon.recommendedPower() * 1.15),
            powerService
        );
        var advantageReport = calibrator.simulateDungeon(dungeon, monsters, advantageStats, SAMPLES, 2026061402L)
            .withPlayerPower(powerService.combatPower(advantageStats));

        assertThat(gateReport.winRate())
            .describedAs("gate report: %s", gateReport)
            .isGreaterThanOrEqualTo(0.95);
        assertThat(gateReport.averageFinalHpRatio())
            .describedAs("gate pressure should still be visible: %s", gateReport)
            .isBetween(0.35, 0.75);
        assertThat(advantageReport.winRate())
            .describedAs("advantage report: %s", advantageReport)
            .isGreaterThanOrEqualTo(0.93);
        assertThat(gateReport.averageNormalRounds())
            .describedAs("normal monster pacing: %s", gateReport)
            .isBetween(2.0, 4.5);
        assertThat(gateReport.averageBossRounds())
            .describedAs("boss pacing: %s", gateReport)
            .isBetween(4.0, 12.0);
        assertThat(gateReport.bossMechanicCoverage())
            .describedAs("boss mechanics should be visible: %s", gateReport)
            .isGreaterThan(0.80);
    }

    @Test
    void secondDungeonAtGateIsNotTrivialAndReportsBossMechanics() {
        DungeonConfig dungeon = spiderTunnelDungeon();
        List<MonsterConfig> monsters = spiderTunnelMonsters();

        CombatStats gateStats = calibrator.syntheticEquippedPlayerForDungeon(dungeon, "ranger", 1.00, powerService);
        var report = calibrator.simulateDungeon(dungeon, monsters, gateStats, SAMPLES, 2026061403L)
            .withPlayerPower(powerService.combatPower(gateStats));
        CombatStats advantageStats = calibrator.syntheticEquippedPlayerForPower(
            dungeon.recommendedLevel(),
            "ranger",
            (int) Math.round(dungeon.recommendedPower() * 1.15),
            powerService
        );
        var advantageReport = calibrator.simulateDungeon(dungeon, monsters, advantageStats, SAMPLES, 2026061404L)
            .withPlayerPower(powerService.combatPower(advantageStats));

        assertThat(report.winRate())
            .describedAs("second dungeon report: %s", report)
            .isGreaterThanOrEqualTo(0.95);
        assertThat(report.averageFinalHpRatio())
            .describedAs("second dungeon gate pressure should still be visible: %s", report)
            .isBetween(0.45, 0.85);
        assertThat(report.averageNormalRounds())
            .describedAs("normal monster pacing: %s", report)
            .isBetween(2.0, 5.0);
        assertThat(report.averageBossRounds())
            .describedAs("boss pacing: %s", report)
            .isBetween(6.0, 12.5);
        assertThat(report.bossMechanicCoverage())
            .describedAs("boss mechanics should be visible: %s", report)
            .isGreaterThan(0.80);
        assertThat(advantageReport.winRate())
            .describedAs("second dungeon advantage report: %s", advantageReport)
            .isGreaterThanOrEqualTo(0.95);
    }

    private DungeonConfig spiderOuterDungeon() {
        return new DungeonConfig(
            "dungeon_01_spider",
            "Spider Outer",
            "calibration sample",
            "normal",
            List.of(
                room("d01_outer", false, monster("m_d01_01_spider", 1)),
                room("d01_inner", false, monster("m_d01_03_spider", 1)),
                room("d01_guard", false, monster("m_d01_05_spider", 1)),
                room("d01_boss", true, monster("m_d01_07_spider", 1))
            ),
            1,
            2800,
            1,
            2000,
            "boss",
            10
        );
    }

    private List<MonsterConfig> spiderOuterMonsters() {
        return List.of(
            monster("m_d01_01_spider", "Spider", 1, 62, 6, 7, 5, 0.8212, 0.0307, 0.0404, 0.0205, 89, "physical", "guardian", "none", false),
            monster("m_d01_03_spider", "Ambusher", 1, 86, 9, 5, 5, 0.8212, 0.0557, 0.0655, 0.0205, 117, "physical", "skirmisher", "none", false),
            monster("m_d01_05_spider", "Nest Guard", 2, 112, 11, 12, 8, 0.8224, 0.0314, 0.0409, 0.0210, 89, "physical", "guardian", "none", false),
            monster("m_d01_07_spider", "Spider Queen", 3, 230, 20, 16, 16, 0.8536, 0.0321, 0.0814, 0.0815, 99, "physical", "boss", "heavy_every_3", true)
        );
    }

    private DungeonConfig spiderTunnelDungeon() {
        return new DungeonConfig(
            "dungeon_02_spider",
            "Spider Tunnel",
            "calibration sample",
            "normal",
            List.of(
                room("d02_outer", false, monster("m_d02_01_spider", 2), monster("m_d02_02_spider", 1)),
                room("d02_inner", false, monster("m_d02_03_spider", 2), monster("m_d02_04_spider", 1)),
                room("d02_guard", false, monster("m_d02_05_spider", 2), monster("m_d02_06_spider", 1)),
                room("d02_boss", true, monster("m_d02_07_spider", 1), monster("m_d02_06_spider", 1))
            ),
            2,
            16500,
            2,
            12000,
            "boss",
            10
        );
    }

    private List<MonsterConfig> spiderTunnelMonsters() {
        return List.of(
            monster("m_d02_01_spider", "Spider", 2, 166, 20, 14, 11, 0.8224, 0.0314, 0.0409, 0.0210, 93, "physical", "brute", "none", false),
            monster("m_d02_02_spider", "Webbinder", 2, 196, 22, 12, 11, 0.8224, 0.0564, 0.0659, 0.0210, 117, "physical", "skirmisher", "none", false),
            monster("m_d02_03_spider", "Ambusher", 2, 229, 27, 11, 24, 0.8224, 0.0314, 0.0659, 0.0210, 103, "magic", "caster", "none", false),
            monster("m_d02_04_spider", "Ripper", 3, 264, 27, 26, 18, 0.8236, 0.0321, 0.0413, 0.0215, 90, "physical", "guardian", "none", false),
            monster("m_d02_05_spider", "Nest Guard", 3, 302, 34, 23, 18, 0.8236, 0.0321, 0.0413, 0.0215, 94, "physical", "brute", "none", false),
            monster("m_d02_06_spider", "Venom Walker", 3, 442, 37, 20, 18, 0.8236, 0.0571, 0.0663, 0.0215, 118, "physical", "skirmisher", "none", false),
            monster("m_d02_07_spider", "Spider Queen", 4, 970, 51, 38, 36, 0.8548, 0.0328, 0.0818, 0.0820, 99, "physical", "boss", "enrage_50", true)
        );
    }

    private DungeonRoom room(String id, boolean bossRoom, RoomMonster... monsters) {
        return new DungeonRoom(id, List.of(monsters), bossRoom);
    }

    private RoomMonster monster(String id, int count) {
        return new RoomMonster(id, count);
    }

    private MonsterConfig monster(
        String id,
        String name,
        int level,
        int maxHp,
        int attackPower,
        int armor,
        int resistance,
        double accuracy,
        double evasion,
        double critChance,
        double critResist,
        int speed,
        String damageType,
        String archetype,
        String mechanic,
        boolean boss
    ) {
        return new MonsterConfig(
            id,
            name,
            level,
            maxHp,
            attackPower,
            armor,
            resistance,
            accuracy,
            evasion,
            critChance,
            critResist,
            speed,
            damageType,
            archetype,
            mechanic,
            boss,
            List.<LootEntry>of(),
            level * 10,
            level * 4
        );
    }
}
