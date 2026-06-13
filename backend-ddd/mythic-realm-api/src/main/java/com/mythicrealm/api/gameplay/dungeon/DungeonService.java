package com.mythicrealm.api.gameplay.dungeon;

import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
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
    private final AnnouncementService announcementService;
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
        AnnouncementService announcementService,
        @Value("${mythic.config.version}") String configVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.questService = questService;
        this.announcementService = announcementService;
        this.configVersion = configVersion;
    }

    public List<DungeonProgressPreview> dungeonPreviews(long playerId) {
        Set<String> cleared = jdbcTemplate.queryForList(
                "SELECT DISTINCT dungeon_id FROM dungeon_run WHERE player_id = ? AND success = TRUE",
                String.class,
                playerId
            )
            .stream()
            .collect(Collectors.toSet());
        return gameConfigService.dungeonPreviews().stream()
            .map(preview -> DungeonProgressPreview.from(preview, cleared.contains(preview.id())))
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
        Random random = new Random(Objects.hash(player.id(), dungeonId, normalizedRequestId, System.nanoTime()));
        var logs = new ArrayList<String>();
        var frames = new ArrayList<BattleFrame>();
        var loot = new ArrayList<ItemRecord>();
        int expGained = 0;
        int goldGained = 0;
        int monstersKilled = 0;
        int rareOrBetterLoot = 0;
        var equipment = inventoryService.equippedItems(player.id()).values();
        int playerMaxHp = (int) Math.max(1, player.maxHp() + equipment.stream().mapToInt(ItemRecord::enhancedHpBonus).sum());
        int playerHp = Math.max(1, playerMaxHp);
        int playerAttack = (int) Math.max(1, player.attack() + equipment.stream().mapToInt(ItemRecord::enhancedAttackBonus).sum());
        int playerDefense = (int) Math.max(0, player.defense() + equipment.stream().mapToInt(ItemRecord::enhancedDefenseBonus).sum());
        double critRate = Math.min(0.45, player.agility() * 0.001 + equipment.stream().mapToDouble(ItemRecord::enhancedCritBonus).sum());
        double powerRatio = combatPower / (double) Math.max(1, dungeon.recommendedPower());
        double pressure = pressureMultiplier(powerRatio);
        boolean success = true;

        appendFrame(logs, frames, "进入副本【" + dungeon.name() + "】", "system", "准备", null, playerHp, playerMaxHp, 0, 0);
        appendFrame(logs, frames, "推荐战力 " + dungeon.recommendedPower() + "，当前战力 " + combatPower, "system", "准备", null, playerHp, playerMaxHp, 0, 0);
        appendFrame(
            logs,
            frames,
            powerRatio >= 1 ? "战力评估：压制区域，推进节奏稳定。" : "战力评估：危险越级，敌方压迫感明显增强。",
            powerRatio >= 1 ? "system" : "danger",
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
                "特殊副本规则：不开放扫荡，通关后只结算传说与不朽装备。",
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
                    int monsterHp = scaledMonsterHp(monster, pressure);
                    int monsterMaxHp = monsterHp;
                    int monsterAttack = scaledMonsterAttack(monster, pressure);
                    int roundLimit = roundLimit(monster);
                    int round = 1;
                    appendFrame(
                        logs,
                        frames,
                        "遭遇 " + monster.name() + " Lv." + monster.level() + "，生命 " + monsterHp,
                        monster.isBoss() ? "danger" : "system",
                        roomLabel,
                        monster.name(),
                        playerHp,
                        playerMaxHp,
                        monsterHp,
                        monsterMaxHp
                    );
                    while (monsterHp > 0 && playerHp > 0 && round <= roundLimit) {
                        boolean critical = random.nextDouble() < critRate;
                        int playerDamage = damageRoll(playerAttack, monster.strength(), critical, random);
                        monsterHp = Math.max(0, monsterHp - playerDamage);
                        appendFrame(
                            logs,
                            frames,
                            "第 " + round + " 回合：你" + (critical ? "打出暴击 " : "造成 ") + playerDamage + " 伤害，" + monster.name() + " 剩余 " + monsterHp,
                            critical ? "critical" : "hit",
                            roomLabel,
                            monster.name(),
                            playerHp,
                            playerMaxHp,
                            monsterHp,
                            monsterMaxHp
                        );
                        if (monsterHp <= 0) {
                            break;
                        }
                        int monsterDamage = damageRoll(monsterAttack, playerDefense, false, random);
                        playerHp = Math.max(0, playerHp - monsterDamage);
                        appendFrame(
                            logs,
                            frames,
                            "第 " + round + " 回合：" + monster.name() + " 反击 " + monsterDamage + " 伤害，你剩余 " + playerHp + "/" + playerMaxHp,
                            "enemy",
                            roomLabel,
                            monster.name(),
                            playerHp,
                            playerMaxHp,
                            monsterHp,
                            monsterMaxHp
                        );
                        round++;
                    }
                    if (monsterHp > 0 && playerHp > 0) {
                        int attritionDamage = Math.max(1, monsterAttack / 2);
                        playerHp = Math.max(0, playerHp - attritionDamage);
                        appendFrame(
                            logs,
                            frames,
                            "战斗拖入消耗，额外承受 " + attritionDamage + " 伤害。",
                            "danger",
                            roomLabel,
                            monster.name(),
                            playerHp,
                            playerMaxHp,
                            monsterHp,
                            monsterMaxHp
                        );
                    }
                    if (playerHp <= 0) {
                        success = false;
                        appendFrame(
                            logs,
                            frames,
                            "你在 " + monster.name() + " 面前倒下，副本推进中止。",
                            "danger",
                            roomLabel,
                            monster.name(),
                            playerHp,
                            playerMaxHp,
                            monsterHp,
                            monsterMaxHp
                        );
                        break battle;
                    }
                    if (monsterHp > 0) {
                        success = false;
                        appendFrame(
                            logs,
                            frames,
                            "久战不下，输出不足以击破 " + monster.name() + "，副本推进中止。",
                            "danger",
                            roomLabel,
                            monster.name(),
                            playerHp,
                            playerMaxHp,
                            monsterHp,
                            monsterMaxHp
                        );
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
                    if (monster.lootTable() == null) {
                        continue;
                    }
                    for (var lootEntry : monster.lootTable()) {
                        ItemTemplate template = gameConfigService.requireItem(lootEntry.itemId());
                        double dropRate = adjustedDropRate(lootEntry.dropRate(), template.quality(), specialDungeon, powerRatio);
                        if (random.nextDouble() <= dropRate) {
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
                                monsterMaxHp
                            );
                        }
                    }
                }
            }
            if (success && playerHp > 0 && roomIndex < dungeon.rooms().size() - 1) {
                int recover = Math.max(6, playerMaxHp / 8);
                playerHp = Math.min(playerMaxHp, playerHp + recover);
                appendFrame(logs, frames, "短暂整备，恢复 " + recover + " 生命，当前 " + playerHp + "/" + playerMaxHp, "heal", roomLabel, null, playerHp, playerMaxHp, 0, 0);
            }
        }
        if (success && specialDungeon) {
            rareOrBetterLoot += applySpecialPity(player.id(), dungeon, random, loot, logs, frames, playerHp, playerMaxHp);
        }

        PlayerRecord updatedPlayer = playerService.applyRewards(player.id(), expGained, goldGained);
        String rating = rating(success, powerRatio, playerHp, playerMaxHp);
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
            frames
        );
        persistRun(player.id(), dungeon.id(), normalizedRequestId, result);
        publishLegendaryLoot(player.name(), dungeon.name(), loot);
        if (success) {
            questService.recordEvent(player.id(), new QuestEvent("dungeonCompleted", dungeon.id(), 1));
        }
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
        DungeonConfig dungeon = gameConfigService.requireDungeon(dungeonId);
        if (isSpecialDungeon(dungeon.id())) {
            throw ApiException.badRequest("特殊副本不支持扫荡，请手动挑战");
        }
        if (!hasCleared(player.id(), dungeon.id())) {
            throw ApiException.badRequest("通关后才能扫荡该副本");
        }

        int sweepTimes = Math.max(1, Math.min(10, times));
        Random random = new Random(Objects.hash(player.id(), dungeonId, normalizeRequestId(requestId), System.nanoTime()));
        var logs = new ArrayList<String>();
        var loot = new ArrayList<ItemRecord>();
        int expGained = 0;
        int goldGained = 0;
        int monstersKilled = 0;
        int rareOrBetterLoot = 0;

        for (int sweepIndex = 1; sweepIndex <= sweepTimes; sweepIndex++) {
            int runExp = 0;
            int runGold = 0;
            int runLoot = 0;
            int runKills = 0;
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
            logs.add("第 " + sweepIndex + " 次扫荡：击败 " + runKills + " 只魔物，获得 " + runExp + " 经验 / " + runGold + " 金，掉落 " + runLoot + " 件装备。");
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
            logs
        );
        persistSweep(player.id(), dungeon.id(), normalizeRequestId(requestId), result);
        publishLegendaryLoot(player.name(), dungeon.name(), loot);
        questService.recordEvent(player.id(), new QuestEvent("dungeonCompleted", dungeon.id(), sweepTimes));
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

    private void publishLegendaryLoot(String playerName, String dungeonName, List<ItemRecord> loot) {
        for (ItemRecord item : loot) {
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

    private double adjustedDropRate(double baseDropRate, String quality, boolean specialDungeon, double powerRatio) {
        if (!specialDungeon || (!"legendary".equals(quality) && !"immortal".equals(quality))) {
            return baseDropRate;
        }
        double ratingBonus = powerRatio >= 1.25 ? 1.2 : 1.0;
        return Math.min(1.0, baseDropRate * ratingBonus);
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

    private int scaledMonsterHp(MonsterConfig monster, double pressure) {
        return (int) Math.max(1, monster.maxHP() * pressure);
    }

    private int scaledMonsterAttack(MonsterConfig monster, double pressure) {
        return (int) Math.max(1, monster.strength() * (0.9 + pressure * 0.45));
    }

    private int roundLimit(MonsterConfig monster) {
        return monster.isBoss() ? 26 : 18;
    }

    private int damageRoll(int attack, int defense, boolean critical, Random random) {
        double variance = 0.88 + random.nextDouble() * 0.24;
        double critMultiplier = critical ? 1.75 : 1.0;
        return (int) Math.max(1, (attack * critMultiplier - defense * 0.42) * variance);
    }

    private double pressureMultiplier(double powerRatio) {
        if (powerRatio >= 1.2) {
            return 0.85;
        }
        if (powerRatio >= 1.0) {
            return 1.0;
        }
        if (powerRatio >= 0.8) {
            return 1.25;
        }
        if (powerRatio >= 0.6) {
            return 1.75;
        }
        return 2.55;
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
            enemyMaxHp
        ));
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
            loadRunFrames(run.id())
        );
    }

    private void persistRun(long playerId, String dungeonId, String requestId, DungeonRunResult result) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO dungeon_run
                (player_id, dungeon_id, config_version, request_id, success, rating, monsters_killed,
                 exp_gained, gold_gained, combat_power, recommended_power, player_max_hp, player_final_hp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
             required_level, attack_bonus, defense_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price,
             enhancement_level, enhancement_luck)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ps.setInt(11, item.hpBonus());
                ps.setInt(12, item.mpBonus());
                ps.setBigDecimal(13, item.critBonus());
                ps.setInt(14, item.sellPrice());
                ps.setInt(15, item.enhancementLevel());
                ps.setInt(16, item.enhancementLuck());
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
             player_hp, player_max_hp, enemy_hp, enemy_max_hp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                rs.getInt("enemy_max_hp")
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
        List<BattleFrame> frames
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
        int enemyMaxHp
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
        List<String> logs
    ) {
    }

    public record DungeonProgressPreview(
        String id,
        String name,
        String description,
        String difficulty,
        int recommendedLevel,
        int recommendedPower,
        List<GameConfigService.DropPreview> drops,
        boolean cleared
    ) {
        static DungeonProgressPreview from(DungeonPreview preview, boolean cleared) {
            return new DungeonProgressPreview(
                preview.id(),
                preview.name(),
                preview.description(),
                preview.difficulty(),
                preview.recommendedLevel(),
                preview.recommendedPower(),
                preview.drops(),
                cleared
            );
        }
    }
}
