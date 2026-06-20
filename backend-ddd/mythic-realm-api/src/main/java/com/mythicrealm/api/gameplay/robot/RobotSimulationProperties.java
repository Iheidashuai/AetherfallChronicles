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
 *     robots-per-tick: 48
 *     min-actions-per-tick: 28
 *     extra-action-range: 13
 *     world-chat-retention: 400
 *     activity-log-retention: 4000
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "mythicrealm.robot")
public class RobotSimulationProperties {
    /** How many robots are sampled (ORDER BY RAND()) each tick. */
    private int robotsPerTick = 48;
    /** Minimum number of sampled robots that actually act each tick (before time-of-day scaling). */
    private int minActionsPerTick = 28;
    /** Random extra actions on top of the minimum: actual = min + nextInt(range). */
    private int extraActionRange = 13;
    /** How many recent world-channel chat messages to keep. */
    private int worldChatRetention = 400;
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
}
