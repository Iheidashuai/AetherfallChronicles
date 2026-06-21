package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.common.ApiException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotSpeedService {
    public static final String SETTING_KEY = "robot.speed.multiplier";
    private static final Set<Integer> ALLOWED_MULTIPLIERS = Set.of(1, 5, 10);

    private final JdbcTemplate jdbcTemplate;
    private final RobotTickRunRepository tickRunRepository;
    private final AtomicReference<TickStats> robotTick = new AtomicReference<>(TickStats.empty("robot"));
    private final AtomicReference<TickStats> marketPulse = new AtomicReference<>(TickStats.empty("market"));

    public RobotSpeedService(JdbcTemplate jdbcTemplate, RobotTickRunRepository tickRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.tickRunRepository = tickRunRepository;
    }

    public int multiplier() {
        String value = jdbcTemplate.query(
            "SELECT setting_value FROM game_setting WHERE setting_key = ?",
            rs -> rs.next() ? rs.getString("setting_value") : null,
            SETTING_KEY
        );
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return ALLOWED_MULTIPLIERS.contains(parsed) ? parsed : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public RobotSpeedView view() {
        return new RobotSpeedView(multiplier(), robotTick.get(), marketPulse.get(), tickRunRepository.latest());
    }

    @Transactional
    public RobotSpeedView updateMultiplier(String adminUsername, int multiplier) {
        validate(multiplier);
        int before = multiplier();
        jdbcTemplate.update(
            """
            INSERT INTO game_setting (setting_key, setting_value)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
            """,
            SETTING_KEY,
            String.valueOf(multiplier)
        );
        if (before != multiplier) {
            jdbcTemplate.update(
                """
                INSERT INTO admin_action_log (admin_username, action, before_value, after_value)
                VALUES (?, 'robot_speed_update', ?, ?)
                """,
                adminUsername,
                String.valueOf(before),
                String.valueOf(multiplier)
            );
        }
        return view();
    }

    public void markRobotTickStarted(String source, int multiplier) {
        robotTick.updateAndGet(current -> current.started(source, multiplier));
    }

    public void markRobotTickFinished(String source, int multiplier, int actionCount, long durationMs) {
        robotTick.updateAndGet(current -> current.finished(source, multiplier, actionCount, durationMs));
    }

    public void markRobotTickSkipped(String source, int multiplier) {
        robotTick.updateAndGet(current -> current.skipped(source, multiplier));
    }

    public void markMarketPulseStarted(String source, int multiplier) {
        marketPulse.updateAndGet(current -> current.started(source, multiplier));
    }

    public void markMarketPulseFinished(String source, int multiplier, int actionCount, long durationMs) {
        marketPulse.updateAndGet(current -> current.finished(source, multiplier, actionCount, durationMs));
    }

    public void markMarketPulseSkipped(String source, int multiplier) {
        marketPulse.updateAndGet(current -> current.skipped(source, multiplier));
    }

    private void validate(int multiplier) {
        if (!ALLOWED_MULTIPLIERS.contains(multiplier)) {
            throw ApiException.badRequest("机器人加速倍率只支持 1、5、10");
        }
    }

    public record RobotSpeedView(
        int multiplier,
        TickStats robotTick,
        TickStats marketPulse,
        RobotTickRunRepository.RobotTickRunView simulation
    ) {
    }

    public record TickStats(
        String kind,
        boolean running,
        int multiplier,
        int lastActionCount,
        long lastDurationMs,
        long skippedCount,
        Instant lastStartedAt,
        Instant lastFinishedAt,
        Instant lastSkippedAt,
        String lastSource
    ) {
        private static TickStats empty(String kind) {
            return new TickStats(kind, false, 1, 0, 0, 0, null, null, null, "none");
        }

        private TickStats started(String source, int multiplier) {
            return new TickStats(kind, true, multiplier, lastActionCount, lastDurationMs, skippedCount, Instant.now(), lastFinishedAt, lastSkippedAt, source);
        }

        private TickStats finished(String source, int multiplier, int actionCount, long durationMs) {
            return new TickStats(kind, false, multiplier, actionCount, durationMs, skippedCount, lastStartedAt, Instant.now(), lastSkippedAt, source);
        }

        private TickStats skipped(String source, int multiplier) {
            return new TickStats(kind, running, multiplier, lastActionCount, lastDurationMs, skippedCount + 1, lastStartedAt, lastFinishedAt, Instant.now(), source);
        }
    }
}
