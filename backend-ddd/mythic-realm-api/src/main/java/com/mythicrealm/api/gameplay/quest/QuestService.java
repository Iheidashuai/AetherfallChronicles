package com.mythicrealm.api.gameplay.quest;

import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.QuestConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.QuestCondition;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.QuestReward;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestService {
    private static final ZoneId GAME_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final PlayerService playerService;
    private final InventoryService inventoryService;

    public QuestService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        PlayerService playerService,
        @Lazy InventoryService inventoryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public List<QuestRow> rows(AuthenticatedAccount account) {
        PlayerRecord player = playerService.requireByAccount(account);
        ensureProgress(player.id());
        return rowsForPlayer(player.id());
    }

    @Transactional
    public void recordEvent(long playerId, QuestEvent event) {
        ensureProgress(playerId);
        for (QuestConfig quest : gameConfigService.quests()) {
            String status = statusOf(playerId, quest.id());
            if (!"active".equals(status)) {
                continue;
            }
            String periodKey = periodKey(quest);
            for (QuestCondition condition : quest.conditions()) {
                ConditionState state = conditionState(playerId, quest.id(), condition.id());
                int updated = applyEvent(state.currentValue(), condition, event);
                boolean completed = updated >= targetValue(condition);
                if (updated != state.currentValue() || completed != state.completed()) {
                    jdbcTemplate.update(
                        """
                        UPDATE quest_condition_progress
                        SET current_value = ?, completed = ?, period_key = ?
                        WHERE player_id = ? AND quest_id = ? AND condition_id = ?
                        """,
                        updated,
                        completed,
                        periodKey,
                        playerId,
                        quest.id(),
                        condition.id()
                    );
                }
            }
            syncQuestCurrentValue(playerId, quest);
        }
        updateCompletionAndUnlocks(playerId);
    }

    @Transactional
    public QuestClaimResult claim(AuthenticatedAccount account, String questId) {
        PlayerRecord player = playerService.requireByAccount(account);
        return claimForPlayer(player, questId);
    }

    @Transactional
    public QuestClaimResult claimForPlayer(PlayerRecord player, String questId) {
        ensureProgress(player.id());
        QuestConfig quest = gameConfigService.quests().stream()
            .filter(config -> config.id().equals(questId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Quest not found"));
        String status = statusOf(player.id(), questId);
        if (!"completed".equals(status)) {
            throw ApiException.badRequest("Quest is not completed");
        }

        int gold = 0;
        int exp = 0;
        int itemCount = 0;
        var grants = new java.util.ArrayList<RewardGrant>();
        for (QuestReward reward : quest.rewards()) {
            switch (reward.type()) {
                case "gold" -> {
                    gold += reward.amount();
                    grants.add(new RewardGrant("gold", null, reward.amount(), null, null, null));
                }
                case "experience" -> {
                    exp += reward.amount();
                    grants.add(new RewardGrant("experience", null, reward.amount(), null, null, null));
                }
                case "itemTemplate" -> {
                    var items = inventoryService.grantItem(player.id(), reward.targetId(), reward.amount(), new Random((questId + reward.targetId()).hashCode()));
                    itemCount += items.stream().mapToInt(item -> Math.max(1, item.quantity())).sum();
                    if (!items.isEmpty()) {
                        var item = items.getFirst();
                        grants.add(new RewardGrant("itemTemplate", reward.targetId(), reward.amount(), item.name(), item.quality(), item.itemCategory()));
                    }
                }
                default -> {
                }
            }
        }
        PlayerRecord updated = playerService.applyRewards(player.id(), exp, gold);
        jdbcTemplate.update(
            "UPDATE quest_progress SET status = 'claimed', claimed_at = CURRENT_TIMESTAMP WHERE player_id = ? AND quest_id = ?",
            player.id(),
            questId
        );
        updateCompletionAndUnlocks(player.id());
        return new QuestClaimResult(questId, quest.title(), gold, exp, itemCount, grants, updated);
    }

    public int claimableCount(long playerId) {
        ensureProgress(playerId);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quest_progress WHERE player_id = ? AND status = 'completed'",
            Integer.class,
            playerId
        );
        return count == null ? 0 : count;
    }

    public String firstClaimableQuestId(long playerId) {
        ensureProgress(playerId);
        return jdbcTemplate.query(
            """
            SELECT qp.quest_id
            FROM quest_progress qp
            JOIN quest_config qc ON qc.id = qp.quest_id
            WHERE qp.player_id = ? AND qp.status = 'completed'
            ORDER BY qc.priority, qp.quest_id
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("quest_id"),
            playerId
        ).stream().findFirst().orElse(null);
    }

    private void ensureProgress(long playerId) {
        Map<String, ProgressState> states = jdbcTemplate.query(
            "SELECT quest_id, status, period_key FROM quest_progress WHERE player_id = ?",
            (rs, rowNum) -> Map.entry(
                rs.getString("quest_id"),
                new ProgressState(rs.getString("status"), rs.getString("period_key"))
            ),
            playerId
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (QuestConfig quest : gameConfigService.quests()) {
            String periodKey = periodKey(quest);
            ProgressState state = states.get(quest.id());
            if (state == null) {
                String status = prerequisitesClaimed(playerId, quest) ? "active" : "locked";
                jdbcTemplate.update(
                    "INSERT INTO quest_progress (player_id, quest_id, status, current_value, period_key) VALUES (?, ?, ?, 0, ?)",
                    playerId,
                    quest.id(),
                    status,
                    periodKey
                );
            } else if (isResettable(quest) && !periodKey.equals(state.periodKey())) {
                String status = prerequisitesClaimed(playerId, quest) ? "active" : "locked";
                jdbcTemplate.update(
                    "UPDATE quest_progress SET status = ?, current_value = 0, period_key = ?, claimed_at = NULL WHERE player_id = ? AND quest_id = ?",
                    status,
                    periodKey,
                    playerId,
                    quest.id()
                );
                jdbcTemplate.update("DELETE FROM quest_condition_progress WHERE player_id = ? AND quest_id = ?", playerId, quest.id());
            }
            ensureConditionProgress(playerId, quest, periodKey);
            syncQuestCurrentValue(playerId, quest);
        }
        updateCompletionAndUnlocks(playerId);
    }

    private void ensureConditionProgress(long playerId, QuestConfig quest, String periodKey) {
        for (QuestCondition condition : quest.conditions()) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_condition_progress WHERE player_id = ? AND quest_id = ? AND condition_id = ?",
                Integer.class,
                playerId,
                quest.id(),
                condition.id()
            );
            if (count == null || count == 0) {
                jdbcTemplate.update(
                    """
                    INSERT INTO quest_condition_progress
                    (player_id, quest_id, condition_id, current_value, completed, period_key)
                    VALUES (?, ?, ?, 0, FALSE, ?)
                    """,
                    playerId,
                    quest.id(),
                    condition.id(),
                    periodKey
                );
            }
        }
    }

    private void updateCompletionAndUnlocks(long playerId) {
        for (QuestConfig quest : gameConfigService.quests()) {
            String status = statusOf(playerId, quest.id());
            if ("locked".equals(status) && prerequisitesClaimed(playerId, quest)) {
                jdbcTemplate.update(
                    "UPDATE quest_progress SET status = 'active' WHERE player_id = ? AND quest_id = ?",
                    playerId,
                    quest.id()
                );
                status = "active";
            }
            if ("active".equals(status) && questComplete(playerId, quest)) {
                jdbcTemplate.update(
                    "UPDATE quest_progress SET status = 'completed' WHERE player_id = ? AND quest_id = ?",
                    playerId,
                    quest.id()
                );
            }
        }
    }

    private List<QuestRow> rowsForPlayer(long playerId) {
        return gameConfigService.quests().stream()
            .sorted(Comparator
                .comparingInt((QuestConfig config) -> statusSort(statusOf(playerId, config.id())))
                .thenComparingInt(QuestConfig::priority))
            .map(config -> toRow(playerId, config))
            .toList();
    }

    private QuestRow toRow(long playerId, QuestConfig config) {
        List<QuestConditionRow> conditions = config.conditions().stream()
            .map(condition -> {
                ConditionState state = conditionState(playerId, config.id(), condition.id());
                int target = targetValue(condition);
                return new QuestConditionRow(
                    condition.id(),
                    condition.type(),
                    condition.targetId(),
                    state.currentValue(),
                    target,
                    state.currentValue() >= target
                );
            })
            .toList();
        int current = conditions.stream().mapToInt(QuestConditionRow::currentValue).sum();
        int target = Math.max(1, conditions.stream().mapToInt(QuestConditionRow::targetValue).sum());
        int percent = Math.min(100, (int) Math.round(current * 100.0 / target));
        String status = statusOf(playerId, config.id());
        boolean claimable = "completed".equals(status);
        List<QuestRewardRow> rewards = config.rewards().stream().map(this::toRewardRow).toList();
        return new QuestRow(
            config.id(),
            config.title(),
            config.category(),
            config.description(),
            config.lore(),
            config.navigationTarget(),
            status,
            current,
            target,
            config.conditionLogic(),
            config.resetPeriod(),
            claimable,
            percent,
            resetAt(config),
            recommended(config, status, percent),
            conditions,
            rewards
        );
    }

    private QuestRewardRow toRewardRow(QuestReward reward) {
        if ("itemTemplate".equals(reward.type()) && reward.targetId() != null) {
            var item = gameConfigService.requireItem(reward.targetId());
            return new QuestRewardRow(reward.type(), reward.targetId(), reward.amount(), item.name(), item.quality(), item.category());
        }
        return new QuestRewardRow(reward.type(), reward.targetId(), reward.amount(), null, null, null);
    }

    private int applyEvent(int current, QuestCondition condition, QuestEvent event) {
        if (!condition.type().equals(event.type())) {
            if ("dungeonClears".equals(condition.type()) && "dungeonCompleted".equals(event.type())) {
                return current + event.amount();
            }
            return current;
        }
        if (!targetMatches(condition, event)) {
            return current;
        }
        return switch (condition.type()) {
            case "combatPowerReached", "enhancementLevelReached" -> Math.max(current, event.amount());
            case "leaderboardRankReached" -> event.amount() <= condition.targetValue() ? condition.targetValue() : current;
            case "marketViewed", "leaderboardViewed", "chatOpened" -> condition.targetValue();
            case "dungeonRatingReached" -> condition.targetValue();
            default -> current + Math.max(1, event.amount());
        };
    }

    private boolean targetMatches(QuestCondition condition, QuestEvent event) {
        if (condition.targetId() == null || condition.targetId().isBlank()) {
            return true;
        }
        if ("itemQualityObtained".equals(condition.type())) {
            return qualityRank(event.targetId()) >= qualityRank(condition.targetId());
        }
        return condition.targetId().equals(event.targetId());
    }

    private boolean questComplete(long playerId, QuestConfig quest) {
        if (quest.conditions().isEmpty()) {
            return true;
        }
        List<Boolean> completed = quest.conditions().stream()
            .map(condition -> conditionState(playerId, quest.id(), condition.id()).currentValue() >= targetValue(condition))
            .toList();
        if ("ANY".equalsIgnoreCase(quest.conditionLogic())) {
            return completed.stream().anyMatch(Boolean::booleanValue);
        }
        return completed.stream().allMatch(Boolean::booleanValue);
    }

    private boolean prerequisitesClaimed(long playerId, QuestConfig quest) {
        if (quest.prerequisiteIds() == null || quest.prerequisiteIds().isEmpty()) {
            return true;
        }
        for (String prerequisite : quest.prerequisiteIds()) {
            if (!"claimed".equals(statusOf(playerId, prerequisite))) {
                return false;
            }
        }
        return true;
    }

    private void syncQuestCurrentValue(long playerId, QuestConfig quest) {
        int current = quest.conditions().stream()
            .mapToInt(condition -> conditionState(playerId, quest.id(), condition.id()).currentValue())
            .sum();
        jdbcTemplate.update(
            "UPDATE quest_progress SET current_value = ? WHERE player_id = ? AND quest_id = ?",
            current,
            playerId,
            quest.id()
        );
    }

    private String statusOf(long playerId, String questId) {
        return jdbcTemplate.query(
            "SELECT status FROM quest_progress WHERE player_id = ? AND quest_id = ?",
            (rs, rowNum) -> rs.getString("status"),
            playerId,
            questId
        ).stream().findFirst().orElse("locked");
    }

    private ConditionState conditionState(long playerId, String questId, String conditionId) {
        return jdbcTemplate.query(
            "SELECT current_value, completed FROM quest_condition_progress WHERE player_id = ? AND quest_id = ? AND condition_id = ?",
            (rs, rowNum) -> new ConditionState(rs.getInt("current_value"), rs.getBoolean("completed")),
            playerId,
            questId,
            conditionId
        ).stream().findFirst().orElse(new ConditionState(0, false));
    }

    private int targetValue(QuestCondition condition) {
        return Math.max(1, condition.targetValue());
    }

    private String periodKey(QuestConfig quest) {
        LocalDate today = LocalDate.now(GAME_ZONE);
        return switch (quest.resetPeriod()) {
            case "daily" -> today.toString();
            case "weekly" -> {
                WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 4);
                yield today.get(weekFields.weekBasedYear()) + "-W" + today.get(weekFields.weekOfWeekBasedYear());
            }
            default -> "lifetime";
        };
    }

    private boolean isResettable(QuestConfig quest) {
        return !"lifetime".equals(quest.resetPeriod());
    }

    private String resetAt(QuestConfig quest) {
        if ("daily".equals(quest.resetPeriod())) {
            return LocalDate.now(GAME_ZONE).plusDays(1).atStartOfDay(GAME_ZONE).toInstant().toString();
        }
        if ("weekly".equals(quest.resetPeriod())) {
            return LocalDate.now(GAME_ZONE).with(DayOfWeek.MONDAY).plusWeeks(1).atStartOfDay(GAME_ZONE).toInstant().toString();
        }
        return null;
    }

    private boolean recommended(QuestConfig config, String status, int percent) {
        return "completed".equals(status)
            || ("active".equals(status) && (percent >= 70 || "main".equals(config.category())));
    }

    private int statusSort(String status) {
        return switch (status) {
            case "completed" -> 0;
            case "active" -> 1;
            case "locked" -> 2;
            default -> 3;
        };
    }

    private int qualityRank(String quality) {
        return switch (quality == null ? "" : quality) {
            case "immortal" -> 6;
            case "legendary" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            default -> 1;
        };
    }

    public record QuestEvent(String type, String targetId, int amount) {
        public static QuestEvent of(String type) {
            return new QuestEvent(type, null, 1);
        }
    }

    public record QuestRow(
        String id,
        String title,
        String category,
        String description,
        String lore,
        String navigationTarget,
        String status,
        int currentValue,
        int targetValue,
        String conditionLogic,
        String resetPeriod,
        boolean claimable,
        int progressPercent,
        String resetAt,
        boolean recommended,
        List<QuestConditionRow> conditions,
        List<QuestRewardRow> rewards
    ) {
    }

    public record QuestConditionRow(
        String id,
        String type,
        String targetId,
        int currentValue,
        int targetValue,
        boolean completed
    ) {
    }

    public record QuestRewardRow(
        String type,
        String targetId,
        int amount,
        String itemName,
        String quality,
        String itemCategory
    ) {
    }

    public record QuestClaimResult(
        String questId,
        String title,
        int gold,
        int experience,
        int itemCount,
        List<RewardGrant> rewards,
        PlayerRecord player
    ) {
    }

    public record RewardGrant(
        String type,
        String targetId,
        int amount,
        String itemName,
        String quality,
        String itemCategory
    ) {
    }

    private record ProgressState(String status, String periodKey) {
    }

    private record ConditionState(int currentValue, boolean completed) {
    }
}
