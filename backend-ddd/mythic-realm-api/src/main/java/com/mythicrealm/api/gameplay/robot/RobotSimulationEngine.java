package com.mythicrealm.api.gameplay.robot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythicrealm.api.gameplay.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RobotSimulationEngine {
    private final RobotPopulationSnapshotLoader snapshotLoader;
    private final RobotBrainService robotBrainService;
    private final RobotActionSupport robotActionSupport;
    private final RobotActivityLogService robotActivityLogService;
    private final RobotSimulationProperties properties;
    private final RobotTickRunRepository tickRunRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    private final ExecutorService executionExecutor;
    private final AtomicReference<BackpressureState> backpressure = new AtomicReference<>(BackpressureState.initial());

    public RobotSimulationEngine(
        RobotPopulationSnapshotLoader snapshotLoader,
        RobotBrainService robotBrainService,
        RobotActionSupport robotActionSupport,
        RobotActivityLogService robotActivityLogService,
        RobotSimulationProperties properties,
        RobotTickRunRepository tickRunRepository,
        ObjectMapper objectMapper
    ) {
        this.snapshotLoader = snapshotLoader;
        this.robotBrainService = robotBrainService;
        this.robotActionSupport = robotActionSupport;
        this.robotActivityLogService = robotActivityLogService;
        this.properties = properties;
        this.tickRunRepository = tickRunRepository;
        this.objectMapper = objectMapper;
        this.executionExecutor = Executors.newFixedThreadPool(
            Math.max(1, properties.getExecutionParallelism()),
            namedFactory("robot-intent-exec-")
        );
    }

    public RobotTickRunResult runTick(String source, int multiplier) {
        Instant startedAt = Instant.now();
        long totalStarted = System.nanoTime();
        long decisionMs = 0;
        long executionMs = 0;
        int robotCount = 0;
        List<RobotIntent> planned = List.of();
        List<RobotIntent> selected = List.of();
        int failedCount = 0;
        Map<String, Long> executionErrors = Map.of();
        try {
            if (!snapshotLoader.hasHumanPlayer()) {
                RobotTickRunResult result = result(
                    source,
                    multiplier,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    elapsedMs(totalStarted),
                    false,
                    startedAt,
                    "{}"
                );
                tickRunRepository.record(result);
                return result;
            }

            List<RobotAgent> robots = snapshotLoader.loadRobots(robotLimit());
            robotCount = robots.size();

            long decisionStarted = System.nanoTime();
            PlanningResult planning = planIntents(robots);
            decisionMs = elapsedMs(decisionStarted);
            planned = planning.intents();
            failedCount += planning.failedCount();

            RobotActionBudgetPlan budget = RobotActionBudgetPlan.from(properties, multiplier, backpressure.get().budgetFactor());
            Selection selection = selectForExecution(planned, budget);
            selected = selection.selected();

            long executionStarted = System.nanoTime();
            ExecutionResult execution = execute(selected);
            failedCount += execution.failedCount();
            executionErrors = execution.errorTypes();
            executionMs = elapsedMs(executionStarted);
            robotActionSupport.trimChat();
            robotActivityLogService.trimRetention();

            long totalMs = elapsedMs(totalStarted);
            BackpressureState nextBackpressure = updateBackpressure(totalMs);
            RobotTickRunResult result = result(
                source,
                multiplier,
                robotCount,
                planned.size(),
                selected.size(),
                selection.deferredCount(),
                failedCount,
                decisionMs,
                executionMs,
                totalMs,
                false,
                startedAt,
                actionSummary(planned, selected, failedCount, budget, nextBackpressure, executionErrors)
            );
            tickRunRepository.record(result);
            return result;
        } catch (Exception error) {
            long totalMs = elapsedMs(totalStarted);
            BackpressureState nextBackpressure = updateBackpressure(totalMs);
            RobotTickRunResult result = result(
                source,
                multiplier,
                robotCount,
                planned.size(),
                selected.size(),
                Math.max(0, planned.size() - selected.size()),
                failedCount + 1,
                decisionMs,
                executionMs,
                totalMs,
                false,
                startedAt,
                errorSummary(error, nextBackpressure)
            );
            tickRunRepository.record(result);
            return result;
        }
    }

    public RobotTickRunResult recordSkipped(String source, int multiplier) {
        RobotTickRunResult result = RobotTickRunResult.skipped(source, multiplier);
        tickRunRepository.record(result);
        return result;
    }

    @PreDestroy
    public void shutdown() {
        executionExecutor.shutdownNow();
    }

    private PlanningResult planIntents(List<RobotAgent> robots) {
        if (robots.isEmpty()) {
            return new PlanningResult(List.of(), 0);
        }
        try (ExecutorService decisionExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<PlanOutcome>> futures = new ArrayList<>();
            for (int index = 0; index < robots.size(); index++) {
                RobotAgent actor = robots.get(index);
                RobotAgent target = targetFor(robots, index);
                futures.add(CompletableFuture.supplyAsync(() -> planLightweight(actor, target), decisionExecutor));
            }
            List<RobotIntent> intents = new ArrayList<>();
            int failed = 0;
            for (CompletableFuture<PlanOutcome> future : futures) {
                PlanOutcome outcome = future.join();
                if (outcome.failed()) {
                    failed++;
                }
                if (outcome.intent() == null) {
                    continue;
                } else {
                    intents.add(outcome.intent());
                }
            }
            return new PlanningResult(intents, failed);
        }
    }

    private PlanOutcome planLightweight(RobotAgent actor, RobotAgent target) {
        try {
            return new PlanOutcome(lightweightIntent(actor, target), false);
        } catch (Exception ignored) {
            return new PlanOutcome(RobotIntent.rest(actor, target, "规划动作失败，改为整理状态"), true);
        }
    }

    private RobotIntent lightweightIntent(RobotAgent actor, RobotAgent target) {
        RobotArchetype archetype = actor.archetype();
        double pve = 0.48 + archetype.pveBias() * 0.08;
        double market = pve + 0.12 + archetype.marketBias() * 0.06;
        double equipment = market + 0.16 + archetype.growthBias() * 0.05;
        double chat = equipment + 0.04 + archetype.socialBias() * 0.03;
        double roll = random.nextDouble();
        if (actor.player().level() < 20) {
            pve += 0.10;
            equipment += 0.04;
        }
        if (roll < Math.min(0.72, pve)) {
            return RobotIntent.lightweight(actor, target, "dungeon", RobotIntentCategory.COMBAT, "轻量规划：优先推进副本或扫荡");
        }
        if (roll < Math.min(0.88, market)) {
            return RobotIntent.lightweight(actor, target, "market_buy", RobotIntentCategory.MARKET, "轻量规划：关注商会供需");
        }
        if (roll < Math.min(0.96, equipment)) {
            return RobotIntent.lightweight(actor, target, "enhance", RobotIntentCategory.EQUIPMENT, "轻量规划：补强装备和构筑");
        }
        if (roll < Math.min(0.99, chat)) {
            return RobotIntent.lightweight(actor, target, "chat", RobotIntentCategory.WORLD_CHAT, "轻量规划：保持世界频道存在感");
        }
        return RobotIntent.lightweight(actor, target, "rest", RobotIntentCategory.LIGHT, "轻量规划：整理状态");
    }

    private Selection selectForExecution(List<RobotIntent> intents, RobotActionBudgetPlan budget) {
        List<RobotIntent> selected = new ArrayList<>();
        int deferred = 0;
        List<RobotIntent> ordered = intents.stream()
            .sorted(Comparator.comparing((RobotIntent intent) -> categoryOrder(intent.category()))
                .thenComparing(RobotIntent::robotId))
            .toList();
        for (RobotIntent intent : ordered) {
            if (budget.tryConsume(intent.category())) {
                selected.add(intent);
            } else {
                deferred++;
            }
        }
        return new Selection(selected, deferred);
    }

    private ExecutionResult execute(List<RobotIntent> intents) {
        if (intents.isEmpty()) {
            return new ExecutionResult(0, Map.of());
        }
        List<CompletableFuture<ExecutionOutcome>> futures = intents.stream()
            .map(intent -> CompletableFuture.supplyAsync(() -> executeOne(intent), executionExecutor))
            .toList();
        int failed = 0;
        Map<String, Long> errorTypes = new LinkedHashMap<>();
        for (CompletableFuture<ExecutionOutcome> future : futures) {
            ExecutionOutcome outcome = future.join();
            if (!outcome.success()) {
                failed++;
                String errorType = outcome.errorType() == null ? "Unknown" : outcome.errorType();
                errorTypes.put(errorType, errorTypes.getOrDefault(errorType, 0L) + 1);
            }
        }
        return new ExecutionResult(failed, errorTypes);
    }

    private ExecutionOutcome executeOne(RobotIntent intent) {
        try {
            if (intent.action() == null && intent.context() == null && !"rest".equals(intent.actionKey())) {
                robotBrainService.thinkAndAct(intent.actor(), intent.target());
            } else {
                robotBrainService.execute(intent);
            }
            return new ExecutionOutcome(true, null);
        } catch (ApiException ignored) {
            return new ExecutionOutcome(true, null);
        } catch (Exception error) {
            return new ExecutionOutcome(false, error.getClass().getSimpleName());
        }
    }

    private RobotAgent targetFor(List<RobotAgent> robots, int index) {
        if (robots.size() == 1) {
            return robots.getFirst();
        }
        int offset = 1 + random.nextInt(robots.size() - 1);
        return robots.get((index + offset) % robots.size());
    }

    private RobotTickRunResult result(
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
        Instant startedAt,
        String actionSummary
    ) {
        BackpressureState state = backpressure.get();
        return new RobotTickRunResult(
            source,
            multiplier,
            robotCount,
            plannedCount,
            executedCount,
            deferredCount,
            failedCount,
            decisionMs,
            executionMs,
            totalMs,
            skipped,
            state.backpressureActive(),
            state.budgetFactor(),
            Math.max(1, properties.getExecutionParallelism()),
            actionSummary,
            startedAt,
            Instant.now()
        );
    }

    private BackpressureState updateBackpressure(long totalMs) {
        return backpressure.updateAndGet(current -> {
            long budgetMs = Math.max(1, properties.getTickTimeBudgetMs());
            if (totalMs > budgetMs) {
                int slow = current.slowTicks() + 1;
                if (slow >= 3) {
                    double proportional = current.budgetFactor() * (budgetMs / (double) Math.max(1, totalMs)) * 0.95;
                    return new BackpressureState(Math.max(0.12, Math.min(current.budgetFactor() * 0.8, proportional)), slow, 0);
                }
                return new BackpressureState(current.budgetFactor(), slow, 0);
            }
            if (totalMs < Math.max(1, properties.getHealthyTickTimeMs())) {
                int healthy = current.healthyTicks() + 1;
                if (healthy >= 6) {
                    return new BackpressureState(Math.min(1.0, current.budgetFactor() + 0.1), 0, 0);
                }
                return new BackpressureState(current.budgetFactor(), 0, healthy);
            }
            return new BackpressureState(current.budgetFactor(), 0, 0);
        });
    }

    private String actionSummary(
        List<RobotIntent> planned,
        List<RobotIntent> executed,
        int failedCount,
        RobotActionBudgetPlan budget,
        BackpressureState backpressureState,
        Map<String, Long> executionErrors
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("planned", counts(planned));
        summary.put("executed", counts(executed));
        summary.put("failed", failedCount);
        summary.put("executionErrors", executionErrors);
        summary.put("budgets", budget.wireLimits());
        summary.put("budgetFactor", backpressureState.budgetFactor());
        summary.put("backpressureActive", backpressureState.backpressureActive());
        return toJson(summary);
    }

    private String errorSummary(Exception error, BackpressureState backpressureState) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("error", error.getClass().getSimpleName());
        summary.put("message", error.getMessage());
        summary.put("budgetFactor", backpressureState.budgetFactor());
        summary.put("backpressureActive", backpressureState.backpressureActive());
        return toJson(summary);
    }

    private Map<String, Long> counts(List<RobotIntent> intents) {
        return intents.stream()
            .collect(Collectors.groupingBy(intent -> intent.category().wireName(), LinkedHashMap::new, Collectors.counting()));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return "{}";
        }
    }

    private int robotLimit() {
        if (properties.isFullPopulationEnabled()) {
            return Math.max(1, properties.getMaxRobotsPerTick());
        }
        return Math.max(1, properties.getRobotsPerTick());
    }

    private static long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static int categoryOrder(RobotIntentCategory category) {
        return switch (category) {
            case COMBAT -> 0;
            case EQUIPMENT -> 1;
            case MARKET -> 2;
            case LIGHT -> 3;
            case WORLD_CHAT -> 4;
        };
    }

    private static ThreadFactory namedFactory(String prefix) {
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record PlanningResult(List<RobotIntent> intents, int failedCount) {
    }

    private record PlanOutcome(RobotIntent intent, boolean failed) {
    }

    private record Selection(List<RobotIntent> selected, int deferredCount) {
    }

    private record ExecutionOutcome(boolean success, String errorType) {
    }

    private record ExecutionResult(int failedCount, Map<String, Long> errorTypes) {
    }

    private record BackpressureState(double budgetFactor, int slowTicks, int healthyTicks) {
        private static BackpressureState initial() {
            return new BackpressureState(1.0, 0, 0);
        }

        private boolean backpressureActive() {
            return budgetFactor < 0.999;
        }
    }
}
