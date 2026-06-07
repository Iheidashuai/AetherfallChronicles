package com.mythicrealm.api.gameplay.dungeon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DungeonService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GameConfigService gameConfigService;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final QuestService questService;
    private final String configVersion;

    public DungeonService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        GameConfigService gameConfigService,
        PlayerService playerService,
        InventoryService inventoryService,
        QuestService questService,
        @Value("${mythic.config.version}") String configVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.gameConfigService = gameConfigService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.questService = questService;
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
                    while (monsterHp > 0 && playerHp > 0 && round <= 18) {
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
                        if (random.nextDouble() <= lootEntry.dropRate()) {
                            ItemTemplate template = gameConfigService.requireItem(lootEntry.itemId());
                            ItemRecord item = inventoryService.addLootToInventory(player.id(), template, random);
                            loot.add(item);
                            if (isRareOrBetter(template.quality())) {
                                rareOrBetterLoot++;
                            }
                            appendFrame(
                                logs,
                                frames,
                                "获得装备：" + item.displayName(),
                                "loot",
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

    private int scaledMonsterHp(MonsterConfig monster, double pressure) {
        return (int) Math.max(1, monster.maxHP() * pressure);
    }

    private int scaledMonsterAttack(MonsterConfig monster, double pressure) {
        return (int) Math.max(1, monster.strength() * (0.9 + pressure * 0.45));
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
            case "rare", "epic", "legendary" -> true;
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
        var rows = jdbcTemplate.queryForList(
            "SELECT result_json FROM dungeon_run WHERE player_id = ? AND request_id = ?",
            String.class,
            playerId,
            requestId
        );
        if (rows.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(rows.get(0), DungeonRunResult.class);
        } catch (JsonProcessingException error) {
            throw ApiException.badRequest("历史副本结果读取失败");
        }
    }

    private void persistRun(long playerId, String dungeonId, String requestId, DungeonRunResult result) {
        String lootJson = writeJson(result.loot());
        String resultJson = writeJson(result);
        jdbcTemplate.update(
            """
            INSERT INTO dungeon_run
            (player_id, dungeon_id, config_version, request_id, success, rating, monsters_killed,
             exp_gained, gold_gained, loot_json, result_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            playerId,
            dungeonId,
            configVersion,
            requestId,
            result.success(),
            result.rating(),
            result.monstersKilled(),
            result.expGained(),
            result.goldGained(),
            lootJson,
            resultJson
        );
        jdbcTemplate.update(
            """
            INSERT INTO economy_audit_event (player_id, event_type, reference_id, payload_json)
            VALUES (?, 'DUNGEON_REWARD', ?, ?)
            """,
            playerId,
            requestId == null ? dungeonId : requestId,
            resultJson
        );
    }

    private void persistSweep(long playerId, String dungeonId, String requestId, DungeonSweepResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO economy_audit_event (player_id, event_type, reference_id, payload_json)
            VALUES (?, 'DUNGEON_SWEEP_REWARD', ?, ?)
            """,
            playerId,
            requestId == null ? dungeonId : requestId,
            writeJson(result)
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw ApiException.badRequest("结果序列化失败");
        }
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
