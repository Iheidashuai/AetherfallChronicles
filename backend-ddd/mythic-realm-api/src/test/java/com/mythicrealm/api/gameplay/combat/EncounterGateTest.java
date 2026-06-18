package com.mythicrealm.api.gameplay.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncounterGateTest {
    private final EncounterGate gate = new EncounterGate();

    @Test
    void lowLevelCannotEnter() {
        var status = gate.evaluate(player(4), 2000, dungeon(5, 1000));

        assertThat(status.eligible()).isFalse();
        assertThat(status.missingLevel()).isEqualTo(1);
    }

    @Test
    void lowPowerCannotEnter() {
        var status = gate.evaluate(player(5), 900, dungeon(5, 1000));

        assertThat(status.eligible()).isFalse();
        assertThat(status.missingPower()).isEqualTo(100);
    }

    @Test
    void matchingLevelAndPowerCanEnter() {
        var status = gate.evaluate(player(5), 1000, dungeon(5, 1000));

        assertThat(status.eligible()).isTrue();
        assertThat(status.missingLevel()).isZero();
        assertThat(status.missingPower()).isZero();
    }

    private PlayerRecord player(int level) {
        return new PlayerRecord(1, 1, "hero", "warrior", level, 0, 100, 10, 5, 8, 3, 4, 0);
    }

    private DungeonConfig dungeon(int minimumLevel, int minimumPower) {
        return new DungeonConfig("d1", "Dungeon", "desc", "normal", List.of(), minimumLevel, minimumPower, minimumLevel, minimumPower, "boss", 10);
    }
}
