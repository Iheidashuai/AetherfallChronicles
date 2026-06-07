package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.market.MarketService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RobotActivityService {
    private final JdbcTemplate jdbcTemplate;
    private final AnnouncementService announcementService;
    private final RobotEquipmentService robotEquipmentService;
    private final RobotActivityLogService robotActivityLogService;
    private final MarketService marketService;
    private final DungeonService dungeonService;
    private final GameConfigService gameConfigService;
    private final InventoryService inventoryService;
    private final Random random = new Random();

    public RobotActivityService(
        JdbcTemplate jdbcTemplate,
        AnnouncementService announcementService,
        RobotEquipmentService robotEquipmentService,
        RobotActivityLogService robotActivityLogService,
        MarketService marketService,
        DungeonService dungeonService,
        GameConfigService gameConfigService,
        InventoryService inventoryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.announcementService = announcementService;
        this.robotEquipmentService = robotEquipmentService;
        this.robotActivityLogService = robotActivityLogService;
        this.marketService = marketService;
        this.dungeonService = dungeonService;
        this.gameConfigService = gameConfigService;
        this.inventoryService = inventoryService;
    }

    @Scheduled(initialDelay = 6_000, fixedDelay = 30_000)
    public void simulateTick() {
        List<RobotProfile> robots = jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, strength, agility,
                   constitution, intelligence, spirit, free_points, personality, dungeon_clears,
                   peak_enhancement, legendary_loot_count
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY RAND()
            LIMIT 7
            """,
            (rs, rowNum) -> {
                PlayerRecord player = mapPlayer(rs);
                return new RobotProfile(
                    player,
                    rs.getString("title"),
                    rs.getString("personality"),
                    inventoryService.combatPower(player),
                    rs.getInt("dungeon_clears"),
                    rs.getInt("peak_enhancement"),
                    rs.getInt("legendary_loot_count")
                );
            }
        );
        if (robots.isEmpty()) {
            return;
        }

        int events = 1 + random.nextInt(2);
        for (int index = 0; index < Math.min(events, robots.size()); index++) {
            RobotProfile actor = robots.get(index);
            RobotProfile target = robots.get((index + 1 + random.nextInt(robots.size())) % robots.size());
            simulateAction(actor, target);
        }
        trimChat();
    }

    private void simulateAction(RobotProfile actor, RobotProfile target) {
        int roll = random.nextInt(1000);
        if (roll < 580) {
            dungeonRun(actor, target);
            return;
        }
        if (roll < 820) {
            enhancement(actor, target);
            return;
        }
        marketTalk(actor, target);
    }

    private void dungeonRun(RobotProfile actor, RobotProfile target) {
        DungeonConfig dungeon = chooseDungeon(actor.player(), actor.power());
        if (dungeon == null) {
            marketTalk(actor, target);
            return;
        }

        DungeonService.DungeonRunResult result;
        try {
            result = dungeonService.runDungeonForPlayer(actor.player(), dungeon.id(), null);
        } catch (ApiException error) {
            String text = "刚准备进副本，发现背包或状态需要整理一下。";
            chat(actor.player().id(), actor.player().name(), text);
            robotActivityLogService.record(actor.player().id(), "rest", text);
            return;
        }

        jdbcTemplate.update(
            """
            UPDATE player
            SET dungeon_clears = dungeon_clears + 1, last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            actor.player().id()
        );
        String text = switch (random.nextInt(4)) {
            case 0 -> "刚推完【" + result.dungeonName() + "】，" + target.player().name() + " 你那边掉率怎么样？";
            case 1 -> "这轮副本击败 " + result.monstersKilled() + " 只魔物，战力评估更新到 " + result.combatPower() + "。";
            case 2 -> "稳妥档真的省药，危险档收益高但心跳也高。";
            default -> "刷本队伍还缺输出吗？我刚清完【" + result.dungeonName() + "】。";
        };
        chat(actor.player().id(), actor.player().name(), text);
        robotActivityLogService.record(actor.player().id(), "dungeon", text);
        processLoot(actor, result);
    }

    private void processLoot(RobotProfile actor, DungeonService.DungeonRunResult result) {
        PlayerRecord updatedPlayer = result.player();
        for (ItemRecord item : result.loot()) {
            RobotEquipmentService.DropResolution drop = robotEquipmentService.resolveDropIfUpgrade(updatedPlayer, item, random);
            boolean legendary = "legendary".equals(item.quality());
            if (legendary) {
                jdbcTemplate.update(
                    "UPDATE player SET legendary_loot_count = legendary_loot_count + 1 WHERE id = ?",
                    updatedPlayer.id()
                );
            }
            if (drop.equipped()) {
                String text = "在【" + result.dungeonName() + "】打到【" + drop.change().itemName() + "】，换到" + drop.change().slotName() + "上，战力提升 " + drop.change().powerGain() + "。";
                robotActivityLogService.record(updatedPlayer.id(), "equip", text);
                if (legendary) {
                    announcementService.publish("loot", updatedPlayer.name(), updatedPlayer.name() + " 在【" + result.dungeonName() + "】获得并穿戴传说装备【" + drop.change().itemName() + "】。", 3);
                }
            } else {
                marketService.listRobotOwnedItem(updatedPlayer, actor.title(), item, "机器人副本掉落 · " + result.dungeonName(), random, true);
                if (legendary) {
                    announcementService.publish("loot", updatedPlayer.name(), updatedPlayer.name() + " 在【" + result.dungeonName() + "】获得传说装备【" + item.displayName() + "】，随后挂到商会寄售。", 2);
                }
            }
        }
    }

    private void enhancement(RobotProfile actor, RobotProfile target) {
        RobotEquipmentService.EquipmentChange change = robotEquipmentService.enhanceRandomEquipment(actor.player(), random);
        if (change == null) {
            String text = "攒金币中，等会儿再冲强化。";
            chat(actor.player().id(), actor.player().name(), text);
            robotActivityLogService.record(actor.player().id(), "rest", text);
            return;
        }
        jdbcTemplate.update(
            """
            UPDATE player
            SET peak_enhancement = GREATEST(peak_enhancement, ?), last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            change.enhancementLevel(),
            actor.player().id()
        );
        String text = change.success()
            ? "刚把【" + change.itemName() + "】强化到 +" + change.enhancementLevel() + "，" + target.player().name() + " 你别再劝我收手了。"
            : "强化【" + change.itemName() + "】失败了，现在 +" + change.enhancementLevel() + "，先缓一口气。";
        chat(actor.player().id(), actor.player().name(), text);
        robotActivityLogService.record(actor.player().id(), "enhance", text);
        if (change.success() && change.enhancementLevel() >= 9) {
            announcementService.publish("enhance", actor.player().name(), actor.player().name() + " 的【" + change.itemName() + "】强化突破 +" + change.enhancementLevel() + "，公会大厅一片惊呼。", 2);
        }
    }

    private void marketTalk(RobotProfile actor, RobotProfile target) {
        String text = switch (random.nextInt(4)) {
            case 0 -> target.player().name() + "，商会那件史诗戒指是不是你挂的？";
            case 1 -> "低价装备刚被秒了，世界频道盯商会的人真多。";
            case 2 -> "我清了普通装攒金币，晚上继续冲强化。";
            default -> "刚看战力榜，前排又有人换装了。";
        };
        chat(actor.player().id(), actor.player().name(), text);
        robotActivityLogService.record(actor.player().id(), "market_watch", text);
    }

    private DungeonConfig chooseDungeon(PlayerRecord player, int power) {
        List<DungeonConfig> candidates = gameConfigService.dungeons().stream()
            .filter(dungeon -> dungeon.recommendedLevel() <= Math.max(1, player.level() + 3))
            .filter(dungeon -> dungeon.recommendedPower() <= Math.max(1, (int) (power * 1.35)))
            .sorted(Comparator
                .comparingInt((DungeonConfig dungeon) -> Math.abs(dungeon.recommendedLevel() - Math.max(1, player.level())))
                .thenComparingInt(dungeon -> Math.abs(dungeon.recommendedPower() - power))
                .thenComparing(DungeonConfig::id))
            .limit(8)
            .toList();
        if (candidates.isEmpty()) {
            candidates = gameConfigService.dungeons().stream()
                .sorted(Comparator.comparingInt(DungeonConfig::recommendedLevel).thenComparing(DungeonConfig::id))
                .limit(3)
                .toList();
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private void chat(long playerId, String senderName, String text) {
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'robot', ?)",
            playerId,
            senderName,
            text
        );
    }

    private void trimChat() {
        jdbcTemplate.update(
            "DELETE FROM chat_message WHERE id NOT IN (SELECT id FROM (SELECT id FROM chat_message ORDER BY created_at DESC, id DESC LIMIT 180) recent)"
        );
    }

    private PlayerRecord mapPlayer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlayerRecord(
            rs.getLong("id"),
            rs.getObject("account_id") == null ? 0 : rs.getLong("account_id"),
            rs.getString("name"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getInt("experience"),
            rs.getInt("gold"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
    }

    private record RobotProfile(
        PlayerRecord player,
        String title,
        String personality,
        int power,
        int dungeonClears,
        int peakEnhancement,
        int legendaryLootCount
    ) {
    }
}
