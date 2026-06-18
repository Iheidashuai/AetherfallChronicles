package com.mythicrealm.api.gameplay.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CombatEngineSimulationTest {
    private final CombatEngine engine = new CombatEngine(new DamageCalculator());

    @Test
    void bossMechanicsEnterCombatLog() {
        Combatant player = new Combatant("p1", "Hero", "player", "warrior", "none", false,
            new CombatStats(10, 1200, 120, 260, 120, 90, 110, 0.95, 0.04, 0.12, 1.5, 0.02, "physical"));
        Combatant boss = new Combatant("b1", "Gate Warden", "enemy", "boss", "shield_phase", true,
            new CombatStats(10, 900, 0, 90, 70, 70, 95, 0.90, 0.04, 0.08, 1.5, 0.04, "physical"));

        CombatEngine.EncounterOutcome outcome = engine.fight(player, boss, player.stats().maxHp(), 12, new Random(7));

        assertThat(outcome.enemyDefeated()).isTrue();
        assertThat(outcome.events())
            .anySatisfy(event -> {
                assertThat(event.eventType()).isEqualTo("phase");
                assertThat(event.text()).contains("护盾阶段");
            });
    }
}
