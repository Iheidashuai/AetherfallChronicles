package com.mythicrealm.api.gameplay.dungeon;

import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.combat.CombatEngine;
import com.mythicrealm.api.gameplay.combat.CombatEngine.CombatEvent;
import com.mythicrealm.api.gameplay.combat.CombatEngine.EncounterOutcome;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.combat.EncounterGate;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService.DungeonPreview;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import com.mythicrealm.api.gameplay.skill.SkillService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DungeonService {
    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final QuestService questService;
    private final StaminaService staminaService;
    private final AnnouncementService announcementService;
    private final CombatStatsService combatStatsService;
    private final EncounterGate encounterGate;
    private final CombatEngine combatEngine;
    private final SkillService skillService;
    private final String configVersion;

    public static boolean isSpecialDungeon(String dungeonId) {
        return dungeonId != null && dungeonId.startsWith("special_");
    }

    public DungeonService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        PlayerService playerService,
        InventoryService inventoryService,
        QuestService questService,
        StaminaService staminaService,
        AnnouncementService announcementService,
        CombatStatsService combatStatsService,
        EncounterGate encounterGate,
        CombatEngine combatEngine,
        SkillService skillService,
        @Value("${mythic.config.version}") String configVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.questService = questService;
        this.staminaService = staminaService;
        this.announcementService = announcementService;
        this.combatStatsService = combatStatsService;
        this.encounterGate = encounterGate;
        this.combatEngine = combatEngine;
        this.skillService = skillService;
        this.configVersion = configVersion;
    }

    public List<DungeonProgressPreview> dungeonPreviews(long playerId) {
        PlayerRecord player = playerService.requireById(playerId);
        int combatPower = inventoryService.combatPower(player);
        StaminaSnapshot stamina = staminaService.snapshot(playerId);
        int normalSweepTickets = inventoryService.templateQuantity(playerId, SweepTicketPolicy.NORMAL_TICKET_TEMPLATE_ID);
        int specialSweepTickets = inventoryService.templateQuantity(playerId, SweepTicketPolicy.SPECIAL_TICKET_TEMPLATE_ID);
        Set<String> cleared = jdbcTemplate.queryForList(
                "SELECT DISTINCT dungeon_id FROM dungeon_run WHERE player_id = ? AND success = TRUE",
                String.class,
                playerId
            )
            .stream()
            .collect(Collectors.toSet());
        return gameConfigService.dungeonPreviews().stream()
            .map(preview -> DungeonProgressPreview.from(
                preview,
                cleared.contains(preview.id()),
                encounterGate.evaluate(player, combatPower, gameConfigService.requireDungeon(preview.id())),
                stamina,
                normalSweepTickets,
                specialSweepTickets
            ))
            .toList();
    }

    @Transactional
    public DungeonRunResult runDungeon(AuthenticatedAccount account, String dungeonId, String requestId) {
        PlayerRecord player = playerService.requireByAccount(account);
        return runDungeonForPlayer(player, dungeonId, requestId);
    }

    @Transactional
    public DungeonRunResult runDungeonForPlayer(PlayerRecord player, String dungeonId, String requestId) {
        String normalizedRequestId = normalizeRequestId(requestId);
        if (normalizedRequestId != null) {
            var existing = findExistingResult(player.id(), normalizedRequestId);
            if (existing != null) {
                return existing;
            }
        }

        DungeonConfig dungeon = gameConfigService.requireDungeon(dungeonId);
        boolean specialDungeon = isSpecialDungeon(dungeon.id());
        int combatPower = inventoryService.combatPower(player);
        var gate = encounterGate.evaluate(player, combatPower, dungeon);
        if (!gate.eligible()) {
            throw ApiException.badRequest("未达到副本门槛：" + gate.label());
        }
        StaminaSnapshot stamina = staminaService.consume(player.id(), 1);
        Random random = new Random(Objects.hash(player.id(), dungeonId, normalizedRequestId, System.nanoTime()));
        var logs = new ArrayList<String>();
        var frames = new ArrayList<BattleFrame>();
        var loot = new ArrayList<ItemRecord>();
        int expGained = 0;
        int goldGained = 0;
        int monstersKilled = 0;
        int rareOrBetterLoot = 0;
        var equipment = inventoryService.equippedItems(player.id()).values();
        var playerCombatant = combatStatsService.playerCombatant(player, equipment);
        var playerSkills = skillService.playerCombatSkills(player);
        int playerMaxHp = playerCombatant.stats().maxHp();
        int playerHp = Math.max(1, playerMaxHp);
        double powerRatio = combatPower / (double) Math.max(1, dungeon.recommendedPower());
        boolean success = true;

        appendFrame(logs, frames, "进入副本【" + dungeon.name() + "】", "system", "准备", null, playerHp, playerMaxHp, 0, 0);
        appendFrame(logs, frames, "门槛 " + dungeon.minimumLevel() + " 级 / " + dungeon.minimumPower() + " 战力，当前战力 " + combatPower, "system", "准备", null, playerHp, playerMaxHp, 0, 0);
        appendFrame(
            logs,
            frames,
            powerRatio >= 1.15 ? "战力评估：压制区域，推进节奏稳定。" : "战力评估：刚达标，仍需注意命中、暴击和 Boss 机制。",
            powerRatio >= 1.15 ? "system" : "danger",
            "准备",
            null,
            playerHp,
            playerMaxHp,
            0,
            0
        );
        if (specialDungeon) {
            appendFrame(
                logs,
                frames,
                "特殊副本规则：手动通关会记录评分，后续扫荡按历史最佳评分结算。",
                "danger",
                "准备",
                null,
                playerHp,
                playerMaxHp,
                0,
                0
            );
        }

        battle:
        for (int roomIndex = 0; roomIndex < dungeon.rooms().size(); roomIndex++) {
            var room = dungeon.rooms().get(roomIndex);
            String roomLabel = room.isBossRoom() ? "BOSS 房间" : "房间 " + (roomIndex + 1);
            appendFrame(logs, frames, roomLabel + " 开始", "system", roomLabel, null, playerHp, playerMaxHp, 0, 0);
            for (var roomMonster : room.monsters()) {
                MonsterConfig monster = gameConfigService.requireMonster(roomMonster.monsterId());
                for (int i = 0; i < roomMonster.count(); i++) {
                    var enemy = combatStatsService.monsterCombatant(monster);
                    int monsterMaxHp = enemy.stats().maxHp();
                    int roundLimit = roundLimit();
                    appendFrame(
                        logs,
                        frames,
                        "遭遇 " + monster.name() + " Lv." + monster.level() + "，" + monster.archetype() + " / " + monster.damageType() + "，生命 " + monsterMaxHp,
                        monster.isBoss() ? "danger" : "system",
                        roomLabel,
                        monster.name(),
                        playerHp,
                        playerMaxHp,
                        monsterMaxHp,
                        monsterMaxHp
                    );
                    EncounterOutcome encounter = combatEngine.fight(playerCombatant, enemy, playerHp, playerSkills, skillService.monsterCombatSkills(enemy), roundLimit, random);
                    for (CombatEvent event : encounter.events()) {
                        appendCombatEvent(logs, frames, event, roomLabel);
                    }
                    playerHp = encounter.playerHp();
                    if (encounter.playerDefeated()) {
                        success = false;
                        break battle;
                    }
                    if (!encounter.enemyDefeated()) {
                        success = false;
                        break battle;
                    }
                    monstersKilled++;
                    expGained += monster.expReward();
                    goldGained += monster.goldReward();
                    appendFrame(
                        logs,
                        frames,
                        "击败 " + monster.name() + "，获得 " + monster.expReward() + " 经验 / " + monster.goldReward() + " 金",
                        "victory",
                        roomLabel,
                        monster.name(),
                        playerHp,
                        playerMaxHp,
                        0,
                        monsterMaxHp
                    );
                    if (specialDungeon || monster.lootTable() == null) {
                        // 特殊副本的掉落（碎片/爆装/保底）按通关评分在结算阶段统一处理。
                        continue;
                    }
                    for (var lootEntry : monster.lootTable()) {
                        ItemTemplate template = gameConfigService.requireItem(lootEntry.itemId());
                        if (random.nextDouble() <= lootEntry.dropRate()) {
                            ItemRecord item = inventoryService.addLootToInventory(player.id(), template, random);
                            loot.add(item);
                            if (isRareOrBetter(template.quality())) {
                                rareOrBetterLoot++;
                            }
                            appendFrame(
                                logs,
                                frames,
                                "获得装备：" + item.displayName(),
                                lootTone(template.quality()),
                                roomLabel,
                                monster.name(),
                                playerHp,
                                playerMaxHp,
                                0,
                                encounter.enemyMaxHp()
                            );
                        }
                    }
                }
            }
            if (success && playerHp > 0 && roomIndex < dungeon.rooms().size() - 1) {
                int recover = Math.max(1, (int) Math.round(playerMaxHp * combatStatsService.roomRecoveryRate(player)));
                playerHp = Math.min(playerMaxHp, playerHp + recover);
                appendFrame(logs, frames, "短暂整备，恢复 " + recover + " 生命，当前 " + playerHp + "/" + playerMaxHp, "heal", roomLabel, null, playerHp, playerMaxHp, 0, 0, "system", "heal", recover, false, false);
            }
        }
        String rating = rating(success, powerRatio, playerHp, playerMaxHp);
        if (success && specialDungeon) {
            rareOrBetterLoot += awardSpecialLoot(player.id(), dungeon, rating, random, loot, logs, frames, playerHp, playerMaxHp);
        } else if (success) {
            rareOrBetterLoot += awardNormalRatingBonus(player.id(), dungeon, rating, random, loot, logs, frames, playerHp, playerMaxHp);
        }
        if (success) {
            awardSweepTicket(player.id(), dungeon, random, loot, logs, frames, playerHp, playerMaxHp);
        }

        PlayerRecord updatedPlayer = playerService.applyRewards(player.id(), expGained, goldGained);
        DungeonRunResult result = new DungeonRunResult(
            dungeon.id(),
            dungeon.name(),
            success,
            rating,
            monstersKilled,
            expGained,
            goldGained,
            loot,
            updatedPlayer,
            inventoryService.combatPower(updatedPlayer),
            logs,
            dungeon.recommendedPower(),
            playerMaxHp,
            playerHp,
            frames,
            stamina
        );
        persistRun(player.id(), dungeon.id(), normalizedRequestId, result);
        publishLegendaryLoot(player.name(), dungeon.name(), loot);
        if (success) {
            questService.recordEvent(player.id(), new QuestEvent("dungeonCompleted", dungeon.id(), 1));
        }
        questService.recordEvent(player.id(), new QuestEvent("staminaSpent", null, 1));
        if (monstersKilled > 0) {
            questService.recordEvent(player.id(), new QuestEvent("monsterKills", null, monstersKilled));
        }
        if (rareOrBetterLoot > 0) {
            questService.recordEvent(player.id(), new QuestEvent("itemQualityObtained", "rare", rareOrBetterLoot));
        }
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, result.combatPower()));
        return result;
    }

    @Transactional
    public DungeonSweepResult sweepDungeon(AuthenticatedAccount account, String dungeonId, int times, String requestId) {
        PlayerRecord player = playerService.requireByAccount(account);
        return sweepDungeonForPlayer(player, dungeonId, times, requestId);
    }

    @Transactional
    public DungeonSweepResult sweepDungeonForPlayer(PlayerRecord player, String dungeonId, int times, String requestId) {
        DungeonConfig dungeon = gameConfigService.requireDungeon(dungeonId);
        if (!hasCleared(player.id(), dungeon.id())) {
            throw ApiException.badRequest("通关后才能扫荡该副本");
        }

        int sweepTimes = requireSweepTimes(times);
        String ticketTemplateId = SweepTicketPolicy.ticketTemplateId(dungeon.id());
        String ticketName = SweepTicketPolicy.ticketName(dungeon.id());
        int ticketCount = inventoryService.templateQuantity(player.id(), ticketTemplateId);
        if (ticketCount < sweepTimes) {
            throw ApiException.badRequest(ticketName + "不足，还需要 " + (sweepTimes - ticketCount) + " 个");
        }
        StaminaSnapshot currentStamina = staminaService.snapshot(player.id());
        if (currentStamina.current() < sweepTimes) {
            throw ApiException.badRequest("疲劳不足，需要 " + sweepTimes + "，当前 " + currentStamina.current());
        }
        inventoryService.consumeTemplateQuantity(player.id(), ticketTemplateId, sweepTimes);
        StaminaSnapshot stamina = staminaService.consume(player.id(), sweepTimes);
        Random random = new Random(Objects.hash(player.id(), dungeonId, normalizeRequestId(requestId), System.nanoTime()));
        var logs = new ArrayList<String>();
        var loot = new ArrayList<ItemRecord>();
        int expGained = 0;
        int goldGained = 0;
        int monstersKilled = 0;
        int rareOrBetterLoot = 0;
        boolean specialDungeon = isSpecialDungeon(dungeon.id());
        String sweepRating = specialDungeon ? bestManualRating(player.id(), dungeon.id()) : "SWEEP";

        for (int sweepIndex = 1; sweepIndex <= sweepTimes; sweepIndex++) {
            int runExp = 0;
            int runGold = 0;
            int runLoot = 0;
            int runKills = 0;
            int lootStart = loot.size();
            List<ItemRecord> runLootItems;
            if (specialDungeon) {
                var passLogs = new ArrayList<String>();
                var passFrames = new ArrayList<BattleFrame>();
                var passLoot = new ArrayList<ItemRecord>();
                rareOrBetterLoot += awardSpecialLoot(player.id(), dungeon, sweepRating, random, passLoot, passLogs, passFrames, 1, 1);
                awardSweepTicket(player.id(), dungeon, random, passLoot, passLogs, passFrames, 1, 1);
                runLootItems = List.copyOf(passLoot);
                runLoot = runLootItems.size();
                loot.addAll(runLootItems);
            } else {
                for (var room : dungeon.rooms()) {
                    for (var roomMonster : room.monsters()) {
                        MonsterConfig monster = gameConfigService.requireMonster(roomMonster.monsterId());
                        for (int i = 0; i < roomMonster.count(); i++) {
                            monstersKilled++;
                            runKills++;
                            expGained += monster.expReward();
                            goldGained += monster.goldReward();
                            runExp += monster.expReward();
                            runGold += monster.goldReward();
                            if (monster.lootTable() == null) {
                                continue;
                            }
                            for (var lootEntry : monster.lootTable()) {
                                if (random.nextDouble() <= lootEntry.dropRate()) {
                                    ItemTemplate template = gameConfigService.requireItem(lootEntry.itemId());
                                    ItemRecord item = inventoryService.addLootToInventory(player.id(), template, random);
                                    loot.add(item);
                                    runLoot++;
                                    if (isRareOrBetter(template.quality())) {
                                        rareOrBetterLoot++;
                                    }
                                }
                            }
                        }
                    }
                }
                var passLogs = new ArrayList<String>();
                var passFrames = new ArrayList<BattleFrame>();
                if (awardSweepTicket(player.id(), dungeon, random, loot, passLogs, passFrames, 1, 1)) {
                    runLoot++;
                }
                runLootItems = List.copyOf(loot.subList(lootStart, loot.size()));
            }
            persistSweepRun(
                player.id(),
                dungeon.id(),
                sweepRunRequestId(normalizeRequestId(requestId), sweepIndex),
                sweepRating,
                runKills,
                runExp,
                runGold,
                inventoryService.combatPower(player),
                dungeon.recommendedPower(),
                runLootItems,
                List.of(sweepLogLine(sweepIndex, specialDungeon, sweepRating, runKills, runExp, runGold, runLootItems))
            );
            logs.add(sweepLogLine(sweepIndex, specialDungeon, sweepRating, runKills, runExp, runGold, runLootItems));
        }

        PlayerRecord updatedPlayer = playerService.applyRewards(player.id(), expGained, goldGained);
        DungeonSweepResult result = new DungeonSweepResult(
            dungeon.id(),
            dungeon.name(),
            sweepTimes,
            monstersKilled,
            expGained,
            goldGained,
            loot,
            updatedPlayer,
            inventoryService.combatPower(updatedPlayer),
            logs,
            stamina
        );
        persistSweep(player.id(), dungeon.id(), normalizeRequestId(requestId), result);
        publishLegendaryLoot(player.name(), dungeon.name(), loot, SweepTicketPolicy.MAX_SWEEP_LOOT_ANNOUNCEMENTS);
        questService.recordEvent(player.id(), new QuestEvent("dungeonCompleted", dungeon.id(), sweepTimes));
        questService.recordEvent(player.id(), new QuestEvent("staminaSpent", null, sweepTimes));
        if (monstersKilled > 0) {
            questService.recordEvent(player.id(), new QuestEvent("monsterKills", null, monstersKilled));
        }
        if (rareOrBetterLoot > 0) {
            questService.recordEvent(player.id(), new QuestEvent("itemQualityObtained", "rare", rareOrBetterLoot));
        }
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, result.combatPower()));
        return result;
    }

    private boolean hasCleared(long playerId, String dungeonId) {
        Integer clears = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dungeon_run WHERE player_id = ? AND dungeon_id = ? AND success = TRUE",
            Integer.class,
            playerId,
            dungeonId
        );
        return clears != null && clears > 0;
    }

    static int requireSweepTimes(int times) {
        if (!SweepTicketPolicy.isSupportedSweepTimes(times)) {
            throw ApiException.badRequest("只支持扫荡 10 次或 50 次");
        }
        return times;
    }

    private String bestManualRating(long playerId, String dungeonId) {
        return jdbcTemplate.queryForList(
                """
                SELECT rating
                FROM dungeon_run
                WHERE player_id = ? AND dungeon_id = ? AND success = TRUE AND run_type = 'manual'
                """,
                String.class,
                playerId,
                dungeonId
            )
            .stream()
            .max(Comparator.comparingInt(SweepTicketPolicy::ratingRank))
            .orElse("B");
    }

    private String sweepRunRequestId(String requestId, int sweepIndex) {
        return requestId == null ? null : requestId + ":sweep:" + sweepIndex;
    }

    private String sweepLogLine(
        int sweepIndex,
        boolean specialDungeon,
        String rating,
        int runKills,
        int runExp,
        int runGold,
        List<ItemRecord> loot
    ) {
        long ticketDrops = loot.stream()
            .filter(item -> SweepTicketPolicy.NORMAL_TICKET_TEMPLATE_ID.equals(item.templateId())
                || SweepTicketPolicy.SPECIAL_TICKET_TEMPLATE_ID.equals(item.templateId()))
            .count();
        String ticketText = ticketDrops > 0 ? "，含扫荡符 " + ticketDrops + " 个" : "";
        if (specialDungeon) {
            return "第 " + sweepIndex + " 次扫荡：按历史最佳 " + rating + " 评分结算，掉落 " + loot.size() + " 件物品" + ticketText + "。";
        }
        return "第 " + sweepIndex + " 次扫荡：击败 " + runKills + " 只魔物，获得 " + runExp + " 经验 / " + runGold + " 金，掉落 " + loot.size() + " 件物品" + ticketText + "。";
    }

    private boolean awardSweepTicket(
        long playerId,
        DungeonConfig dungeon,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        if (random.nextDouble() > SweepTicketPolicy.ticketDropRate(dungeon.id())) {
            return false;
        }
        ItemRecord ticket = inventoryService.grantItem(
            playerId,
            SweepTicketPolicy.ticketTemplateId(dungeon.id()),
            1,
            random
        ).getFirst();
        loot.add(ticket);
        appendFrame(logs, frames, "结算掉落：" + ticket.name(), "loot", "结算", null, playerHp, playerMaxHp, 0, 0);
        return true;
    }

    private void publishLegendaryLoot(String playerName, String dungeonName, List<ItemRecord> loot) {
        publishLegendaryLoot(playerName, dungeonName, loot, Integer.MAX_VALUE);
    }

    private void publishLegendaryLoot(String playerName, String dungeonName, List<ItemRecord> loot, int maxAnnouncements) {
        List<ItemRecord> featuredLoot = loot.stream()
            .filter(item -> "immortal".equals(item.quality()) || "legendary".equals(item.quality()))
            .sorted(Comparator.comparingInt((ItemRecord item) -> "immortal".equals(item.quality()) ? 2 : 1).reversed())
            .limit(Math.max(0, maxAnnouncements))
            .toList();
        for (ItemRecord item : featuredLoot) {
            if ("immortal".equals(item.quality())) {
                announcementService.publishImmortalLoot(playerName, dungeonName, item.displayName());
            } else if ("legendary".equals(item.quality())) {
                announcementService.publishLegendaryLoot(playerName, dungeonName, item.displayName());
            }
        }
    }

    private String lootTone(String quality) {
        return switch (quality) {
            case "immortal" -> "immortal-loot";
            case "legendary" -> "legendary-loot";
            default -> "loot";
        };
    }

    private static final String[] EQUIPMENT_SLOTS =
        {"weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace", "ring"};

    /** Map a special dungeon to its gear tier (60/70/80/90) from its minimum level. */
    private int specialTier(DungeonConfig dungeon) {
        int level = dungeon.minimumLevel();
        if (level >= 90) {
            return 90;
        }
        if (level >= 80) {
            return 80;
        }
        if (level >= 70) {
            return 70;
        }
        return 60;
    }

    /**
     * Special-dungeon loot, driven by the clear rating: higher rating -> more fragments
     * and a higher chance of a direct legendary/immortal piece. Fragments are the
     * reliable currency (so a clear is never "empty"); direct gear is the jackpot.
     * The legacy pity net is preserved for dry streaks.
     */
    private int awardSpecialLoot(
        long playerId,
        DungeonConfig dungeon,
        String rating,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        int tier = specialTier(dungeon);
        int mainFragments = switch (rating) {
            case "S" -> 6;
            case "A" -> 4;
            default -> 2;
        };
        int sideFragments = switch (rating) {
            case "S" -> 2;
            case "A" -> 1;
            default -> 0;
        };
        // Low tiers rain legendary fragments, high tiers rain immortal fragments;
        // the 70/80 tiers also sprinkle a few of the other kind.
        String mainFragment = tier >= 80 ? "mat_fragment_immortal" : "mat_fragment_legendary";
        String sideFragment = tier >= 80 ? "mat_fragment_legendary" : "mat_fragment_immortal";
        grantFragment(playerId, mainFragment, mainFragments, random, loot, logs, frames, playerHp, playerMaxHp);
        if ((tier == 70 || tier == 80) && sideFragments > 0) {
            grantFragment(playerId, sideFragment, sideFragments, random, loot, logs, frames, playerHp, playerMaxHp);
        }

        int rareOrBetter = 0;
        double legendaryChance = switch (rating) {
            case "S" -> 0.25;
            case "A" -> 0.15;
            default -> 0.08;
        };
        double immortalChance = switch (rating) {
            case "S" -> 0.06;
            case "A" -> 0.03;
            default -> 0.01;
        };
        if (random.nextDouble() < immortalChance) {
            rareOrBetter += grantTierGear(playerId, tier, "immortal", random, loot, logs, frames, playerHp, playerMaxHp);
        }
        if (random.nextDouble() < legendaryChance) {
            rareOrBetter += grantTierGear(playerId, tier, "legendary", random, loot, logs, frames, playerHp, playerMaxHp);
        }
        rareOrBetter += applySpecialPity(playerId, dungeon, random, loot, logs, frames, playerHp, playerMaxHp);
        return rareOrBetter;
    }

    private void grantFragment(
        long playerId,
        String fragmentId,
        int count,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        if (count <= 0) {
            return;
        }
        loot.addAll(inventoryService.grantItem(playerId, fragmentId, count, random));
        String label = "mat_fragment_immortal".equals(fragmentId) ? "不朽装备碎片" : "传说装备碎片";
        appendFrame(logs, frames, "血月结算：获得 " + count + " 个" + label, "loot", "血月结算", null, playerHp, playerMaxHp, 0, 0);
    }

    private int grantTierGear(
        long playerId,
        int tier,
        String quality,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        String slot = EQUIPMENT_SLOTS[random.nextInt(EQUIPMENT_SLOTS.length)];
        ItemTemplate template = gameConfigService.requireItem("eq_bloodmoon_l" + tier + "_" + slot + "_" + quality);
        ItemRecord item = inventoryService.addLootToInventory(playerId, template, random);
        loot.add(item);
        appendFrame(logs, frames, "血月爆装：" + item.displayName(), lootTone(quality), "血月结算", null, playerHp, playerMaxHp, 0, 0);
        return isRareOrBetter(quality) ? 1 : 0;
    }

    /**
     * Normal dungeons: a high clear rating grants bonus loot rolls over the boss table
     * (A -> +1 pass, S -> +2 passes). Quality is unchanged (normal caps at epic). Sweeps
     * have no combat and therefore no rating, so manual high-rating clears out-earn sweeps.
     */
    private int awardNormalRatingBonus(
        long playerId,
        DungeonConfig dungeon,
        String rating,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        int bonusRolls = switch (rating) {
            case "S" -> 2;
            case "A" -> 1;
            default -> 0;
        };
        if (bonusRolls == 0) {
            return 0;
        }
        int rareOrBetter = 0;
        for (var room : dungeon.rooms()) {
            if (!room.isBossRoom()) {
                continue;
            }
            for (var roomMonster : room.monsters()) {
                MonsterConfig monster = gameConfigService.requireMonster(roomMonster.monsterId());
                if (monster.lootTable() == null) {
                    continue;
                }
                for (int r = 0; r < bonusRolls; r++) {
                    for (var lootEntry : monster.lootTable()) {
                        if (random.nextDouble() <= lootEntry.dropRate()) {
                            ItemTemplate template = gameConfigService.requireItem(lootEntry.itemId());
                            ItemRecord item = inventoryService.addLootToInventory(playerId, template, random);
                            loot.add(item);
                            if (isRareOrBetter(template.quality())) {
                                rareOrBetter++;
                            }
                            appendFrame(logs, frames, "高分奖励掉落：" + item.displayName(), lootTone(template.quality()), "结算", null, playerHp, playerMaxHp, 0, 0);
                        }
                    }
                }
            }
        }
        return rareOrBetter;
    }

    private int applySpecialPity(
        long playerId,
        DungeonConfig dungeon,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        int addedRareOrBetter = 0;
        if (!containsQuality(loot, "immortal")) {
            int immortalMisses = specialSuccessesSinceQuality(playerId, "immortal");
            boolean hardPity = immortalMisses >= 99;
            boolean softPity = immortalMisses >= 59 && random.nextDouble() < Math.min(1.0, (immortalMisses - 58) * 0.025);
            if (hardPity || softPity) {
                addedRareOrBetter += addPityLoot(playerId, dungeon, "immortal", random, loot, logs, frames, playerHp, playerMaxHp);
            }
        }
        if (!containsLegendaryOrBetter(loot)) {
            int legendaryMisses = specialSuccessesSinceLegendaryOrBetter(playerId);
            if (legendaryMisses >= 14) {
                addedRareOrBetter += addPityLoot(playerId, dungeon, "legendary", random, loot, logs, frames, playerHp, playerMaxHp);
            }
        }
        return addedRareOrBetter;
    }

    private int addPityLoot(
        long playerId,
        DungeonConfig dungeon,
        String quality,
        Random random,
        List<ItemRecord> loot,
        List<String> logs,
        List<BattleFrame> frames,
        int playerHp,
        int playerMaxHp
    ) {
        List<ItemTemplate> candidates = specialDropCandidates(dungeon, quality);
        if (candidates.isEmpty()) {
            return 0;
        }
        ItemTemplate template = candidates.get(random.nextInt(candidates.size()));
        ItemRecord item = inventoryService.addLootToInventory(playerId, template, random);
        loot.add(item);
        appendFrame(
            logs,
            frames,
            "血月保底触发，获得装备：" + item.displayName(),
            lootTone(template.quality()),
            "血月结算",
            null,
            playerHp,
            playerMaxHp,
            0,
            0
        );
        return isRareOrBetter(template.quality()) ? 1 : 0;
    }

    private List<ItemTemplate> specialDropCandidates(DungeonConfig dungeon, String quality) {
        List<ItemTemplate> candidates = new ArrayList<>();
        for (var room : dungeon.rooms()) {
            for (var roomMonster : room.monsters()) {
                MonsterConfig monster = gameConfigService.requireMonster(roomMonster.monsterId());
                if (monster.lootTable() == null) {
                    continue;
                }
                for (var lootEntry : monster.lootTable()) {
                    ItemTemplate item = gameConfigService.requireItem(lootEntry.itemId());
                    if (quality.equals(item.quality()) && candidates.stream().noneMatch(candidate -> candidate.id().equals(item.id()))) {
                        candidates.add(item);
                    }
                }
            }
        }
        return candidates;
    }

    private boolean containsQuality(List<ItemRecord> loot, String quality) {
        return loot.stream().anyMatch(item -> quality.equals(item.quality()));
    }

    private boolean containsLegendaryOrBetter(List<ItemRecord> loot) {
        return loot.stream().anyMatch(item -> "legendary".equals(item.quality()) || "immortal".equals(item.quality()));
    }

    private int specialSuccessesSinceQuality(long playerId, String quality) {
        Long lastLootRunId = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(MAX(dr.id), 0)
            FROM dungeon_run dr
            JOIN dungeon_run_loot loot ON loot.dungeon_run_id = dr.id
            WHERE dr.player_id = ? AND dr.dungeon_id LIKE 'special_%' AND loot.quality = ?
            """,
            Long.class,
            playerId,
            quality
        );
        return countSpecialSuccessesAfter(playerId, lastLootRunId == null ? 0 : lastLootRunId);
    }

    private int specialSuccessesSinceLegendaryOrBetter(long playerId) {
        Long lastLootRunId = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(MAX(dr.id), 0)
            FROM dungeon_run dr
            JOIN dungeon_run_loot loot ON loot.dungeon_run_id = dr.id
            WHERE dr.player_id = ? AND dr.dungeon_id LIKE 'special_%'
              AND loot.quality IN ('legendary', 'immortal')
            """,
            Long.class,
            playerId
        );
        return countSpecialSuccessesAfter(playerId, lastLootRunId == null ? 0 : lastLootRunId);
    }

    private int countSpecialSuccessesAfter(long playerId, long runId) {
        Integer clears = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM dungeon_run
            WHERE player_id = ? AND success = TRUE AND dungeon_id LIKE 'special_%' AND id > ?
            """,
            Integer.class,
            playerId,
            runId
        );
        return clears == null ? 0 : clears;
    }

    private int roundLimit() {
        return CombatEngine.DEFAULT_ROUND_LIMIT;
    }

    private String rating(boolean success, double powerRatio, int playerHp, int playerMaxHp) {
        if (!success) {
            return "F";
        }
        double hpRatio = playerHp / (double) Math.max(1, playerMaxHp);
        if (powerRatio >= 1.25 && hpRatio >= 0.65) {
            return "S";
        }
        if (powerRatio >= 0.95 && hpRatio >= 0.35) {
            return "A";
        }
        return "B";
    }

    private boolean isRareOrBetter(String quality) {
        return switch (quality) {
            case "rare", "epic", "legendary", "immortal" -> true;
            default -> false;
        };
    }

    private void appendFrame(
        List<String> logs,
        List<BattleFrame> frames,
        String text,
        String tone,
        String roomLabel,
        String enemyName,
        int playerHp,
        int playerMaxHp,
        int enemyHp,
        int enemyMaxHp
    ) {
        appendFrame(logs, frames, text, tone, roomLabel, enemyName, playerHp, playerMaxHp, enemyHp, enemyMaxHp, "system", "phase", 0, false, false, null, null, null, "system", 0);
    }

    private void appendFrame(
        List<String> logs,
        List<BattleFrame> frames,
        String text,
        String tone,
        String roomLabel,
        String enemyName,
        int playerHp,
        int playerMaxHp,
        int enemyHp,
        int enemyMaxHp,
        String actor,
        String eventType,
        int damage,
        boolean critical,
        boolean missed
    ) {
        appendFrame(logs, frames, text, tone, roomLabel, enemyName, playerHp, playerMaxHp, enemyHp, enemyMaxHp, actor, eventType, damage, critical, missed, null, null, null, actor, 0);
    }

    private void appendFrame(
        List<String> logs,
        List<BattleFrame> frames,
        String text,
        String tone,
        String roomLabel,
        String enemyName,
        int playerHp,
        int playerMaxHp,
        int enemyHp,
        int enemyMaxHp,
        String actor,
        String eventType,
        int damage,
        boolean critical,
        boolean missed,
        String skillId,
        String skillName,
        String visualKey,
        String targetSide,
        int effectValue
    ) {
        logs.add(text);
        frames.add(new BattleFrame(
            frames.size() + 1,
            text,
            tone,
            roomLabel,
            enemyName,
            playerHp,
            playerMaxHp,
            enemyHp,
            enemyMaxHp,
            actor,
            eventType,
            damage,
            critical,
            missed,
            skillId,
            skillName,
            visualKey,
            targetSide,
            effectValue
        ));
    }

    private void appendCombatEvent(List<String> logs, List<BattleFrame> frames, CombatEvent event, String roomLabel) {
        appendFrame(
            logs,
            frames,
            event.text(),
            event.tone(),
            roomLabel,
            event.enemyName(),
            event.playerHp(),
            event.playerMaxHp(),
            event.enemyHp(),
            event.enemyMaxHp(),
            event.actor(),
            event.eventType(),
            event.damage(),
            event.critical(),
            event.missed(),
            event.skillId(),
            event.skillName(),
            event.visualKey(),
            event.targetSide(),
            event.effectValue()
        );
    }

    private DungeonRunResult findExistingResult(long playerId, String requestId) {
        var rows = jdbcTemplate.query(
            """
            SELECT id, dungeon_id, success, rating, monsters_killed, exp_gained, gold_gained,
                   combat_power, recommended_power, player_max_hp, player_final_hp
            FROM dungeon_run
            WHERE player_id = ? AND request_id = ?
            """,
            (rs, rowNum) -> new StoredRun(
                rs.getLong("id"),
                rs.getString("dungeon_id"),
                rs.getBoolean("success"),
                rs.getString("rating"),
                rs.getInt("monsters_killed"),
                rs.getInt("exp_gained"),
                rs.getInt("gold_gained"),
                rs.getInt("combat_power"),
                rs.getInt("recommended_power"),
                rs.getInt("player_max_hp"),
                rs.getInt("player_final_hp")
            ),
            playerId,
            requestId
        );
        if (rows.isEmpty()) {
            return null;
        }
        StoredRun run = rows.get(0);
        DungeonConfig dungeon = gameConfigService.requireDungeon(run.dungeonId());
        return new DungeonRunResult(
            run.dungeonId(),
            dungeon.name(),
            run.success(),
            run.rating(),
            run.monstersKilled(),
            run.expGained(),
            run.goldGained(),
            loadRunLoot(run.id(), playerId),
            playerService.requireById(playerId),
            run.combatPower(),
            loadRunLogs(run.id()),
            run.recommendedPower(),
            run.playerMaxHp(),
            run.playerFinalHp(),
            loadRunFrames(run.id()),
            staminaService.snapshot(playerId)
        );
    }

    private void persistRun(long playerId, String dungeonId, String requestId, DungeonRunResult result) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO dungeon_run
                (player_id, dungeon_id, config_version, request_id, run_type, success, rating, monsters_killed,
                 exp_gained, gold_gained, combat_power, recommended_power, player_max_hp, player_final_hp)
                VALUES (?, ?, ?, ?, 'manual', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, dungeonId);
            ps.setString(3, configVersion);
            ps.setString(4, requestId);
            ps.setBoolean(5, result.success());
            ps.setString(6, result.rating());
            ps.setInt(7, result.monstersKilled());
            ps.setInt(8, result.expGained());
            ps.setInt(9, result.goldGained());
            ps.setInt(10, result.combatPower());
            ps.setInt(11, result.recommendedPower());
            ps.setInt(12, result.playerMaxHp());
            ps.setInt(13, result.playerFinalHp());
            return ps;
        }, keyHolder);
        long runId = keyHolder.getKey().longValue();
        persistRunLoot(runId, result.loot());
        persistRunLogs(runId, result.logs());
        persistRunFrames(runId, result.frames());
        jdbcTemplate.update(
            """
            INSERT INTO economy_audit_event
            (player_id, event_type, reference_id, dungeon_id, success, rating, monsters_killed,
             exp_gained, gold_gained, loot_count, sweep_times, combat_power)
            VALUES (?, 'DUNGEON_REWARD', ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
            """,
            playerId,
            requestId == null ? dungeonId : requestId,
            dungeonId,
            result.success(),
            result.rating(),
            result.monstersKilled(),
            result.expGained(),
            result.goldGained(),
            result.loot().size(),
            result.combatPower()
        );
    }

    private void persistSweepRun(
        long playerId,
        String dungeonId,
        String requestId,
        String rating,
        int monstersKilled,
        int expGained,
        int goldGained,
        int combatPower,
        int recommendedPower,
        List<ItemRecord> loot,
        List<String> logs
    ) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO dungeon_run
                (player_id, dungeon_id, config_version, request_id, run_type, success, rating, monsters_killed,
                 exp_gained, gold_gained, combat_power, recommended_power, player_max_hp, player_final_hp)
                VALUES (?, ?, ?, ?, 'sweep', TRUE, ?, ?, ?, ?, ?, ?, 0, 0)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, dungeonId);
            ps.setString(3, configVersion);
            ps.setString(4, requestId);
            ps.setString(5, rating);
            ps.setInt(6, monstersKilled);
            ps.setInt(7, expGained);
            ps.setInt(8, goldGained);
            ps.setInt(9, combatPower);
            ps.setInt(10, recommendedPower);
            return ps;
        }, keyHolder);
        long runId = keyHolder.getKey().longValue();
        persistRunLoot(runId, loot);
        persistRunLogs(runId, logs);
    }

    private void persistSweep(long playerId, String dungeonId, String requestId, DungeonSweepResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO economy_audit_event
            (player_id, event_type, reference_id, dungeon_id, success, rating, monsters_killed,
             exp_gained, gold_gained, loot_count, sweep_times, combat_power)
            VALUES (?, 'DUNGEON_SWEEP_REWARD', ?, ?, TRUE, NULL, ?, ?, ?, ?, ?, ?)
            """,
            playerId,
            requestId == null ? dungeonId : requestId,
            dungeonId,
            result.monstersKilled(),
            result.expGained(),
            result.goldGained(),
            result.loot().size(),
            result.times(),
            result.combatPower()
        );
    }

    private void persistRunLoot(long runId, List<ItemRecord> loot) {
        AtomicInteger order = new AtomicInteger(1);
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO dungeon_run_loot
            (dungeon_run_id, loot_order, item_id, item_template_id, item_name, item_type, quality,
             required_level, attack_bonus, defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price,
             enhancement_level, enhancement_luck)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            loot,
            100,
            (ps, item) -> {
                ps.setLong(1, runId);
                ps.setInt(2, order.getAndIncrement());
                ps.setLong(3, item.id());
                ps.setString(4, item.templateId());
                ps.setString(5, item.name());
                ps.setString(6, item.itemType());
                ps.setString(7, item.quality());
                ps.setInt(8, item.requiredLevel());
                ps.setInt(9, item.attackBonus());
                ps.setInt(10, item.defenseBonus());
                ps.setInt(11, item.resistanceBonus());
                ps.setInt(12, item.hpBonus());
                ps.setInt(13, item.mpBonus());
                ps.setBigDecimal(14, item.critBonus());
                ps.setInt(15, item.sellPrice());
                ps.setInt(16, item.enhancementLevel());
                ps.setInt(17, item.enhancementLuck());
            }
        );
    }

    private void persistRunLogs(long runId, List<String> logs) {
        AtomicInteger order = new AtomicInteger(1);
        jdbcTemplate.batchUpdate(
            "INSERT INTO dungeon_run_log (dungeon_run_id, log_order, text) VALUES (?, ?, ?)",
            logs,
            200,
            (ps, log) -> {
                ps.setLong(1, runId);
                ps.setInt(2, order.getAndIncrement());
                ps.setString(3, log);
            }
        );
    }

    private void persistRunFrames(long runId, List<BattleFrame> frames) {
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO dungeon_run_frame
            (dungeon_run_id, frame_index, text, tone, room_label, enemy_name,
             player_hp, player_max_hp, enemy_hp, enemy_max_hp, actor, event_type, damage, critical, missed,
             skill_id, skill_name, visual_key, target_side, effect_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            frames,
            200,
            (ps, frame) -> {
                ps.setLong(1, runId);
                ps.setInt(2, frame.index());
                ps.setString(3, frame.text());
                ps.setString(4, frame.tone());
                ps.setString(5, frame.roomLabel());
                ps.setString(6, frame.enemyName());
                ps.setInt(7, frame.playerHp());
                ps.setInt(8, frame.playerMaxHp());
                ps.setInt(9, frame.enemyHp());
                ps.setInt(10, frame.enemyMaxHp());
                ps.setString(11, frame.actor());
                ps.setString(12, frame.eventType());
                ps.setInt(13, frame.damage());
                ps.setBoolean(14, frame.critical());
                ps.setBoolean(15, frame.missed());
                ps.setString(16, frame.skillId());
                ps.setString(17, frame.skillName());
                ps.setString(18, frame.visualKey());
                ps.setString(19, frame.targetSide());
                ps.setInt(20, frame.effectValue());
            }
        );
    }

    private List<ItemRecord> loadRunLoot(long runId, long playerId) {
        return jdbcTemplate.query(
            """
            SELECT *
            FROM dungeon_run_loot
            WHERE dungeon_run_id = ?
            ORDER BY loot_order
            """,
            (rs, rowNum) -> new ItemRecord(
                rs.getObject("item_id") == null ? -rs.getLong("loot_order") : rs.getLong("item_id"),
                playerId,
                rs.getString("item_template_id"),
                rs.getString("item_name"),
                rs.getString("item_type"),
                rs.getString("quality"),
                rs.getInt("required_level"),
                rs.getInt("attack_bonus"),
                rs.getInt("defense_bonus"),
                rs.getInt("resistance_bonus"),
                rs.getInt("hp_bonus"),
                rs.getInt("mp_bonus"),
                rs.getBigDecimal("crit_bonus"),
                rs.getInt("sell_price"),
                rs.getInt("enhancement_level"),
                rs.getInt("enhancement_luck")
            ),
            runId
        );
    }

    private List<String> loadRunLogs(long runId) {
        return jdbcTemplate.queryForList(
            """
            SELECT text
            FROM dungeon_run_log
            WHERE dungeon_run_id = ?
            ORDER BY log_order
            """,
            String.class,
            runId
        );
    }

    private List<BattleFrame> loadRunFrames(long runId) {
        return jdbcTemplate.query(
            """
            SELECT *
            FROM dungeon_run_frame
            WHERE dungeon_run_id = ?
            ORDER BY frame_index
            """,
            (rs, rowNum) -> new BattleFrame(
                rs.getInt("frame_index"),
                rs.getString("text"),
                rs.getString("tone"),
                rs.getString("room_label"),
                rs.getString("enemy_name"),
                rs.getInt("player_hp"),
                rs.getInt("player_max_hp"),
                rs.getInt("enemy_hp"),
                rs.getInt("enemy_max_hp"),
                rs.getString("actor"),
                rs.getString("event_type"),
                rs.getInt("damage"),
                rs.getBoolean("critical"),
                rs.getBoolean("missed"),
                rs.getString("skill_id"),
                rs.getString("skill_name"),
                rs.getString("visual_key"),
                rs.getString("target_side"),
                rs.getInt("effect_value")
            ),
            runId
        );
    }

    private record StoredRun(
        long id,
        String dungeonId,
        boolean success,
        String rating,
        int monstersKilled,
        int expGained,
        int goldGained,
        int combatPower,
        int recommendedPower,
        int playerMaxHp,
        int playerFinalHp
    ) {
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.trim();
    }

    public record DungeonRunResult(
        String dungeonId,
        String dungeonName,
        boolean success,
        String rating,
        int monstersKilled,
        int expGained,
        int goldGained,
        List<ItemRecord> loot,
        PlayerRecord player,
        int combatPower,
        List<String> logs,
        int recommendedPower,
        int playerMaxHp,
        int playerFinalHp,
        List<BattleFrame> frames,
        StaminaSnapshot stamina
    ) {
    }

    public record BattleFrame(
        int index,
        String text,
        String tone,
        String roomLabel,
        String enemyName,
        int playerHp,
        int playerMaxHp,
        int enemyHp,
        int enemyMaxHp,
        String actor,
        String eventType,
        int damage,
        boolean critical,
        boolean missed,
        String skillId,
        String skillName,
        String visualKey,
        String targetSide,
        int effectValue
    ) {
    }

    public record DungeonSweepResult(
        String dungeonId,
        String dungeonName,
        int times,
        int monstersKilled,
        int expGained,
        int goldGained,
        List<ItemRecord> loot,
        PlayerRecord player,
        int combatPower,
        List<String> logs,
        StaminaSnapshot stamina
    ) {
    }

    public record DungeonProgressPreview(
        String id,
        String name,
        String description,
        String difficulty,
        int recommendedLevel,
        int recommendedPower,
        int minimumLevel,
        int minimumPower,
        String bossArchetype,
        int expectedRounds,
        List<GameConfigService.DropPreview> drops,
        boolean cleared,
        EncounterGate.GateStatus gate,
        StaminaSnapshot stamina,
        int normalSweepTickets,
        int specialSweepTickets
    ) {
        static DungeonProgressPreview from(DungeonPreview preview, boolean cleared) {
            return from(
                preview,
                cleared,
                new EncounterGate.GateStatus(false, preview.minimumLevel(), preview.minimumPower(), "登录后查看"),
                null,
                0,
                0
            );
        }

        static DungeonProgressPreview from(DungeonPreview preview, boolean cleared, EncounterGate.GateStatus gate) {
            return from(preview, cleared, gate, null, 0, 0);
        }

        static DungeonProgressPreview from(
            DungeonPreview preview,
            boolean cleared,
            EncounterGate.GateStatus gate,
            StaminaSnapshot stamina,
            int normalSweepTickets,
            int specialSweepTickets
        ) {
            return new DungeonProgressPreview(
                preview.id(),
                preview.name(),
                preview.description(),
                preview.difficulty(),
                preview.recommendedLevel(),
                preview.recommendedPower(),
                preview.minimumLevel(),
                preview.minimumPower(),
                preview.bossArchetype(),
                preview.expectedRounds(),
                preview.drops(),
                cleared,
                gate,
                stamina,
                normalSweepTickets,
                specialSweepTickets
            );
        }
    }
}
