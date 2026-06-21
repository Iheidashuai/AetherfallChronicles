package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.build.BuildService;
import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.dungeon.SweepTicketPolicy;
import com.mythicrealm.api.gameplay.guild.GuildBossService;
import com.mythicrealm.api.gameplay.guild.GuildService;
import com.mythicrealm.api.gameplay.endgame.EndgameRiftService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.inventory.EquipmentProcessingService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemEffectEvent;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.market.MarketService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import com.mythicrealm.api.gameplay.skill.SkillService;
import java.sql.Timestamp;
import java.util.ArrayList;
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
        "咱们公会排名又稳了一点，继续保持。",
        "今晚有没有人冲公会 Boss？凑波伤害冲榜。",
        "新人有问题尽管问，公会老人都挺热心的。",
        "捐献别落下，公会升级了大家都有增益。",
        "刚换了套构筑，感觉刷本顺手多了，推荐试试。",
        "周常任务记得做，攒下来奖励不少。",
        "谁的战力又涨了？带带我这条咸鱼。",
        "下个副本门槛有点高，准备攒装备硬刚。",
        "商会最近好货多，手快的盯一盯。"
    );

    private static final List<String> WORLD_BANTER = List.of(
        "你现在主刷哪个副本？想找个稳定节奏。",
        "我在比较装备词条，品质高但属性歪也不一定值。",
        "刚看战力榜，前排又有人换装了。",
        "先刷能稳定通关的本，掉落和经验都不会太亏。",
        "今天手气一般，连开几个箱子都很素。",
        "攒了点金币，纠结是强化还是去商会捡漏。",
        "卡战力了，准备回头补一波强化再往上冲。",
        "有没有人也在冲深渊裂隙？想交流下配装。",
        "任务奖励刷新了，先把日常清一清。",
        "这波掉落还行，挂商会出了换点活动经费。"
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
    private final RobotMemoryService memoryService;
    private final RobotSimulationProperties properties;

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
        GuildService guildService,
        RobotMemoryService memoryService,
        RobotSimulationProperties properties
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
        this.memoryService = memoryService;
        this.properties = properties;
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

    public RobotActionResult sweepDungeon(RobotDecisionContext context, RobotActionScore score) {
        DungeonConfig dungeon = context.sweepDungeon();
        if (dungeon == null || context.sweepTimes() <= 0) {
            return rest(context.actor(), "扫荡符和疲劳还没准备好，先调整路线。", score.reason());
        }

        DungeonService.DungeonSweepResult result;
        try {
            result = dungeonService.sweepDungeonForPlayer(context.player(), dungeon.id(), context.sweepTimes(), null);
        } catch (ApiException error) {
            String text = "准备扫荡时发现扫荡符、疲劳或通关记录不满足。";
            record(context.actor(), "rest", text, score.reason());
            return RobotActionResult.failure("rest", text);
        }

        jdbcTemplate.update(
            """
            UPDATE player
            SET dungeon_clears = dungeon_clears + ?, last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            result.times(),
            context.actor().id()
        );

        long ticketDrops = result.loot().stream()
            .filter(item -> SweepTicketPolicy.NORMAL_TICKET_TEMPLATE_ID.equals(item.templateId())
                || SweepTicketPolicy.SPECIAL_TICKET_TEMPLATE_ID.equals(item.templateId()))
            .count();
        String text = "扫荡 " + result.times() + " 次【" + result.dungeonName() + "】，获得 "
            + result.loot().size() + " 件物品 / " + ticketDrops + " 个扫荡符，战力更新到 " + result.combatPower() + "。";
        record(context.actor(), "dungeon_sweep", text, score.reason());
        processLoot(context.actor(), result, context.random());
        return RobotActionResult.success("dungeon_sweep", text);
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
            text += "（消耗 " + change.usedStoneCount() + " 颗强化石）";
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
            List<Long> upgradeGemIds = firstUpgradeableGemSet(snapshot.gems(), snapshot.materials().getOrDefault("mat_gem_dust", 0));
            if (!upgradeGemIds.isEmpty()) {
                EquipmentProcessingService.ProcessingResult result = equipmentProcessingService.upgradeGems(context.player(), upgradeGemIds);
                String text = "把三颗宝石合成为【" + result.item().displayName() + "】，继续给核心装备攒孔位收益。";
                chat(context.actor(), text);
                record(context.actor(), "processing_socket", text, score.reason());
                return RobotActionResult.success("processing_socket", text);
            }
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

    private List<Long> firstUpgradeableGemSet(List<ItemRecord> gems, int gemDust) {
        for (ItemRecord gem : gems) {
            int rank = gemRank(gem.templateId());
            if (rank >= 9 || gemDust < rank * 8) {
                continue;
            }
            List<Long> sameGems = new ArrayList<>();
            for (ItemRecord candidate : gems) {
                if (!candidate.templateId().equals(gem.templateId())) {
                    continue;
                }
                int available = Math.max(1, candidate.quantity());
                for (int index = 0; index < available && sameGems.size() < 3; index++) {
                    sameGems.add(candidate.id());
                }
                if (sameGems.size() >= 3) {
                    break;
                }
            }
            if (sameGems.size() == 3) {
                return sameGems;
            }
        }
        return List.of();
    }

    private int gemRank(String templateId) {
        int separator = templateId == null ? -1 : templateId.lastIndexOf('_');
        if (separator < 0 || separator >= templateId.length() - 1) {
            return 1;
        }
        try {
            return Integer.parseInt(templateId.substring(separator + 1));
        } catch (NumberFormatException error) {
            return 1;
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
        if (context.stamina() != null
            && context.stamina().current() <= Math.max(1, context.stamina().max()) * 0.15
            && context.staminaPotionCount() > 0) {
            RobotActionResult result = useFirstItem(
                context,
                item -> hasAnyTag(item, "stamina", "staminaPotion"),
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
                item -> item.chest() || hasAnyTag(item, "chest"),
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
                item -> hasAnyTag(item, "attribute", "attributePotion"),
                Comparator.comparingInt(ItemRecord::requiredLevel),
                "item_attribute",
                score
            );
            if (result.success()) {
                return result;
            }
        }

        if (context.stamina() != null
            && context.stamina().current() < Math.max(1, context.stamina().max()) * 0.8
            && context.staminaPotionCount() > 0) {
            return useFirstItem(
                context,
                item -> hasAnyTag(item, "stamina", "staminaPotion"),
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
            text = pickFreshLine(context.actor().id(), WORLD_BANTER, context.random());
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
        String text = pickFreshLine(context.actor().id(), GUILD_BANTER, context.random());
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text, channel) VALUES (?, ?, 'robot', ?, ?)",
            context.actor().id(),
            context.actor().name(),
            text,
            "guild:" + guildId
        );
        if (memoryService != null) {
            memoryService.recordChat(context.actor().id(), text);
        }
        record(context.actor(), "guild_chat", text, score.reason());
        return RobotActionResult.success("guild_chat", text);
    }

    public void trimChat() {
        jdbcTemplate.update(
            "DELETE FROM chat_message WHERE channel = 'world' AND id NOT IN (SELECT id FROM (SELECT id FROM chat_message WHERE channel = 'world' ORDER BY created_at DESC, id DESC LIMIT ?) recent)",
            properties.getWorldChatRetention()
        );
    }

    private void processLoot(RobotAgent actor, DungeonService.DungeonRunResult result, Random random) {
        processLoot(actor, result.player(), result.dungeonName(), result.loot(), random);
    }

    private void processLoot(RobotAgent actor, DungeonService.DungeonSweepResult result, Random random) {
        processLoot(actor, result.player(), result.dungeonName(), result.loot(), random);
    }

    private void processLoot(RobotAgent actor, PlayerRecord updatedPlayer, String dungeonName, List<ItemRecord> loot, Random random) {
        for (ItemRecord item : loot) {
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
                String text = "在【" + dungeonName + "】打到【" + drop.change().itemName() + "】，换到" + drop.change().slotName() + "上，战力提升 " + drop.change().powerGain() + "。";
                robotActivityLogService.record(updatedPlayer.id(), "equip", text);
            } else {
                marketService.listRobotOwnedItem(updatedPlayer, actor.title(), item, "机器人副本掉落 · " + dungeonName, random, true);
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
            .filter(this::robotCanUse)
            .filter(filter)
            .sorted(comparator)
            .toList();
        for (ItemRecord item : candidates) {
            try {
                InventoryService.UseItemResult result = inventoryService.useItem(context.player(), item.id());
                recordUseItemQuestEvents(context.player().id(), result);
                String extra = result.rewards().isEmpty()
                    ? ""
                    : "，获得 " + result.rewards().stream()
                        .map(ItemRecord::displayName)
                        .limit(2)
                        .collect(java.util.stream.Collectors.joining("、"));
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
            String rewardNames = result.rewards().stream()
                .map(ItemRecord::displayName)
                .limit(2)
                .collect(java.util.stream.Collectors.joining("、"));
            String text = "合成《" + result.recipeName() + "》"
                + (rewardNames.isBlank() ? "" : "，背包获得 " + rewardNames) + "。";
            chat(context.actor(), text);
            record(context.actor(), "item_craft", text, score.reason());
            return RobotActionResult.success("item_craft", text);
        } catch (ApiException error) {
            return RobotActionResult.failure("item_craft", "材料暂时不够。");
        }
    }

    private void recordUseItemQuestEvents(long playerId, InventoryService.UseItemResult result) {
        for (ItemEffectEvent event : result.events()) {
            questService.recordEvent(playerId, new QuestEvent(event.type(), event.targetId(), event.amount()));
        }
    }

    private boolean robotCanUse(ItemRecord item) {
        return item.usable() && !"never".equals(item.robotPolicy());
    }

    private boolean hasAnyTag(ItemRecord item, String... tags) {
        for (String tag : tags) {
            if (item.effectTags().contains(tag)) {
                return true;
            }
        }
        return false;
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
        if (!shouldPublishToWorldChat(text)) {
            return;
        }
        if (worldChatCoolingDown()) {
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO chat_message (player_id, sender_name, kind, text) VALUES (?, ?, 'robot', ?)",
            actor.id(),
            actor.name(),
            text
        );
        if (memoryService != null) {
            memoryService.recordChat(actor.id(), text);
        }
    }

    private boolean worldChatCoolingDown() {
        int cooldownSeconds = properties == null ? 0 : Math.max(0, properties.getWorldChatCooldownSeconds());
        if (cooldownSeconds == 0) {
            return false;
        }
        Integer recentRobotMessages = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM chat_message
            WHERE channel = 'world'
              AND kind = 'robot'
              AND created_at >= ?
            """,
            Integer.class,
            Timestamp.from(java.time.Instant.now().minusSeconds(cooldownSeconds))
        );
        return recentRobotMessages != null && recentRobotMessages > 0;
    }

    static boolean shouldPublishToWorldChat(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim();
        if ((normalized.startsWith("刚花 ") || normalized.startsWith("先换 ")) && normalized.contains("技能【")) {
            return false;
        }
        if (normalized.startsWith("领取任务《")
            || normalized.startsWith("使用《")
            || normalized.startsWith("合成《")) {
            return false;
        }
        if (normalized.startsWith("这轮副本击败 ") && normalized.contains("战力评估更新到 ")) {
            return false;
        }
        if (normalized.startsWith("换了 ") && normalized.contains("元，补进 ") && normalized.contains(" 金")) {
            return false;
        }
        if (normalized.contains("刚把【") && normalized.contains("】强化到 +")) {
            return false;
        }
        if (normalized.contains("强化【") && normalized.contains("】失败了")) {
            return false;
        }
        if (normalized.startsWith("切换到构筑【")) {
            return false;
        }
        return true;
    }

    /**
     * Pick a line from a template pool that the robot hasn't said recently, so the
     * world/guild channels don't visibly loop the same handful of sentences.
     */
    private String pickFreshLine(long robotId, List<String> pool, Random random) {
        String candidate = pool.get(random.nextInt(pool.size()));
        for (int attempt = 0; attempt < 4 && memoryService != null
            && memoryService.recentlySaid(robotId, candidate); attempt++) {
            candidate = pool.get(random.nextInt(pool.size()));
        }
        return candidate;
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
