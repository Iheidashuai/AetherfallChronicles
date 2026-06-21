package com.mythicrealm.api.gameplay.robot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RobotActivityService {
    private final RobotSpeedService robotSpeedService;
    private final RobotSimulationEngine simulationEngine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RobotActivityService(
        RobotSpeedService robotSpeedService,
        RobotSimulationEngine simulationEngine
    ) {
        this.robotSpeedService = robotSpeedService;
        this.simulationEngine = simulationEngine;
    }

    @Scheduled(initialDelay = 6_000, fixedDelay = 5_000)
    public void simulateTick() {
        simulateTick("scheduled");
    }

    public void triggerNow(String source) {
        CompletableFuture.runAsync(() -> simulateTick(source));
    }

    private void simulateTick(String source) {
        int multiplier = robotSpeedService.multiplier();
        if (!running.compareAndSet(false, true)) {
            robotSpeedService.markRobotTickSkipped(source, multiplier);
            simulationEngine.recordSkipped(source, multiplier);
            return;
        }
        robotSpeedService.markRobotTickStarted(source, multiplier);
        try {
            RobotTickRunResult result = simulationEngine.runTick(source, multiplier);
            robotSpeedService.markRobotTickFinished(source, multiplier, result.executedCount(), result.totalMs());
        } finally {
            running.set(false);
        }
    }
}
