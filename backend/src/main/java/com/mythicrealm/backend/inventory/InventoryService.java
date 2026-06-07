package com.mythicrealm.backend.inventory;

import com.mythicrealm.backend.common.ApiException;
import com.mythicrealm.backend.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.backend.gameconfig.GameConfigService;
import com.mythicrealm.backend.player.PlayerRecord;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    public static final int MAX_SLOTS = 1000;
    private static final List<String> STARTER_TEMPLATE_IDS = List.of(
        "eq_t01_weapon_03",
        "eq_t01_helmet_02",
        "eq_t01_armor_03",
        "eq_t01_boots_02",
        "eq_t01_gloves_02"
    );

    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;

    public InventoryService(JdbcTemplate jdbcTemplate, GameConfigService gameConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
    }

    public void grantStarterEquipment(long playerId) {
        for (String templateId : STARTER_TEMPLATE_IDS) {
            ItemTemplate template = gameConfigService.requireItem(templateId);
            String slot = equipSlotFor(template.type()).orElseThrow();
            long itemId = createItem(playerId, template, new Random(templateId.hashCode()));
            jdbcTemplate.update(
                "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
                playerId,
                slot,
                itemId
            );
        }
    }

    public long createItem(long playerId, ItemTemplate template, Random random) {
        int randomRange = Math.max(0, template.randomRange());
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO item_instance
                (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
                 defense_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, template.id());
            ps.setString(3, template.name());
            ps.setString(4, template.type());
            ps.setString(5, template.quality());
            ps.setInt(6, template.requiredLevel());
            ps.setInt(7, template.attackBonus() + roll(random, randomRange));
            ps.setInt(8, template.defenseBonus() + roll(random, randomRange));
            ps.setInt(9, template.hpBonus());
            ps.setInt(10, template.mpBonus());
            ps.setBigDecimal(11, template.critBonus());
            ps.setInt(12, template.sellPrice());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public ItemRecord addLootToInventory(long playerId, ItemTemplate template, Random random) {
        int slot = nextFreeSlot(playerId);
        long itemId = createItem(playerId, template, random);
        jdbcTemplate.update(
            "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
            playerId,
            slot,
            itemId
        );
        return requireItem(itemId);
    }

    public ItemRecord addRewardItem(long playerId, String templateId, Random random) {
        return addLootToInventory(playerId, gameConfigService.requireItem(templateId), random);
    }

    public ItemRecord addMarketItemToInventory(
        long playerId,
        String templateId,
        String name,
        String itemType,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int hpBonus,
        int mpBonus,
        double critBonus,
        int sellPrice,
        int enhancementLevel,
        int enhancementLuck
    ) {
        int slot = nextFreeSlot(playerId);
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO item_instance
                (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
                 defense_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, enhancement_level, enhancement_luck)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, templateId);
            ps.setString(3, name);
            ps.setString(4, itemType);
            ps.setString(5, quality);
            ps.setInt(6, Math.max(1, requiredLevel));
            ps.setInt(7, Math.max(0, attackBonus));
            ps.setInt(8, Math.max(0, defenseBonus));
            ps.setInt(9, Math.max(0, hpBonus));
            ps.setInt(10, Math.max(0, mpBonus));
            ps.setBigDecimal(11, BigDecimal.valueOf(Math.max(0, critBonus)));
            ps.setInt(12, Math.max(1, sellPrice));
            ps.setInt(13, Math.max(0, enhancementLevel));
            ps.setInt(14, Math.max(0, enhancementLuck));
            return ps;
        }, keyHolder);
        long itemId = keyHolder.getKey().longValue();
        jdbcTemplate.update(
            "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
            playerId,
            slot,
            itemId
        );
        return requireItem(itemId);
    }

    public List<ItemRecord> inventoryItems(long playerId) {
        return jdbcTemplate.query(
            """
            SELECT ii.*
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            WHERE s.player_id = ?
            ORDER BY s.slot_index
            """,
            (rs, rowNum) -> mapItem(rs),
            playerId
        );
    }

    public Map<String, ItemRecord> equippedItems(long playerId) {
        return jdbcTemplate.query(
                """
                SELECT es.slot_name, ii.*
                FROM equipment_slot es
                JOIN item_instance ii ON ii.id = es.item_id
                WHERE es.player_id = ?
                ORDER BY es.slot_name
                """,
                (rs, rowNum) -> Map.entry(rs.getString("slot_name"), mapItem(rs)),
                playerId
            )
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional
    public InventorySnapshot snapshot(PlayerRecord player) {
        return new InventorySnapshot(
            inventoryItems(player.id()),
            equippedItems(player.id()),
            combatPower(player),
            player.gold(),
            MAX_SLOTS
        );
    }

    @Transactional
    public InventorySnapshot equip(PlayerRecord player, long itemId) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        String targetSlot = equipSlotForItem(player.id(), item);
        Integer sourceSlot = inventorySlot(player.id(), itemId)
            .orElseThrow(() -> ApiException.badRequest("只能穿戴背包中的装备"));
        List<Long> oldEquipped = jdbcTemplate.queryForList(
            "SELECT item_id FROM equipment_slot WHERE player_id = ? AND slot_name = ?",
            Long.class,
            player.id(),
            targetSlot
        );
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND slot_index = ?", player.id(), sourceSlot);
        if (!oldEquipped.isEmpty()) {
            jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ? AND slot_name = ?", player.id(), targetSlot);
            jdbcTemplate.update(
                "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
                player.id(),
                nextFreeSlot(player.id()),
                oldEquipped.get(0)
            );
        }
        jdbcTemplate.update(
            "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
            player.id(),
            targetSlot,
            itemId
        );
        return snapshot(player);
    }

    @Transactional
    public InventorySnapshot unequip(PlayerRecord player, long itemId) {
        requireOwnedItem(player.id(), itemId);
        List<String> equippedSlots = jdbcTemplate.queryForList(
            "SELECT slot_name FROM equipment_slot WHERE player_id = ? AND item_id = ?",
            String.class,
            player.id(),
            itemId
        );
        if (equippedSlots.isEmpty()) {
            throw ApiException.badRequest("只能下架已穿戴的装备");
        }
        int targetInventorySlot = nextFreeSlot(player.id());
        jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ? AND item_id = ?", player.id(), itemId);
        jdbcTemplate.update(
            "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
            player.id(),
            targetInventorySlot,
            itemId
        );
        return snapshot(player);
    }

    @Transactional
    public InventorySnapshot sell(PlayerRecord player, long itemId) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        inventorySlot(player.id(), itemId).orElseThrow(() -> ApiException.badRequest("只能出售背包中的装备"));
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", player.id(), itemId);
        jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", itemId, player.id());
        jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", item.sellPrice(), player.id());
        return snapshot(new PlayerRecord(
            player.id(),
            player.accountId(),
            player.name(),
            player.profession(),
            player.level(),
            player.experience(),
            player.gold() + item.sellPrice(),
            player.strength(),
            player.agility(),
            player.constitution(),
            player.intelligence(),
            player.spirit(),
            player.freePoints()
        ));
    }

    @Transactional
    public EnhanceResult enhance(PlayerRecord player, long itemId) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        if (item.enhancementLevel() >= 15) {
            throw ApiException.badRequest("装备已强化到上限");
        }
        int targetLevel = item.enhancementLevel() + 1;
        int cost = Math.max(1, item.requiredLevel()) * Math.max(1, item.requiredLevel()) * targetLevel * 10;
        if (player.gold() < cost) {
            throw ApiException.badRequest("金币不足，需要 " + cost + " 金");
        }
        double chance = Math.min(1.0, baseSuccessRate(targetLevel) + item.enhancementLuck() * 0.05);
        boolean success = new Random(System.nanoTime() + itemId).nextDouble() <= chance;
        int nextLevel = item.enhancementLevel();
        int nextLuck = item.enhancementLuck();
        if (success) {
            nextLevel++;
            nextLuck = 0;
        } else {
            nextLuck++;
            if (targetLevel >= 7 && targetLevel <= 12) {
                nextLevel = Math.max(0, nextLevel - 1);
            } else if (targetLevel >= 13) {
                nextLevel = Math.max(0, nextLevel - 2);
            }
        }
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", cost, player.id());
        jdbcTemplate.update(
            "UPDATE item_instance SET enhancement_level = ?, enhancement_luck = ? WHERE id = ? AND player_id = ?",
            nextLevel,
            nextLuck,
            itemId,
            player.id()
        );
        PlayerRecord updatedPlayer = new PlayerRecord(
            player.id(),
            player.accountId(),
            player.name(),
            player.profession(),
            player.level(),
            player.experience(),
            player.gold() - cost,
            player.strength(),
            player.agility(),
            player.constitution(),
            player.intelligence(),
            player.spirit(),
            player.freePoints()
        );
        return new EnhanceResult(success, cost, chance, snapshot(updatedPlayer));
    }

    @Transactional
    public BulkSellResult bulkSell(PlayerRecord player, List<String> qualities, List<String> itemTypes) {
        Set<String> qualitySet = qualities == null ? Set.of() : qualities.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        Set<String> typeSet = itemTypes == null ? Set.of() : itemTypes.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        List<ItemRecord> soldItems = inventoryItems(player.id()).stream()
            .filter(item -> qualitySet.isEmpty() || qualitySet.contains(item.quality()))
            .filter(item -> typeSet.isEmpty() || typeSet.contains(item.itemType()))
            .toList();
        int goldGained = soldItems.stream().mapToInt(ItemRecord::sellPrice).sum();
        for (ItemRecord item : soldItems) {
            jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", player.id(), item.id());
            jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", item.id(), player.id());
        }
        if (goldGained > 0) {
            jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", goldGained, player.id());
        }
        PlayerRecord updatedPlayer = new PlayerRecord(
            player.id(),
            player.accountId(),
            player.name(),
            player.profession(),
            player.level(),
            player.experience(),
            player.gold() + goldGained,
            player.strength(),
            player.agility(),
            player.constitution(),
            player.intelligence(),
            player.spirit(),
            player.freePoints()
        );
        return new BulkSellResult(soldItems.size(), goldGained, snapshot(updatedPlayer));
    }

    @Transactional
    public InventorySnapshot organize(PlayerRecord player, String sort) {
        List<ItemRecord> sorted = inventoryItems(player.id()).stream()
            .sorted(sortComparator(sort))
            .toList();
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ?", player.id());
        for (int i = 0; i < sorted.size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
                player.id(),
                i,
                sorted.get(i).id()
            );
        }
        return snapshot(player);
    }

    public int combatPower(PlayerRecord player) {
        var equipment = equippedItems(player.id()).values();
        int equipmentAttack = equipment.stream().mapToInt(ItemRecord::enhancedAttackBonus).sum();
        int equipmentDefense = equipment.stream().mapToInt(ItemRecord::enhancedDefenseBonus).sum();
        int equipmentHp = equipment.stream().mapToInt(ItemRecord::enhancedHpBonus).sum();
        int equipmentMp = equipment.stream().mapToInt(ItemRecord::enhancedMpBonus).sum();
        double equipmentCrit = equipment.stream().mapToDouble(ItemRecord::enhancedCritBonus).sum();

        double attack = player.attack() + equipmentAttack;
        double defense = player.defense() + equipmentDefense;
        double hp = player.maxHp() + equipmentHp;
        double mp = player.maxMp() + equipmentMp;
        double critRate = Math.min(0.45, player.agility() * 0.001 + equipmentCrit);
        double equippedSlots = equipment.size();

        double offenseScore = attack * 12;
        double defenseScore = defense * 8;
        double healthScore = Math.sqrt(Math.max(1, hp)) * 26;
        double manaScore = Math.sqrt(Math.max(1, mp)) * 12;
        double critScore = offenseScore * critRate * 0.8;
        double levelScore = player.level() * 45.0;
        double slotSetBonus = 1 + Math.min(0.10, equippedSlots * 0.008);

        return (int) ((offenseScore + defenseScore + healthScore + manaScore + critScore + levelScore) * slotSetBonus);
    }

    public ItemRecord requireItem(long itemId) {
        return jdbcTemplate.query(
                "SELECT * FROM item_instance WHERE id = ?",
                (rs, rowNum) -> mapItem(rs),
                itemId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("装备不存在"));
    }

    public ItemRecord requireOwnedItem(long playerId, long itemId) {
        ItemRecord item = requireItem(itemId);
        if (item.playerId() != playerId) {
            throw ApiException.badRequest("装备不属于当前角色");
        }
        return item;
    }

    public int nextFreeSlot(long playerId) {
        Set<Integer> occupied = jdbcTemplate.queryForList(
                "SELECT slot_index FROM inventory_slot WHERE player_id = ?",
                Integer.class,
                playerId
            )
            .stream()
            .collect(Collectors.toSet());
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!occupied.contains(i)) {
                return i;
            }
        }
        throw ApiException.badRequest("背包已满");
    }

    private Optional<String> equipSlotFor(String itemType) {
        return switch (itemType) {
            case "weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace" -> Optional.of(itemType);
            case "ring" -> Optional.of("ring1");
            default -> Optional.empty();
        };
    }

    public Optional<String> equipSlot(String itemType) {
        return equipSlotFor(itemType);
    }

    public Optional<Integer> inventorySlot(long playerId, long itemId) {
        return jdbcTemplate.query(
            "SELECT slot_index FROM inventory_slot WHERE player_id = ? AND item_id = ?",
            (rs, rowNum) -> rs.getInt("slot_index"),
            playerId,
            itemId
        ).stream().findFirst();
    }

    public void removeFromInventory(long playerId, long itemId) {
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("装备不在背包中"));
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, itemId);
    }

    public void addExistingItemToInventory(long playerId, long itemId) {
        jdbcTemplate.update(
            "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
            playerId,
            nextFreeSlot(playerId),
            itemId
        );
    }

    public void transferItemOwner(long itemId, long playerId) {
        jdbcTemplate.update("UPDATE item_instance SET player_id = ? WHERE id = ?", playerId, itemId);
    }

    private String equipSlotForItem(long playerId, ItemRecord item) {
        Optional<String> baseSlot = equipSlotFor(item.itemType());
        if (baseSlot.isEmpty()) {
            throw ApiException.badRequest("该物品不可穿戴");
        }
        if (!"ring".equals(item.itemType())) {
            return baseSlot.get();
        }
        Map<String, ItemRecord> equipped = equippedItems(playerId);
        if (!equipped.containsKey("ring1")) {
            return "ring1";
        }
        if (!equipped.containsKey("ring2")) {
            return "ring2";
        }
        return "ring1";
    }

    private double baseSuccessRate(int targetLevel) {
        if (targetLevel <= 3) {
            return 1.0;
        }
        if (targetLevel <= 6) {
            return 0.8;
        }
        if (targetLevel <= 9) {
            return 0.6;
        }
        if (targetLevel <= 12) {
            return 0.4;
        }
        return 0.2;
    }

    private int roll(Random random, int randomRange) {
        return randomRange <= 0 ? 0 : random.nextInt(randomRange + 1);
    }

    private Comparator<ItemRecord> sortComparator(String sort) {
        Comparator<ItemRecord> byQuality = Comparator.comparingInt((ItemRecord item) -> qualityRank(item.quality())).reversed();
        Comparator<ItemRecord> byLevel = Comparator.comparingInt(ItemRecord::requiredLevel).reversed();
        Comparator<ItemRecord> byType = Comparator.comparing(ItemRecord::itemType);
        return switch (sort == null ? "quality" : sort) {
            case "level" -> byLevel.thenComparing(byQuality).thenComparing(ItemRecord::id);
            case "type" -> byType.thenComparing(byQuality).thenComparing(byLevel).thenComparing(ItemRecord::id);
            default -> byQuality.thenComparing(byLevel).thenComparing(byType).thenComparing(ItemRecord::id);
        };
    }

    private int qualityRank(String quality) {
        return switch (quality) {
            case "legendary" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            default -> 1;
        };
    }

    private ItemRecord mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ItemRecord(
            rs.getLong("id"),
            rs.getLong("player_id"),
            rs.getString("template_id"),
            rs.getString("name"),
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
        );
    }

    public record InventorySnapshot(
        List<ItemRecord> inventory,
        Map<String, ItemRecord> equippedItems,
        int combatPower,
        int gold,
        int capacity
    ) {
    }

    public record EnhanceResult(boolean success, int cost, double chance, InventorySnapshot inventory) {
    }

    public record BulkSellResult(int soldCount, int goldGained, InventorySnapshot inventory) {
    }
}
