package com.mythicrealm.api.gameplay.robot;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RobotTickRunRepository {
    private final JdbcTemplate jdbcTemplate;

    public RobotTickRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(RobotTickRunResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO robot_tick_run
                (source, multiplier, robot_count, planned_count, executed_count, deferred_count, failed_count,
                 decision_ms, execution_ms, total_ms, skipped, backpressure_active, budget_factor,
                 execution_parallelism, action_summary, started_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            result.source(),
            result.multiplier(),
            result.robotCount(),
            result.plannedCount(),
            result.executedCount(),
            result.deferredCount(),
            result.failedCount(),
            result.decisionMs(),
            result.executionMs(),
            result.totalMs(),
            result.skipped(),
            result.backpressureActive(),
            result.budgetFactor(),
            result.executionParallelism(),
            result.actionSummary(),
            Timestamp.from(result.startedAt()),
            Timestamp.from(result.finishedAt())
        );
    }

    public RobotTickRunView latest() {
        return jdbcTemplate.query(
            """
            SELECT id, source, multiplier, robot_count, planned_count, executed_count, deferred_count, failed_count,
                   decision_ms, execution_ms, total_ms, skipped, backpressure_active, budget_factor,
                   execution_parallelism, action_summary, started_at, finished_at
            FROM robot_tick_run
            ORDER BY id DESC
            LIMIT 1
            """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new RobotTickRunView(
                    rs.getLong("id"),
                    rs.getString("source"),
                    rs.getInt("multiplier"),
                    rs.getInt("robot_count"),
                    rs.getInt("planned_count"),
                    rs.getInt("executed_count"),
                    rs.getInt("deferred_count"),
                    rs.getInt("failed_count"),
                    rs.getLong("decision_ms"),
                    rs.getLong("execution_ms"),
                    rs.getLong("total_ms"),
                    rs.getBoolean("skipped"),
                    rs.getBoolean("backpressure_active"),
                    rs.getDouble("budget_factor"),
                    rs.getInt("execution_parallelism"),
                    rs.getString("action_summary"),
                    timestamp(rs.getTimestamp("started_at")),
                    timestamp(rs.getTimestamp("finished_at"))
                );
            }
        );
    }

    private static Instant timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record RobotTickRunView(
        long id,
        String source,
        int multiplier,
        int robotCount,
        int plannedCount,
        int executedCount,
        int deferredCount,
        int failedCount,
        long decisionMs,
        long executionMs,
        long totalMs,
        boolean skipped,
        boolean backpressureActive,
        double budgetFactor,
        int executionParallelism,
        String actionSummary,
        Instant startedAt,
        Instant finishedAt
    ) {
    }
}
