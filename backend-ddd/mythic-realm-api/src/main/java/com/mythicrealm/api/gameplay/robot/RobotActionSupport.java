package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.build.BuildService;
import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.guild.GuildBossService;
import com.mythicrealm.api.gameplay.guild.GuildService;
import com.mythicrealm.api.gameplay.endgame.EndgameRiftService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.inventory.EquipmentProcessingService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.market.MarketService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import com.mythicrealm.api.gameplay.skill.SkillService;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RobotActionSupport {
    private static final List<String> GUILD_BANTER = List.of(
        "公会里有人组队刷本吗？带带我。",
        "刚给公会出了点力，等公会 Boss 上线一起干。",
        "会长今天又上分了，咱们也别躺。",
        "公会频道安静了，冒个泡，大家加油。",
        "谁有多的强化石匀点，回头公会活动还你。",
        "咱们公会排名又稳了一点，继续保持。"
    );

    private final JdbcTemplate jdbcTemplate;
    private final RobotEquipmentService robotEquipmentService;
    private final RobotActivityLogService robotActivityLogService;
    private final MarketService marketService;
    private final DungeonService dungeonService;
    private final InventoryService inventoryService;
    private final QuestService questService;
    private final SkillService skillService;
    private final RechargeService rechargeService;
    private final EndgameRiftService endgameRiftService;
    private final BuildService buildService;
    private final EquipmentProcessingService equipmentProcessingService;
    private final GuildBossService guildBossService;
    private final GuildService guildService;

    public RobotActionSupport(
        JdbcTemplate jdbcTemplate,
        RobotEquipmentService robotEquipmentService,
        RobotActivityLogService robotActivityLogService,
        MarketService marketService,
        DungeonService dungeonService,
        InventoryService inventoryService,
        QuestService questService,
        SkillService skillService,
        RechargeService rechargeService,
        EndgameRiftService endgameRiftService,
        BuildService buildService,
        EquipmentProcessingService equipmentProcessingService,
        GuildBossService guildBossService,
        GuildService guildService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.robotEquipmentService = robotEquipmentService;
        this.robotActivityLogService = robotActivityLogService;
        this.marketService = marketService;
        this.dungeonService = dungeonService;
        this.inventoryService = inventoryService;
        this.questService = questService;
        this.skillService = skillService;
        this.rechargeService = rechargeService;
        this.endgameRiftService = endgameRiftService;
        this.buildService = buildService;
        this.equipmentProcessingService = equipmentProcessingService;
        this.guildBossService = guildBossService;
        this.guildService = guildService;
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
        questService.recordEvent(context.player().id(), QuestEvent.of("enhancementAttempts"));
        if (change.usedStoneCount() > 0) {
            questService.recordEvent(context.player().id(), new QuestEvent("enhancementStoneUsed", null, change.usedStoneCount()));
        }
        if (change.success()) {
            questService.recordEvent(context.player().id(), QuestEvent.of("enhancementSuccesses"));
            questService.recordEvent(context.player().id(), new QuestEvent("enhancementLevelReached", null, change.enhancementLevel()));
        }
        String rechargeText = change.recharged()
            ? "先花 " + change.rechargeRmb() + " 元换了 " + change.rechargeGold() + " 金，"
            : "";
        String text = change.success()
            ? rechargeText + "刚把【" + change.itemName() + "】强化到 +" + change.enhancementLevel() + "，" + context.target().name() + " 你别再劝我收手了。"
            : rechargeText + "强化【" + change.itemName() + "】失败了，现在 +" + change.enhancementLevel() + "，先缓一口气。";
        if (change.usedStoneCount() > 0) {
            text += " Used " + change.usedStoneCount() + " enhancement stones.";
        }
        chat(context.actor(), text);
        record(context.actor(), "enhance", text, score.reason());
        return RobotActionResult.success("enhance", text);
    }

    public RobotActionResult runRift(RobotDecisionContext context, RobotActionScore score) {
        int tier = Math.max(1, context.riftNextTier());
        EndgameRiftService.RiftRunResult result;
        try {
            result = endgameRiftService.run(context.player(), tier, null);
        } catch (ApiException error) {
            return rest(context.actor(), "深渊裂隙状态不理想，先回大厅调整装备。", score.reason());
        }
        String text = result.success()
            ? "刚打穿深渊 T" + result.tier() + "，评级 " + result.rating() + "，带回 " + result.rewards().essence() + " 个深渊精华。"
            : "深渊 T" + result.tier() + " 没打过，看来还得补一轮强化和淬炼。";
        chat(context.actor(), text);
        record(context.actor(), "rift", text, score.reason());
        return RobotActionResult.success("rift", text);
    }

    public RobotActionResult refineRiftEquipment(RobotDecisionContext context, RobotActionScore score) {
        List<ItemRecord> candidates = inventoryService.equippedItems(context.player().id()).values().stream()
            .filter(ItemRecord::equipment)
            .filter(item -> item.refineLevel() < 5)
            .sorted(Comparator
                .comparingInt(ItemRecord::refineLevel)
                .thenComparingInt(inventoryService::equipmentPower)
                .reversed())
            .toList();
        if (candidates.isEmpty()) {
            return rest(context.actor(), "深渊材料先存着，当前装备暂时没有适合淬炼的目标。", score.reason());
        }
        ItemRecord target = candidates.getFirst();
        String focus = switch (context.player().profession()) {
            case "mage" -> "mp";
            case "ranger" -> "crit";
            default -> "attack";
        };
        try {
            InventoryService.RefineResult result = inventoryService.refine(context.player(), target.id(), focus);
            questService.recordEvent(context.player().id(), QuestEvent.of("equipmentRefined"));
            questService.recordEvent(context.player().id(), new QuestEvent("combatPowerReached", null, result.inventory().combatPower()));
            String text = "用深渊材料把【" + result.item().displayName() + "】淬炼到 " + result.refineLevel() + " 阶，战力又往上拧了一圈。";
            chat(context.actor(), text);
            record(context.actor(), "rift_refine", text, score.reason());
            return RobotActionResult.success("rift_refine", text);
        } catch (ApiException error) {
            return rest(context.actor(), "深渊材料还差一点，先继续刷裂隙。", score.reason());
        }
    }

    public RobotActionResult processSockets(RobotDecisionContext context, RobotActionScore score) {
        try {
            EquipmentProcessingService.ProcessingSnapshot snapshot = equipmentProcessingService.snapshot(context.player());
            List<EquipmentProcessingService.ProcessingItemView> equippedTargets = snapshot.equipment().stream()
                .filter(entry -> inventoryService.equippedItems(context.player().id()).values().stream().anyMatch(item -> item.id() == entry.item().id()))
                .sorted(Comparator.comparingInt((EquipmentProcessingService.ProcessingItemView entry) -> inventoryService.equipmentPower(entry.item())).reversed())
                .toList();
            for (EquipmentProcessingService.ProcessingItemView target : equippedTargets) {
                var emptySocket = target.item().sockets().stream()
                    .filter(socket -> socket.unlocked() && socket.gemItemId() == null)
                    .findFirst();
                if (emptySocket.isPresent() && !snapshot.gems().isEmpty()) {
                    EquipmentProcessingService.ProcessingResult result = equipmentProcessingService.socketGem(
                        context.player(),
                        target.item().id(),
                        emptySocket.get().socketIndex(),
                        snapshot.gems().getFirst().id()
                    );
                    String text = "给【" + result.item().displayName() + "】镶上一颗宝石，战力 " + result.powerBefore() + " -> " + result.powerAfter() + "。";
                    chat(context.actor(), text);
                    record(context.actor(), "processing_socket", text, score.reason());
                    return RobotActionResult.success("processing_socket", text);
                }
                if (target.unlockedSocketCount() < target.socketLimit()) {
                    EquipmentProcessingService.ProcessingResult result = equipmentProcessingService.unlockSocket(context.player(), target.item().id());
                    String text = result.success()
                        ? "给【" + result.item().displayName() + "】开了新孔位，准备上宝石。"
                        : "给【" + target.item().displayName() + "】开孔失败，材料消耗了但装备没掉级。";
                    chat(context.actor(), text);
                    record(context.actor(), "processing_socket", text, score.reason());
                    return RobotActionResult.success("processing_socket", text);
                }
            }
            return rest(context.actor(), "宝石和孔位暂时对不上，先继续刷深渊材料。", score.reason());
        } catch (ApiException error) {
            return rest(context.actor(), "宝石加工条件还差一点，先不硬上。", score.reason());
        }
    }

    public RobotActionResult reforgeEquipment(RobotDecisionContext context, RobotActionScore score) {
        try {
            EquipmentProcessingService.ProcessingSnapshot snapshot = equipmentProcessingService.snapshot(context.player());
            EquipmentProcessingService.ProcessingItemView target = snapshot.equipment().stream()
                .filter(entry -> entry.affixLimit() > 0)
                .filter(entry -> inventoryService.equippedItems(context.player().id()).values().stream().anyMatch(item -> item.id() == entry.item().id()))
                .max(Comparator.comparingInt((EquipmentProcessingService.ProcessingItemView entry) -> inventoryService.equipmentPower(entry.item())))
                .orElse(null);
            if (target == null) {
                return rest(context.actor(), "当前装备还没到适合重铸词条的阶段。", score.reason());
            }
            List<Integer> locked = target.item().affixes().stream()
                .filter(affix -> affix.tier() >= 4)
                .map(ItemRecord.EquipmentAffixView::affixIndex)
                .limit(Math.max(0, context.affixLockCount()))
                .toList();
            EquipmentProcessingService.ProcessingResult result = equipmentProcessingService.reforge(context.player(), target.item().id(), locked);
            String text = result.success()
                ? "重铸【" + result.item().displayName() + "】词条成功，战力 " + result.powerBefore() + " -> " + result.powerAfter() + "。"
                : "重铸【" + target.item().displayName() + "】翻车了，未锁定词条降了一档。";
            chat(context.actor(), text);
            record(context.actor(), "processing_reforge", text, score.reason());
            return RobotActionResult.success("processing_reforge", text);
        } catch (ApiException error) {
            return rest(context.actor(), "重铸材料还不够稳定，先攒一轮。", score.reason());
        }
    }

    public RobotActionResult ascendEquipment(RobotDecisionContext context, RobotActionScore score) {
        try {
            EquipmentProcessingService.ProcessingSnapshot snapshot = equipmentProcessingService.snapshot(context.player());
            EquipmentProcessingService.ProcessingItemView target = snapshot.equipment().stream()
                .filter(entry -> entry.item().ascensionLevel() < 5)
                .filter(entry -> inventoryService.equippedItems(context.player().id()).values().stream().anyMatch(item -> item.id() == entry.item().id()))
                .max(Comparator.comparingInt((EquipmentProcessingService.ProcessingItemView entry) -> inventoryService.equipmentPower(entry.item()) + inventoryService.qualityRank(entry.item().quality()) * 120))
                .orElse(null);
            if (target == null) {
                return rest(context.actor(), "当前装备暂时没有升阶目标。", score.reason());
            }
            boolean useProtector = target.item().ascensionLevel() >= 3 && context.ascensionGuardCount() > 0;
            EquipmentProcessingService.ProcessingResult result = equipmentProcessingService.ascend(context.player(), target.item().id(), useProtector);
            String text = result.success()
                ? "把【" + result.item().displayName() + "】升阶成功，后期加工上限打开了。"
                : "升阶【" + target.item().displayName() + "】失败，" + (useProtector ? "护阶符保住了等级。" : "风险还是有点凶。");
            chat(context.actor(), text);
            record(context.actor(), "processing_ascend", text, score.reason());
            return RobotActionResult.success("processing_ascend", text);
        } catch (ApiException error) {
            return rest(context.actor(), "升阶材料还没攒齐，继续刷裂隙。", score.reason());
        }
    }

    public RobotActionResult exchangeGold(RobotDecisionContext context, RobotActionScore score) {
        long targetGold = context.tacticalGoldReserveTarget();
        var recharge = rechargeService.rechargeForGoldNeed(
            context.player().id(),
            targetGold,
            "robot_gold_reserve",
            score.reason()
        ).orElse(null);
        if (recharge == null) {
            return rest(context.actor(), "金币储备暂时够用，先观察下一步成长路线。", score.reason());
        }
        jdbcTemplate.update(
            "UPDATE player SET last_activity_at = CURRENT_TIMESTAMP WHERE id = ?",
            context.actor().id()
        );
        String text = "换了 " + recharge.rmbAmount() + " 元，补进 " + recharge.goldAmount() + " 金，先留作强化、技能和商会预算。";
        chat(context.actor(), text);
        record(context.actor(), "recharge", text, score.reason());
        return RobotActionResult.success("recharge", text);
    }

    public RobotActionResult trainSkill(RobotDecisionContext context, RobotActionScore score) {
        SkillService.TrainingOption planned = skillService.bestTrainingOption(context.player());
        RechargeService.RechargeResult recharge = null;
        PlayerRecord fundedPlayer = context.player();
        if (planned != null && !planned.affordable()) {
            recharge = rechargeService.rechargeForGoldNeed(
                context.player().id(),
                planned.cost(),
                "robot_skill_training",
                "修炼技能【" + planned.skillName() + "】"
            ).orElse(null);
            if (recharge != null) {
                fundedPlayer = recharge.player();
            }
        }
        SkillService.TrainingOption option = skillService.trainBestAffordable(fundedPlayer);
        if (option == null) {
            return rest(context.actor(), "技能导师那边暂时没有合适课程，先换个目标。", score.reason());
        }
        jdbcTemplate.update(
            "UPDATE player SET last_activity_at = CURRENT_TIMESTAMP WHERE id = ?",
            context.actor().id()
        );
        questService.recordEvent(context.player().id(), new QuestEvent("combatPowerReached", null, inventoryService.combatPower(context.player())));
        String actionText = option.learn() ? "学会" : "升级";
        String rankText = option.learn() ? "1 阶" : option.nextRank() + " 阶";
        String rechargeText = recharge == null ? "" : "先换 " + recharge.rmbAmount() + " 元成 " + recharge.goldAmount() + " 金，";
        String text = rechargeText + "刚花 " + option.cost() + " 金" + actionText + "技能【" + option.skillName() + "】到 " + rankText + "，下次刷本试试自动循环。";
        chat(context.actor(), text);
        record(context.actor(), "skill", text, score.reason());
        return RobotActionResult.success("skill", text);
    }

    public RobotActionResult configureBuild(RobotDecisionContext context, RobotActionScore score) {
        String presetId = context.recommendedBuildPresetId();
        if (presetId == null || presetId.isBlank()) {
            return rest(context.actor(), "暂时没有合适的流派预设，先按当前配置推进。", score.reason());
        }
        try {
            BuildService.BuildSnapshot snapshot = buildService.snapshot(context.player());
            BuildService.PlayerBuildView build = snapshot.builds().stream()
                .filter(candidate -> presetId.equals(candidate.sourcePresetId()))
                .findFirst()
                .orElse(null);
            if (build == null) {
                snapshot = buildService.copyPreset(context.player(), presetId);
                build = snapshot.builds().stream()
                    .filter(candidate -> presetId.equals(candidate.sourcePresetId()))
                    .findFirst()
                    .orElse(null);
            }
            if (build == null) {
                return rest(context.actor(), "流派档案还没准备好，先保留当前战斗计划。", score.reason());
            }
            BuildService.BuildActivationResult result = buildService.activate(context.player(), build.id());
            jdbcTemplate.update(
                "UPDATE player SET last_activity_at = CURRENT_TIMESTAMP WHERE id = ?",
                context.actor().id()
            );
            String warningText = result.warnings().isEmpty() ? "" : "，缺口：" + String.join("；", result.warnings());
            String text = "切换到构筑【" + result.buildName() + "】，装备 " + result.appliedEquipmentCount()
                + " 件，技能轮转 " + result.configuredSkillCount() + " 个，战力 "
                + result.beforePower() + " -> " + result.afterPower() + warningText + "。";
            chat(context.actor(), text);
            record(context.actor(), "build", text, score.reason());
            return RobotActionResult.success("build", text);
        } catch (ApiException error) {
            return rest(context.actor(), "构筑调整暂时失败，先继续原路线。", score.reason());
        }
    }

    public RobotActionResult claimQuestReward(RobotDecisionContext context, RobotActionScore score) {
        String questId = context.firstClaimableQuestId();
        if (questId == null || questId.isBlank()) {
            return rest(context.actor(), "任务板暂时没有可领取奖励，先换个目标。", score.reason());
        }
        QuestService.QuestClaimResult result;
        try {
            result = questService.claimForPlayer(context.player(), questId);
        } catch (ApiException error) {
            return rest(context.actor(), "任务奖励刚被刷新，先整理下一步路线。", score.reason());
        }
        int itemCount = result.itemCount();
        String text = "领取任务《" + result.title() + "》奖励：" + result.gold() + " 金 / "
            + result.experience() + " 经验" + (itemCount > 0 ? " / " + itemCount + " 件物品" : "") + "。";
        chat(context.actor(), text);
        record(context.actor(), "quest_claim", text, score.reason());
        return RobotActionResult.success("quest_claim", text);
    }

    public RobotActionResult useInventoryItemOrCraft(RobotDecisionContext context, RobotActionScore score) {
        if (context.stamina() != null && context.stamina().current() <= 30 && context.staminaPotionCount() > 0) {
            RobotActionResult result = useFirstItem(
                context,
                item -> "staminaPotion".equals(item.effectType()),
                Comparator.comparingInt(ItemRecord::sellPrice),
                "item_stamina",
                score
            );
            if (result.success()) {
                return result;
            }
        }

        if (context.immortalFragmentCount() >= 30) {
            RobotActionResult result = craftRecipe(context, "recipe_immortal_cache", score);
            if (result.success()) {
                return result;
            }
        }
        if (context.legendaryFragmentCount() >= 20) {
            RobotActionResult result = craftRecipe(context, "recipe_legendary_cache", score);
            if (result.success()) {
                return result;
            }
        }

        if (context.chestCount() > 0) {
            RobotActionResult result = useFirstItem(
                context,
                ItemRecord::chest,
                Comparator.comparingInt((ItemRecord item) -> qualityRank(item.quality())).reversed(),
                "item_chest",
                score
            );
            if (result.success()) {
                return result;
            }
        }

        if (context.attributePotionCount() > 0) {
            RobotActionResult result = useFirstItem(
                context,
                item -> "attributePotion".equals(item.effectType()),
                Comparator.comparingInt(ItemRecord::requiredLevel),
                "item_attribute",
                score
            );
            if (result.success()) {
                return result;
            }
        }

        if (context.stamina() != null && context.stamina().current() < 160 && context.staminaPotionCount() > 0) {
            return useFirstItem(
                context,
                item -> "staminaPotion".equals(item.effectType()),
                Comparator.comparingInt(ItemRecord::sellPrice),
                "item_stamina",
                score
            );
        }

        return rest(context.actor(), "背包里暂时没有适合现在使用的道具。", score.reason());
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

    public RobotActionResult guildDonate(RobotDecisionContext context, RobotActionScore score) {
        boolean donated = guildService.robotDonate(context.actor().id());
        if (!donated) {
            return rest(context.actor(), "金币还不够，先攒一攒再给公会捐献。", score.reason());
        }
        String text = "给公会捐了一笔金币，离公会升级更近一步。";
        record(context.actor(), "guild_donate", text, score.reason());
        return RobotActionResult.success("guild_donate", text);
    }

    public RobotActionResult guildBoss(RobotDecisionContext context, RobotActionScore score) {
        boolean hit = guildBossService.robotAttack(context.actor().id(), context.actor().power(), context.random());
        if (!hit) {
            return rest(context.actor(), "还没加入公会，先各刷各的。", score.reason());
        }
        String text = "给公会 Boss 输出了一轮，伤害已记入公会贡献。";
        record(context.actor(), "guild_boss", text, score.reason());
        return RobotActionResult.success("guild_boss", text);
    }

    public RobotActionResult guildChat(RobotDecisionContext context, RobotActionScore score) {
        Long guildId = jdbcTemplate.query(
            "SELECT guild_id FROM guild_member WHERE player_id = ?",
            rs -> rs.next() ? rs.getLong("guild_id") : null,
            context.actor().id()
        );
        if (guildId == null) {
            return rest(context.actor(), "还没加入公会，先各刷各的。", score.reason());
        }
        String text = GUILD_BANTER.get(context.random().nextInt(GUILD_BANTER.size()));
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text, channel) VALUES (?, ?, 'robot', ?, ?)",
            context.actor().id(),
            context.actor().name(),
            text,
            "guild:" + guildId
        );
        record(context.actor(), "guild_chat", text, score.reason());
        return RobotActionResult.success("guild_chat", text);
    }

    public void trimChat() {
        jdbcTemplate.update(
            "DELETE FROM chat_message WHERE channel = 'world' AND id NOT IN (SELECT id FROM (SELECT id FROM chat_message WHERE channel = 'world' ORDER BY created_at DESC, id DESC LIMIT 260) recent)"
        );
    }

    private void processLoot(RobotAgent actor, DungeonService.DungeonRunResult result, Random random) {
        PlayerRecord updatedPlayer = result.player();
        for (ItemRecord item : result.loot()) {
            if (!item.equipment()) {
                boolean legendary = "legendary".equals(item.quality()) || "immortal".equals(item.quality());
                if (legendary) {
                    jdbcTemplate.update(
                        "UPDATE player SET legendary_loot_count = legendary_loot_count + 1 WHERE id = ?",
                        updatedPlayer.id()
                    );
                }
                continue;
            }
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

    private RobotActionResult useFirstItem(
        RobotDecisionContext context,
        Predicate<ItemRecord> filter,
        Comparator<ItemRecord> comparator,
        String kind,
        RobotActionScore score
    ) {
        List<ItemRecord> candidates = inventoryService.inventoryItems(context.player().id()).stream()
            .filter(filter)
            .sorted(comparator)
            .toList();
        for (ItemRecord item : candidates) {
            try {
                InventoryService.UseItemResult result = inventoryService.useItem(context.player(), item.id());
                recordUseItemQuestEvents(context.player().id(), result);
                String extra = result.rewards().isEmpty()
                    ? ""
                    : "，获得 " + result.rewards().stream().map(ItemRecord::displayName).limit(2).toList();
                String text = "使用《" + result.itemName() + "》" + extra + "。";
                chat(context.actor(), text);
                record(context.actor(), kind, text, score.reason());
                return RobotActionResult.success(kind, text);
            } catch (ApiException ignored) {
                // Try the next candidate. Some low-tier potions have level caps.
            }
        }
        return RobotActionResult.failure(kind, "没有可用道具。");
    }

    private RobotActionResult craftRecipe(RobotDecisionContext context, String recipeId, RobotActionScore score) {
        try {
            InventoryService.CraftResult result = inventoryService.craftRecipe(context.player(), recipeId);
            questService.recordEvent(context.player().id(), new QuestEvent("fragmentCrafted", recipeId, 1));
            String text = "合成《" + result.recipeName() + "》，背包获得 "
                + result.rewards().stream().map(ItemRecord::displayName).limit(2).toList() + "。";
            chat(context.actor(), text);
            record(context.actor(), "item_craft", text, score.reason());
            return RobotActionResult.success("item_craft", text);
        } catch (ApiException error) {
            return RobotActionResult.failure("item_craft", "材料暂时不够。");
        }
    }

    private void recordUseItemQuestEvents(long playerId, InventoryService.UseItemResult result) {
        questService.recordEvent(playerId, new QuestEvent("itemUsed", result.effectType(), 1));
        if ("chest".equals(result.effectType())) {
            questService.recordEvent(playerId, QuestEvent.of("chestOpened"));
        }
        if ("staminaPotion".equals(result.effectType())) {
            questService.recordEvent(playerId, QuestEvent.of("staminaPotionUsed"));
        }
        questService.recordEvent(playerId, new QuestEvent("combatPowerReached", null, result.inventory().combatPower()));
    }

    private int qualityRank(String quality) {
        return switch (quality) {
            case "immortal" -> 6;
            case "legendary" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            default -> 1;
        };
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
