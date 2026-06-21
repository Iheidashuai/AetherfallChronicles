package com.mythicrealm.api.gameplay.robot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable knobs for the robot population and its activity cadence.
 *
 * <p>The seed population size lives in the SQL schema, but how lively that
 * population feels is governed here so you can dial ambience density from
 * {@code application.yml} without touching code. Defaults are sized for the
 * 400-robot seed and aim to keep each individual robot acting roughly every few
 * minutes (so nobody looks permanently idle) while the world stays busy.
 *
 * <pre>
 * mythicrealm:
 *   robot:
 *     full-population-enabled: true
 *     max-robots-per-tick: 400
 *     tick-time-budget-ms: 4500
 *     decision-virtual-concurrency: 400
 *     execution-parallelism: 16
 *     world-chat-retention: 400
 *     world-chat-cooldown-seconds: 6
 *     activity-log-retention: 4000
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "mythicrealm.robot")
public class RobotSimulationProperties {
    /** Whether the 5s tick should consider the whole robot population instead of a random sample. */
    private boolean fullPopulationEnabled = true;
    /** Maximum robots considered in one tick before fair rotation kicks in. */
    private int maxRobotsPerTick = 400;
    /** Soft time budget for a tick before adaptive backpressure starts lowering execution budgets. */
    private long tickTimeBudgetMs = 4_500;
    /** Threshold for considering a tick healthy enough to restore budget. */
    private long healthyTickTimeMs = 3_500;
    /** Number of virtual-thread decision tasks allowed per tick. */
    private int decisionVirtualConcurrency = 400;
    /** Fixed pool size for DB-writing intent execution. */
    private int executionParallelism = 16;
    /** How many robots are sampled (ORDER BY RAND()) each tick. */
    private int robotsPerTick = 48;
    /** Minimum number of sampled robots that actually act each tick (before time-of-day scaling). */
    private int minActionsPerTick = 28;
    /** Random extra actions on top of the minimum: actual = min + nextInt(range). */
    private int extraActionRange = 13;
    /** How many recent world-channel chat messages to keep. */
    private int worldChatRetention = 400;
    /** Minimum spacing between passive robot action messages in world chat. */
    private int worldChatCooldownSeconds = 6;
    /** How many recent robot activity-log rows to keep. */
    private int activityLogRetention = 4000;
    /**
     * Wall-clock interval for passive robot growth (ms). Runs regardless of whether a
     * human is online, so the world keeps progressing while the player is away.
     */
    private long passiveGrowthIntervalMs = 180_000;
    /**
     * How many levels per real hour each robot gains passively. Tuned so the field
     * keeps climbing and the top cohort stays a meaningful chase target — surpassable
     * with sustained effort, not "a few dungeons".
     */
    private double passiveLevelsPerHour = 0.5;
    private ActionBudgets actionBudgets = new ActionBudgets();

    public boolean isFullPopulationEnabled() {
        return fullPopulationEnabled;
    }

    public void setFullPopulationEnabled(boolean fullPopulationEnabled) {
        this.fullPopulationEnabled = fullPopulationEnabled;
    }

    public int getMaxRobotsPerTick() {
        return maxRobotsPerTick;
    }

    public void setMaxRobotsPerTick(int maxRobotsPerTick) {
        this.maxRobotsPerTick = maxRobotsPerTick;
    }

    public long getTickTimeBudgetMs() {
        return tickTimeBudgetMs;
    }

    public void setTickTimeBudgetMs(long tickTimeBudgetMs) {
        this.tickTimeBudgetMs = tickTimeBudgetMs;
    }

    public long getHealthyTickTimeMs() {
        return healthyTickTimeMs;
    }

    public void setHealthyTickTimeMs(long healthyTickTimeMs) {
        this.healthyTickTimeMs = healthyTickTimeMs;
    }

    public int getDecisionVirtualConcurrency() {
        return decisionVirtualConcurrency;
    }

    public void setDecisionVirtualConcurrency(int decisionVirtualConcurrency) {
        this.decisionVirtualConcurrency = decisionVirtualConcurrency;
    }

    public int getExecutionParallelism() {
        return executionParallelism;
    }

    public void setExecutionParallelism(int executionParallelism) {
        this.executionParallelism = executionParallelism;
    }

    public int getRobotsPerTick() {
        return robotsPerTick;
    }

