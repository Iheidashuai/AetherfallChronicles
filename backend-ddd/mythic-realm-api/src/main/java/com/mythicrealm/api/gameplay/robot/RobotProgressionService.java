package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.player.PlayerService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Passive, wall-clock robot progression.
 *
 * <p>The tick-based utility AI only grows a robot when a human is online AND the
 * sampler + softmax happen to pick a dungeon for it — roughly once every 10+ minutes
 * per robot, so an actively-grinding player blows past the whole field in one session.
 *
 * <p>This grants every robot a steady trickle of experience on a real-time schedule
 * that runs even with nobody online (the world keeps moving while the player is away),
 * reusing the exact same {@link PlayerService#applyRewards} level-up math so attributes,
 * gear quality and combat power all climb together. The field becomes an ever-moving
 * chase target: catching the top cohort takes sustained effort, not a few dungeons.
 */
@Service
public class RobotProgressionService {
    private static final int MAX_LEVEL = 90;

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final RobotSimulationProperties properties;
    private final RobotSpeedService robotSpeedService;

    public RobotProgressionService(
        JdbcTemplate jdbcTemplate,
        PlayerService playerService,
        RobotSimulationProperties properties,
        RobotSpeedService robotSpeedService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
        this.properties = properties;
        this.robotSpeedService = robotSpeedService;
    }

    @Scheduled(fixedDelayString = "${mythicrealm.robot.passive-growth-interval-ms:180000}", initialDelay = 20_000)
    public void grantPassiveGrowth() {
        double levelsPerHour = properties.getPassiveLevelsPerHour() * robotSpeedService.multiplier();
        if (levelsPerHour <= 0) {
            return;
        }
        double intervalHours = properties.getPassiveGrowthIntervalMs() / 3_600_000.0;
        double fraction = levelsPerHour * intervalHours; // fraction of a level granted per run

        List<Robot> robots = jdbcTemplate.query(
            "SELECT id, level FROM player WHERE controller_type = 'robot' AND level < ?",
            (rs, rowNum) -> new Robot(rs.getLong("id"), rs.getInt("level")),
            MAX_LEVEL
        );
        for (Robot robot : robots) {
            int required = PlayerService.experienceRequired(robot.level());
            if (required <= 0) {
                continue;
            }
            int exp = Math.max(1, (int) Math.round(required * fraction));
            int gold = Math.max(1, exp / 2);
            playerService.applyRewards(robot.id(), exp, gold);
        }
    }

    private record Robot(long id, int level) {
    }
}
