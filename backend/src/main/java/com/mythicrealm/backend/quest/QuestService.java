package com.mythicrealm.backend.quest;

import com.mythicrealm.backend.auth.AuthenticatedAccount;
import com.mythicrealm.backend.common.ApiException;
import com.mythicrealm.backend.gameconfig.ConfigModels.QuestConfig;
import com.mythicrealm.backend.gameconfig.ConfigModels.QuestCondition;
import com.mythicrealm.backend.gameconfig.ConfigModels.QuestReward;
import com.mythicrealm.backend.gameconfig.GameConfigService;
import com.mythicrealm.backend.inventory.InventoryService;
import com.mythicrealm.backend.player.PlayerRecord;
import com.mythicrealm.backend.player.PlayerService;
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
            int current = currentValue(playerId, quest.id());
            int updated = applyEvent(current, quest, event);
            if (updated != current) {
                jdbcTemplate.update(
                    "UPDATE quest_progress SET current_value = ? WHERE player_id = ? AND quest_id = ?",
                    updated,
                    playerId,
                    quest.id()
                );
            }
        }
        updateCompletionAndUnlocks(playerId);
    }

    @Transactional
    public QuestClaimResult claim(AuthenticatedAccount account, String questId) {
        PlayerRecord player = playerService.requireByAccount(account);
        ensureProgress(player.id());
        QuestConfig quest = gameConfigService.quests().stream()
            .filter(config -> config.id().equals(questId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("任务不存在"));
        String status = statusOf(player.id(), questId);
        if (!"completed".equals(status)) {
            throw ApiException.badRequest("任务尚未完成");
        }

        int gold = 0;
        int exp = 0;
        int itemCount = 0;
        for (QuestReward reward : quest.rewards()) {
            switch (reward.type()) {
                case "gold" -> gold += reward.amount();
                case "experience" -> exp += reward.amount();
                case "itemTemplate" -> {
                    for (int i = 0; i < reward.amount(); i++) {
                        inventoryService.addRewardItem(player.id(), reward.targetId(), new Random((questId + i).hashCode()));
                        itemCount++;
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
        return new QuestClaimResult(questId, quest.title(), gold, exp, itemCount, updated);
    }

    private void ensureProgress(long playerId) {
        Map<String, String> statuses = jdbcTemplate.query(
            "SELECT quest_id, status FROM quest_progress WHERE player_id = ?",
            (rs, rowNum) -> Map.entry(rs.getString("quest_id"), rs.getString("status")),
            playerId
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (QuestConfig quest : gameConfigService.quests()) {
            if (statuses.containsKey(quest.id())) {
                continue;
            }
            String status = prerequisitesClaimed(playerId, quest) ? "active" : "locked";
            jdbcTemplate.update(
                "INSERT INTO quest_progress (player_id, quest_id, status, current_value) VALUES (?, ?, ?, 0)",
                playerId,
                quest.id(),
                status
            );
        }
        updateCompletionAndUnlocks(playerId);
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
            if ("active".equals(status) && currentValue(playerId, quest.id()) >= targetValue(quest)) {
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
            .sorted(Comparator.comparingInt(QuestConfig::priority))
            .map(config -> new QuestRow(
                config.id(),
                config.title(),
                config.category(),
                config.description(),
                config.lore(),
                config.navigationTarget(),
                statusOf(playerId, config.id()),
                currentValue(playerId, config.id()),
                targetValue(config),
                config.rewards()
            ))
            .toList();
    }

    private int applyEvent(int current, QuestConfig quest, QuestEvent event) {
        QuestCondition condition = quest.conditions().isEmpty() ? null : quest.conditions().get(0);
        if (condition == null) {
            return current;
        }
        if (!condition.type().equals(event.type())) {
            if ("dungeonClears".equals(condition.type()) && "dungeonCompleted".equals(event.type())) {
                return current + event.amount();
            }
            if ("monsterKills".equals(condition.type()) && "monsterKills".equals(event.type())) {
                return current + event.amount();
            }
            return current;
        }
        if (condition.targetId() != null && !condition.targetId().isBlank() && !condition.targetId().equals(event.targetId())) {
            return current;
        }
        return switch (condition.type()) {
            case "combatPowerReached" -> Math.max(current, event.amount());
            case "leaderboardRankReached" -> event.amount() <= condition.targetValue() ? condition.targetValue() : current;
            case "marketViewed", "leaderboardViewed", "chatOpened" -> condition.targetValue();
            default -> current + Math.max(1, event.amount());
        };
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

    private String statusOf(long playerId, String questId) {
        return jdbcTemplate.query(
            "SELECT status FROM quest_progress WHERE player_id = ? AND quest_id = ?",
            (rs, rowNum) -> rs.getString("status"),
            playerId,
            questId
        ).stream().findFirst().orElse("locked");
    }

    private int currentValue(long playerId, String questId) {
        return jdbcTemplate.query(
            "SELECT current_value FROM quest_progress WHERE player_id = ? AND quest_id = ?",
            (rs, rowNum) -> rs.getInt("current_value"),
            playerId,
            questId
        ).stream().findFirst().orElse(0);
    }

    private int targetValue(QuestConfig quest) {
        return quest.conditions().isEmpty() ? 1 : Math.max(1, quest.conditions().get(0).targetValue());
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
        List<QuestReward> rewards
    ) {
    }

    public record QuestClaimResult(String questId, String title, int gold, int experience, int itemCount, PlayerRecord player) {
    }
}
