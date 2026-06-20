package com.mythicrealm.api.gameplay.stamina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class StaminaServiceTest {
    @Test
    void snapshotRefillsToMaxAfterRecoveryWindow() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        StaminaService service = new StaminaService(jdbcTemplate);
        Instant updatedAt = Instant.now().minusSeconds(StaminaService.RECOVERY_SECONDS + 1L);

        when(jdbcTemplate.query(anyString(), anyRowMapper(), anyLong())).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getInt("stamina_current")).thenReturn(420);
            when(resultSet.getTimestamp("stamina_updated_at")).thenReturn(Timestamp.from(updatedAt));
            return List.of(mapper.mapRow(resultSet, 0));
        });

        StaminaService.StaminaSnapshot snapshot = service.snapshot(1L);

        assertThat(snapshot.current()).isEqualTo(1000);
        assertThat(snapshot.max()).isEqualTo(1000);
        assertThat(snapshot.secondsUntilNext()).isZero();
        assertThat(snapshot.secondsUntilFull()).isZero();
        verify(jdbcTemplate).update(
            eq("UPDATE player SET stamina_current = ?, stamina_updated_at = ? WHERE id = ?"),
            eq(1000),
            any(Timestamp.class),
            eq(1L)
        );
    }

    private RowMapper<Object> anyRowMapper() {
        return any();
    }
}
