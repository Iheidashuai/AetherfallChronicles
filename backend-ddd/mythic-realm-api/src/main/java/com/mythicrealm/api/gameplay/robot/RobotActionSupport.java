package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.market.MarketService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RobotActionSupport {
    private final JdbcTemplate jdbcTemplate;
    private final RobotEquipmentService robotEquipmentService;
    private final RobotActivityLogService robotActivityLogService;
    private final MarketService marketService;
    private final DungeonService dungeonService;
    private final InventoryService inventoryService;

    public RobotActionSupport(
        JdbcTemplate jdbcTemplate,
        RobotEquipmentService robotEquipmentService,
        RobotActivityLogService robotActivityLogService,
        MarketService marketService,
        DungeonService dungeonService,
        InventoryService inventoryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.robotEquipmentService = robotEquipmentService;
        this.robotActivityLogService = robotActivityLogService;
        this.marketService = marketService;
        this.dungeonService = dungeonService;
        this.inventoryService = inventoryService;
    }

    public RobotActionResult runDungeon(RobotDecisionContext context, RobotActionScore score) {
        DungeonConfig dungeon = context.runnableDungeon();
        if (dungeon == null) {
            return rest(context.actor(), "暂时没有合适的副本，改为整理装备。", score.reason());
        }

        DungeonService.DungeonRunResult result;
        try {
            result = dungeonService.runDungeonForPlayer(context.player(), dungeon.id(), null);
        } catch (ApiException error) {
            String text = "刚准备进副本，发现背包或状态需要整理一下。";
            chat(context.actor(), text);
            record(context.actor(), "rest", text, score.reason());
            return RobotActionResult.failure("rest", text);
        }

        jdbcTemplate.update(
            """
            UPDATE player
            SET dungeon_clears = dungeon_clears + 1, last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            context.actor().id()
        );

        String text = switch (context.random().nextInt(4)) {
            case 0 -> "刚推完【" + result.dungeonName() + "】，" + context.target().name() + " 你那边掉率怎么样？";
            case 1 -> "这轮副本击败 " + result.monstersKilled() + " 只魔物，战力评估更新到 " + result.combatPower() + "。";
            case 2 -> "稳妥档真的省药，危险档收益高但心跳也高。";
            default -> "刷本队伍还缺输出吗？我刚清完【" + result.dungeonName() + "】。";
        };
        chat(context.actor(), text);
        record(context.actor(), "dungeon", text, score.reason());
        processLoot(context.actor(), result, context.random());
        return RobotActionResult.success("dungeon", text);
    }

    public RobotActionResult enhanceEquipment(RobotDecisionContext context, RobotActionScore score) {
        RobotEquipmentService.EquipmentChange change = robotEquipmentService.enhancePlannedEquipment(context.player(), context.random());
        if (change == null) {
            String text = "攒金币中，等会儿再冲强化。";
            chat(context.actor(), text);
            record(context.actor(), "rest", text, score.reason());
            return RobotActionResult.failure("rest", text);
        }
        jdbcTemplate.update(
            """
            UPDATE player
            SET peak_enhancement = GREATEST(peak_enhancement, ?), last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            change.enhancementLevel(),
            context.actor().id()
        );
        String rechargeText = change.recharged()
            ? "先花 " + change.rechargeRmb() + " 元换了 " + change.rechargeGold() + " 金，"
            : "";
        String text = change.success()
            ? rechargeText + "刚把【" + change.itemName() + "】强化到 +" + change.enhancementLevel() + "，" + context.target().name() + " 你别再劝我收手了。"
            : rechargeText + "强化【" + change.itemName() + "】失败了，现在 +" + change.enhancementLevel() + "，先缓一口气。";
        chat(context.actor(), text);
        record(context.actor(), "enhance", text, score.reason());
        return RobotActionResult.success("enhance", text);
    }

    public RobotActionResult buyFromMarket(RobotDecisionContext context, RobotActionScore score) {
        boolean bought = marketService.tryRobotBuyListing(context.actor().id(), score.reason());
        if (!bought) {
            String text = "看了一圈商会，没有找到价格和词条都合适的装备。";
            record(context.actor(), "market_watch", text, score.reason());
            return RobotActionResult.failure("market_watch", text);
        }
        return RobotActionResult.success("market_buy", "已按当前目标完成一次商会采购。");
    }

    public RobotActionResult supplyMarket(RobotDecisionContext context, RobotActionScore score) {
        MarketService.RobotLootOutcome outcome = marketService.resolveRobotDungeonLoot(
            context.actor().id(),
            context.actor().name(),
            context.actor().title(),
            context.player().profession(),
            context.player().level(),
            context.actor().power(),
            context.player().gold(),
            context.random()
        );
        if (outcome.listedCount() > 0 || outcome.equippedCount() > 0) {
            return RobotActionResult.success("market_list", "完成一次刷本补货。");
        }
        String text = "想给商会补点货，但这轮没有合适的掉落。";
        record(context.actor(), "market_watch", text, score.reason());
        return RobotActionResult.failure("market_watch", text);
    }

    public RobotActionResult socialChat(RobotDecisionContext context, RobotActionScore score) {
        String text;
        int powerGap = context.powerGapToProgression();
        if (powerGap > 0 && context.enhancementOpportunity() != null) {
            text = "下一层副本还差 " + powerGap + " 战力，我准备先补 " + context.enhancementOpportunity().slotName() + "。";
        } else if (context.marketOpportunities() > 0) {
            text = "商会有几件装备价格还行，先看词条再决定要不要出手。";
        } else if (context.personalityContains("强化")) {
            text = "强化这事要看节奏，金币够的时候再冲一手。";
        } else if (context.personalityContains("商会")) {
            text = "今天商会流动挺快，低价稀有装基本挂不住。";
        } else {
            text = switch (context.random().nextInt(4)) {
                case 0 -> context.target().name() + "，你现在主刷哪个副本？";
                case 1 -> "我在比较装备词条，品质高但属性歪也不一定值。";
                case 2 -> "刚看战力榜，前排又有人换装了。";
                default -> "先刷能稳定通关的本，掉落和经验都不会太亏。";
            };
        }
        chat(context.actor(), text);
        record(context.actor(), "chat", text, score.reason());
        return RobotActionResult.success("chat", text);
    }

    public RobotActionResult rest(RobotAgent actor, String text, String reason) {
        record(actor, "rest", text, reason);
        return RobotActionResult.success("rest", text);
    }

    public void trimChat() {
        jdbcTemplate.update(
            "DELETE FROM chat_message WHERE id NOT IN (SELECT id FROM (SELECT id FROM chat_message ORDER BY created_at DESC, id DESC LIMIT 260) recent)"
        );
    }

    private void processLoot(RobotAgent actor, DungeonService.DungeonRunResult result, Random random) {
        PlayerRecord updatedPlayer = result.player();
        for (ItemRecord item : result.loot()) {
            RobotEquipmentService.DropResolution drop = robotEquipmentService.resolveDropIfUpgrade(updatedPlayer, item, random);
            boolean legendary = "legendary".equals(item.quality()) || "immortal".equals(item.quality());
            if (legendary) {
                jdbcTemplate.update(
                    "UPDATE player SET legendary_loot_count = legendary_loot_count + 1 WHERE id = ?",
                    updatedPlayer.id()
                );
            }
            if (drop.equipped()) {
                String text = "在【" + result.dungeonName() + "】打到【" + drop.change().itemName() + "】，换到" + drop.change().slotName() + "上，战力提升 " + drop.change().powerGain() + "。";
                robotActivityLogService.record(updatedPlayer.id(), "equip", text);
            } else {
                marketService.listRobotOwnedItem(updatedPlayer, actor.title(), item, "机器人副本掉落 · " + result.dungeonName(), random, true);
            }
        }
    }

    private void chat(RobotAgent actor, String text) {
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'robot', ?)",
            actor.id(),
            actor.name(),
            text
        );
    }

    private void record(RobotAgent actor, String kind, String text, String reason) {
        robotActivityLogService.record(actor.id(), kind, withReason(text, reason));
    }

    private String withReason(String text, String reason) {
        if (reason == null || reason.isBlank()) {
            return text;
        }
        return text + "（决策：" + reason + "）";
    }
}
