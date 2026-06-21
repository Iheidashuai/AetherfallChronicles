package com.mythicrealm.api.gameplay.dungeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mythicrealm.api.gameplay.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SweepTicketPolicyTest {
    @Test
    void sweepOnlySupportsTenOrFiftyRuns() {
        assertThat(DungeonService.requireSweepTimes(10)).isEqualTo(10);
        assertThat(DungeonService.requireSweepTimes(50)).isEqualTo(50);

        assertThatThrownBy(() -> DungeonService.requireSweepTimes(9))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("10 次或 50 次");
    }

    @Test
    void latestSchemaDefinesSweepTicketsAndShopOffers() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertThat(schema).contains("run_type VARCHAR(16) NOT NULL DEFAULT 'manual'");
        assertThat(schema).contains("'ticket_sweep_normal', '普通扫荡符'");
        assertThat(schema).contains("'ticket_sweep_special', '特殊扫荡符'");
        assertThat(schema).contains("'sweepTicket'");
        assertThat(schema).contains("'sweep_ticket_normal_pack', '普通扫荡符包'");
        assertThat(schema).contains("'sweep_ticket_special_pack', '特殊扫荡符包'");
        assertThat(schema).contains("'sweep', 8, 'ticket_sweep_normal', 50");
        assertThat(schema).contains("'sweep', 24, 'ticket_sweep_special', 10");
    }
}
