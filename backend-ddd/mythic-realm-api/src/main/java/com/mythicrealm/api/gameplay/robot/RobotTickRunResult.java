package com.mythicrealm.api.gameplay.robot;

import java.time.Instant;

public record RobotTickRunResult(
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
    public static RobotTickRunResult skipped(String source, int multiplier) {
        Instant now = Instant.now();
        return new RobotTickRunResult(source, multiplier, 0, 0, 0, 0, 0, 0, 0, 0, true, false, 1.0, 0, "{}", now, now);
    }
}
