package com.mythicrealm.api.gameplay.stamina;

import com.mythicrealm.api.gameplay.common.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaminaService {
    public static final int MAX_STAMINA = 1000;
    public static final int RECOVERY_SECONDS = 180;

    private final JdbcTemplate jdbcTemplate;

    public StaminaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public StaminaSnapshot snapshot(long playerId) {
        return settle(playerId);
    }

    @Transactional
    public StaminaSnapshot consume(long playerId, int amount) {
        int cost = Math.max(0, amount);
        StaminaSnapshot snapshot = settle(playerId);
        if (cost == 0) {
            return snapshot;
        }
        if (snapshot.current() < cost) {
            throw ApiException.badRequest("Stamina is not enough: need " + cost + ", current " + snapshot.current());
        }
        int next = snapshot.current() - cost;
        Instant nextUpdatedAt = snapshot.current() >= MAX_STAMINA ? Instant.now() : snapshot.updatedAt();
        jdbcTemplate.update(
            "UPDATE player SET stamina_current = ?, stamina_updated_at = ? WHERE id = ?",
            next,
            Timestamp.from(nextUpdatedAt),
            playerId
        );
        return toSnapshot(next, nextUpdatedAt);
    }

    @Transactional
    public StaminaSnapshot add(long playerId, int amount) {
        int gain = Math.max(0, amount);
        StaminaSnapshot snapshot = settle(playerId);
        if (gain == 0) {
            return snapshot;
        }
        int next = Math.min(MAX_STAMINA, snapshot.current() + gain);
        Instant nextUpdatedAt = next >= MAX_STAMINA ? Instant.now() : snapshot.updatedAt();
        jdbcTemplate.update(
            "UPDATE player SET stamina_current = ?, stamina_updated_at = ? WHERE id = ?",
            next,
            Timestamp.from(nextUpdatedAt),
            playerId
        );
        return toSnapshot(next, nextUpdatedAt);
    }

    private StaminaSnapshot settle(long playerId) {
        var rows = jdbcTemplate.query(
            "SELECT stamina_current, stamina_updated_at FROM player WHERE id = ?",
            (rs, rowNum) -> new RawStamina(
                Math.max(0, Math.min(MAX_STAMINA, rs.getInt("stamina_current"))),
                rs.getTimestamp("stamina_updated_at").toInstant()
            ),
            playerId
        );
        if (rows.isEmpty()) {
            throw ApiException.notFound("Player stamina not found");
        }
        RawStamina raw = rows.getFirst();
        Instant now = Instant.now();
        long elapsedSeconds = Math.max(0, Duration.between(raw.updatedAt(), now).getSeconds());
        if (raw.current() < MAX_STAMINA && elapsedSeconds >= RECOVERY_SECONDS) {
            int next = MAX_STAMINA;
            Instant nextUpdatedAt = now;
            jdbcTemplate.update(
                "UPDATE player SET stamina_current = ?, stamina_updated_at = ? WHERE id = ?",
                next,
                Timestamp.from(nextUpdatedAt),
                playerId
            );
            return toSnapshot(next, nextUpdatedAt);
        }
        if (raw.current() >= MAX_STAMINA) {
            return toSnapshot(MAX_STAMINA, raw.updatedAt());
        }
        return toSnapshot(raw.current(), raw.updatedAt());
    }

    private StaminaSnapshot toSnapshot(int current, Instant updatedAt) {
        if (current >= MAX_STAMINA) {
            return new StaminaSnapshot(current, MAX_STAMINA, 0, 0, updatedAt);
        }
        Instant now = Instant.now();
        long elapsedSeconds = Math.max(0, Duration.between(updatedAt, now).getSeconds());
        int secondsUntilFull = (int) Math.max(0, RECOVERY_SECONDS - elapsedSeconds);
        return new StaminaSnapshot(current, MAX_STAMINA, secondsUntilFull, secondsUntilFull, updatedAt);
    }

    private record RawStamina(int current, Instant updatedAt) {
    }

    public record StaminaSnapshot(
        int current,
        int max,
        int secondsUntilNext,
        long secondsUntilFull,
        Instant updatedAt
    ) {
    }
}