    public void setRobotsPerTick(int robotsPerTick) {
        this.robotsPerTick = robotsPerTick;
    }

    public int getMinActionsPerTick() {
        return minActionsPerTick;
    }

    public void setMinActionsPerTick(int minActionsPerTick) {
        this.minActionsPerTick = minActionsPerTick;
    }

    public int getExtraActionRange() {
        return extraActionRange;
    }

    public void setExtraActionRange(int extraActionRange) {
        this.extraActionRange = extraActionRange;
    }

    public int getWorldChatRetention() {
        return worldChatRetention;
    }

    public void setWorldChatRetention(int worldChatRetention) {
        this.worldChatRetention = worldChatRetention;
    }

    public int getWorldChatCooldownSeconds() {
        return worldChatCooldownSeconds;
    }

    public void setWorldChatCooldownSeconds(int worldChatCooldownSeconds) {
        this.worldChatCooldownSeconds = worldChatCooldownSeconds;
    }

    public int getActivityLogRetention() {
        return activityLogRetention;
    }

    public void setActivityLogRetention(int activityLogRetention) {
        this.activityLogRetention = activityLogRetention;
    }

    public long getPassiveGrowthIntervalMs() {
        return passiveGrowthIntervalMs;
    }

    public void setPassiveGrowthIntervalMs(long passiveGrowthIntervalMs) {
        this.passiveGrowthIntervalMs = passiveGrowthIntervalMs;
    }

    public double getPassiveLevelsPerHour() {
        return passiveLevelsPerHour;
    }

    public void setPassiveLevelsPerHour(double passiveLevelsPerHour) {
        this.passiveLevelsPerHour = passiveLevelsPerHour;
    }

    public ActionBudgets getActionBudgets() {
        return actionBudgets;
    }

    public void setActionBudgets(ActionBudgets actionBudgets) {
        this.actionBudgets = actionBudgets == null ? new ActionBudgets() : actionBudgets;
    }

    public static class ActionBudgets {
        private int combatBase = 100;
        private int combatCap = 240;
        private int equipmentBase = 80;
        private int equipmentCap = 200;
        private int marketBase = 100;
        private int marketCap = 220;
        private int lightBase = 160;
        private int lightCap = 320;
        private int worldChatBase = 6;
        private int worldChatCap = 12;
        private int activityLogBase = 220;
        private int activityLogCap = 320;

        public int getCombatBase() {
            return combatBase;
        }

        public void setCombatBase(int combatBase) {
            this.combatBase = combatBase;
        }

        public int getCombatCap() {
            return combatCap;
        }

        public void setCombatCap(int combatCap) {
            this.combatCap = combatCap;
        }

        public int getEquipmentBase() {
            return equipmentBase;
        }

        public void setEquipmentBase(int equipmentBase) {
            this.equipmentBase = equipmentBase;
        }

        public int getEquipmentCap() {
            return equipmentCap;
        }

        public void setEquipmentCap(int equipmentCap) {
            this.equipmentCap = equipmentCap;
        }

        public int getMarketBase() {
            return marketBase;
        }

        public void setMarketBase(int marketBase) {
            this.marketBase = marketBase;
        }

        public int getMarketCap() {
            return marketCap;
        }

        public void setMarketCap(int marketCap) {
            this.marketCap = marketCap;
        }

        public int getLightBase() {
            return lightBase;
        }

        public void setLightBase(int lightBase) {
            this.lightBase = lightBase;
        }

        public int getLightCap() {
            return lightCap;
        }

        public void setLightCap(int lightCap) {
            this.lightCap = lightCap;
        }

        public int getWorldChatBase() {
            return worldChatBase;
        }

        public void setWorldChatBase(int worldChatBase) {
            this.worldChatBase = worldChatBase;
        }

        public int getWorldChatCap() {
            return worldChatCap;
        }

        public void setWorldChatCap(int worldChatCap) {
            this.worldChatCap = worldChatCap;
        }

        public int getActivityLogBase() {
            return activityLogBase;
        }

        public void setActivityLogBase(int activityLogBase) {
            this.activityLogBase = activityLogBase;
        }

        public int getActivityLogCap() {
            return activityLogCap;
        }

        public void setActivityLogCap(int activityLogCap) {
            this.activityLogCap = activityLogCap;
        }
    }
}
