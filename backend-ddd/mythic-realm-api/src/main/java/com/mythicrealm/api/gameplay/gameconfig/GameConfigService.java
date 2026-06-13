package com.mythicrealm.api.gameplay.gameconfig;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.QuestConfig;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@DependsOn("flywayInitializer")
public class GameConfigService {
    private final JdbcTemplate jdbcTemplate;

    private List<ItemTemplate> itemTemplates = List.of();
    private List<MonsterConfig> monsters = List.of();
    private List<DungeonConfig> dungeons = List.of();
    private List<QuestConfig> quests = List.of();
    private Map<String, ItemTemplate> itemById = Map.of();
    private Map<String, MonsterConfig> monsterById = Map.of();
    private String checksum = "";
    private String configVersion = "";

    public GameConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        load();
    }

    public final void load() {
        itemTemplates = loadItems();
        monsters = loadMonsters();
        dungeons = loadDungeons();
        quests = loadQuests();
        itemById = itemTemplates.stream().collect(Collectors.toUnmodifiableMap(ItemTemplate::id, Function.identity()));
        monsterById = monsters.stream().collect(Collectors.toUnmodifiableMap(MonsterConfig::id, Function.identity()));
        var bundle = jdbcTemplate.query(
            "SELECT version, checksum FROM config_bundle WHERE active = TRUE ORDER BY id DESC LIMIT 1",
            (rs, rowNum) -> Map.entry(rs.getString("version"), rs.getString("checksum"))
        ).stream().findFirst();
        configVersion = bundle.map(Map.Entry::getKey).orElse("unknown");
        checksum = bundle.map(Map.Entry::getValue).orElse("");
        validateReferences();
    }

    public List<ItemTemplate> itemTemplates() {
        return itemTemplates;
    }

    public List<MonsterConfig> monsters() {
        return monsters;
    }

    public List<DungeonConfig> dungeons() {
        return dungeons;
    }

    public List<QuestConfig> quests() {
        return quests;
    }

    public ItemTemplate requireItem(String itemId) {
        ItemTemplate item = itemById.get(itemId);
        if (item == null) {
            throw ApiException.notFound("装备配置不存在: " + itemId);
        }
        return item;
    }

    public MonsterConfig requireMonster(String monsterId) {
        MonsterConfig monster = monsterById.get(monsterId);
        if (monster == null) {
            throw ApiException.notFound("怪物配置不存在: " + monsterId);
        }
        return monster;
    }

    public DungeonConfig requireDungeon(String dungeonId) {
        return dungeons.stream()
            .filter(dungeon -> dungeon.id().equals(dungeonId))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "副本不存在: " + dungeonId));
    }

    public ConfigSummary summary() {
        return new ConfigSummary(
            configVersion,
            checksum,
            itemTemplates.size(),
            monsters.size(),
            dungeons.size(),
            quests.size(),
            dungeonPreviews()
        );
    }

    public List<DungeonPreview> dungeonPreviews() {
        return dungeons.stream().map(this::toDungeonPreview).toList();
    }

    private void validateReferences() {
        for (MonsterConfig monster : monsters) {
            if (monster.lootTable() == null) {
                continue;
            }
            for (var loot : monster.lootTable()) {
                if (!itemById.containsKey(loot.itemId())) {
                    throw new IllegalStateException("Monster " + monster.id() + " references missing item " + loot.itemId());
                }
            }
        }

        for (DungeonConfig dungeon : dungeons) {
            for (var room : dungeon.rooms()) {
                for (var roomMonster : room.monsters()) {
                    if (!monsterById.containsKey(roomMonster.monsterId())) {
                        throw new IllegalStateException("Dungeon " + dungeon.id() + " references missing monster " + roomMonster.monsterId());
                    }
                }
            }
        }
    }

    private List<ItemTemplate> loadItems() {
        return jdbcTemplate.query(
            "SELECT * FROM item_template ORDER BY required_level, id",
            (rs, rowNum) -> new ItemTemplate(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("item_type"),
                rs.getString("quality"),
                rs.getInt("required_level"),
                rs.getInt("attack_bonus"),
                rs.getInt("defense_bonus"),
                rs.getInt("hp_bonus"),
                rs.getInt("mp_bonus"),
                rs.getBigDecimal("crit_bonus"),
                rs.getInt("random_range"),
                rs.getString("description"),
                rs.getInt("sell_price")
            )
        );
    }

    private List<MonsterConfig> loadMonsters() {
        return jdbcTemplate.query(
            "SELECT * FROM monster_config ORDER BY level, id",
            (rs, rowNum) -> {
                String monsterId = rs.getString("id");
                var loot = jdbcTemplate.query(
                    "SELECT item_id, drop_rate FROM monster_loot WHERE monster_id = ? ORDER BY item_id",
                    (lootRs, lootRow) -> new ConfigModels.LootEntry(
                        lootRs.getString("item_id"),
                        lootRs.getBigDecimal("drop_rate").doubleValue()
                    ),
                    monsterId
                );
                return new MonsterConfig(
                    monsterId,
                    rs.getString("name"),
                    rs.getInt("level"),
                    rs.getInt("max_hp"),
                    rs.getInt("strength"),
                    rs.getBoolean("is_boss"),
                    loot,
                    rs.getInt("exp_reward"),
                    rs.getInt("gold_reward")
                );
            }
        );
    }

    private List<DungeonConfig> loadDungeons() {
        return jdbcTemplate.query(
            "SELECT * FROM dungeon_config ORDER BY recommended_level, id",
            (rs, rowNum) -> {
                String dungeonId = rs.getString("id");
                var rooms = jdbcTemplate.query(
                    "SELECT * FROM dungeon_room WHERE dungeon_id = ? ORDER BY room_order",
                    (roomRs, roomRow) -> {
                        long roomId = roomRs.getLong("id");
                        var roomMonsters = jdbcTemplate.query(
                            "SELECT monster_id, monster_count FROM dungeon_room_monster WHERE room_id = ? ORDER BY id",
                            (monsterRs, monsterRow) -> new ConfigModels.RoomMonster(
                                monsterRs.getString("monster_id"),
                                monsterRs.getInt("monster_count")
                            ),
                            roomId
                        );
                        return new ConfigModels.DungeonRoom(
                            roomRs.getString("room_config_id"),
                            roomMonsters,
                            roomRs.getBoolean("is_boss_room")
                        );
                    },
                    dungeonId
                );
                return new DungeonConfig(
                    dungeonId,
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("difficulty"),
                    rooms,
                    rs.getInt("recommended_level"),
                    rs.getInt("recommended_power")
                );
            }
        );
    }

    private List<QuestConfig> loadQuests() {
        return jdbcTemplate.query(
            "SELECT * FROM quest_config ORDER BY priority, id",
            (rs, rowNum) -> {
                String questId = rs.getString("id");
                List<String> prerequisites = jdbcTemplate.queryForList(
                    "SELECT prerequisite_id FROM quest_prerequisite WHERE quest_id = ? ORDER BY prerequisite_id",
                    String.class,
                    questId
                );
                var conditions = jdbcTemplate.query(
                    "SELECT * FROM quest_condition WHERE quest_id = ? ORDER BY condition_order",
                    (conditionRs, conditionRow) -> new ConfigModels.QuestCondition(
                        conditionRs.getString("condition_id"),
                        conditionRs.getString("condition_type"),
                        conditionRs.getString("target_id"),
                        conditionRs.getInt("target_value")
                    ),
                    questId
                );
                var rewards = jdbcTemplate.query(
                    "SELECT * FROM quest_reward WHERE quest_id = ? ORDER BY reward_order",
                    (rewardRs, rewardRow) -> new ConfigModels.QuestReward(
                        rewardRs.getString("reward_type"),
                        rewardRs.getString("target_id"),
                        rewardRs.getInt("amount")
                    ),
                    questId
                );
                return new QuestConfig(
                    questId,
                    rs.getString("title"),
                    rs.getString("category"),
                    rs.getString("description"),
                    rs.getString("lore"),
                    rs.getInt("priority"),
                    rs.getString("navigation_target"),
                    prerequisites,
                    conditions,
                    rewards
                );
            }
        );
    }

    public record ConfigSummary(
        String version,
        String checksum,
        int itemCount,
        int monsterCount,
        int dungeonCount,
        int questCount,
        List<DungeonPreview> dungeons
    ) {
    }

    private DungeonPreview toDungeonPreview(DungeonConfig dungeon) {
        Map<String, Double> dropMissChanceByItem = new LinkedHashMap<>();
        for (var room : dungeon.rooms()) {
            for (var roomMonster : room.monsters()) {
                MonsterConfig monster = requireMonster(roomMonster.monsterId());
                if (monster.lootTable() == null) {
                    continue;
                }
                for (var loot : monster.lootTable()) {
                    double clampedRate = Math.max(0, Math.min(1, loot.dropRate()));
                    double missChance = Math.pow(1 - clampedRate, Math.max(1, roomMonster.count()));
                    dropMissChanceByItem.merge(loot.itemId(), missChance, (oldValue, nextValue) -> oldValue * nextValue);
                }
            }
        }

        List<DropPreview> drops = dropMissChanceByItem.entrySet().stream()
            .map(entry -> {
                ItemTemplate item = requireItem(entry.getKey());
                return new DropPreview(
                    item.id(),
                    item.name(),
                    item.type(),
                    item.quality(),
                    item.requiredLevel(),
                    item.attackBonus(),
                    item.defenseBonus(),
                    item.hpBonus(),
                    item.mpBonus(),
                    item.sellPrice(),
                    Math.round((1 - entry.getValue()) * 10000) / 10000.0
                );
            })
            .sorted(Comparator
                .comparingInt((DropPreview drop) -> qualityRank(drop.quality())).reversed()
                .thenComparingInt(DropPreview::requiredLevel)
                .thenComparing(DropPreview::name))
            .toList();

        return new DungeonPreview(
            dungeon.id(),
            dungeon.name(),
            dungeon.description(),
            dungeon.difficulty(),
            dungeon.recommendedLevel(),
            dungeon.recommendedPower(),
            drops
        );
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

    public record DungeonPreview(
        String id,
        String name,
        String description,
        String difficulty,
        int recommendedLevel,
        int recommendedPower,
        List<DropPreview> drops
    ) {
    }

    public record DropPreview(
        String templateId,
        String name,
        String itemType,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int hpBonus,
        int mpBonus,
        int sellPrice,
        double dropRate
    ) {
    }
}
