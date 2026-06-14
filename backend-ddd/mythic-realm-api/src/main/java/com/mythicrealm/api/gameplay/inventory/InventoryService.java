package com.mythicrealm.api.gameplay.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.combat.CombatPowerService;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final List<String> EQUIPMENT_SLOT_ORDER = List.of(
        "weapon",
        "helmet",
        "armor",
        "legs",
        "boots",
        "gloves",
        "necklace",
        "ring1",
        "ring2"
    );

    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final AnnouncementService announcementService;
    private final CombatStatsService combatStatsService;
    private final CombatPowerService combatPowerService;
    private final StaminaService staminaService;
    private final ObjectMapper objectMapper;

    public InventoryService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        AnnouncementService announcementService,
        CombatStatsService combatStatsService,
        CombatPowerService combatPowerService,
        StaminaService staminaService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.announcementService = announcementService;
        this.combatStatsService = combatStatsService;
        this.combatPowerService = combatPowerService;
        this.staminaService = staminaService;
        this.objectMapper = objectMapper;
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
                 defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, quantity)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.setInt(9, template.resistanceBonus() + roll(random, randomRange));
            ps.setInt(10, template.hpBonus());
            ps.setInt(11, template.mpBonus());
            ps.setBigDecimal(12, template.critBonus());
            ps.setInt(13, template.sellPrice());
            ps.setInt(14, 1);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public ItemRecord addLootToInventory(long playerId, ItemTemplate template, Random random) {
        if (template.stackable()) {
            return addStackableItem(playerId, template, 1);
        }
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
        return grantItem(playerId, templateId, 1, random).getFirst();
    }

    public List<ItemRecord> grantItem(long playerId, String templateId, int amount, Random random) {
        ItemTemplate template = gameConfigService.requireItem(templateId);
        int count = Math.max(1, amount);
        if (template.stackable()) {
            return List.of(addStackableItem(playerId, template, count));
        }
        List<ItemRecord> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(addLootToInventory(playerId, template, random));
        }
        return result;
    }

    private ItemRecord addStackableItem(long playerId, ItemTemplate template, int amount) {
        int count = Math.max(1, amount);
        Optional<ItemRecord> existing = findInventoryStack(playerId, template.id());
        if (existing.isPresent()) {
            ItemRecord stack = existing.get();
            jdbcTemplate.update(
                "UPDATE item_instance SET quantity = LEAST(?, quantity + ?) WHERE id = ? AND player_id = ?",
                Math.max(1, template.maxStack()),
                count,
                stack.id(),
                playerId
            );
            return requireItem(stack.id());
        }
        int slot = nextFreeSlot(playerId);
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO item_instance
                (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
                 defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, quantity)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, template.id());
            ps.setString(3, template.name());
            ps.setString(4, template.type());
            ps.setString(5, template.quality());
            ps.setInt(6, template.requiredLevel());
            ps.setInt(7, template.sellPrice());
            ps.setInt(8, Math.min(count, Math.max(1, template.maxStack())));
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

    private Optional<ItemRecord> findInventoryStack(long playerId, String templateId) {
        return jdbcTemplate.query(
            """
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ? AND ii.template_id = ? AND it.stackable = TRUE
            ORDER BY s.slot_index
            LIMIT 1
            """,
            (rs, rowNum) -> mapItem(rs),
            playerId,
            templateId
        ).stream().findFirst();
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
        int resistanceBonus,
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
                 defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, quantity, enhancement_level, enhancement_luck)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
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
            ps.setInt(9, Math.max(0, resistanceBonus));
            ps.setInt(10, Math.max(0, hpBonus));
            ps.setInt(11, Math.max(0, mpBonus));
            ps.setBigDecimal(12, BigDecimal.valueOf(Math.max(0, critBonus)));
            ps.setInt(13, Math.max(1, sellPrice));
            ps.setInt(14, Math.max(0, enhancementLevel));
            ps.setInt(15, Math.max(0, enhancementLuck));
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
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
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
                SELECT es.slot_name, ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                       it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level
                FROM equipment_slot es
                JOIN item_instance ii ON ii.id = es.item_id
                JOIN item_template it ON it.id = ii.template_id
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
        if (!item.equipment()) {
            throw ApiException.badRequest("Only equipment can be worn");
        }
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
    public InventorySnapshot equipBest(PlayerRecord player) {
        List<ItemRecord> candidates = new ArrayList<>(inventoryItems(player.id()).stream().filter(ItemRecord::equipment).toList());
        candidates.addAll(equippedItems(player.id()).values());
        if (candidates.isEmpty()) {
            return snapshot(player);
        }
        Map<String, ItemRecord> bestBySlot = bestEquipmentSet(candidates);
        Set<Long> equippedIds = bestBySlot.values().stream()
            .map(ItemRecord::id)
            .collect(Collectors.toSet());

        jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ?", player.id());
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ?", player.id());
        for (var entry : bestBySlot.entrySet()) {
            jdbcTemplate.update(
                "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
                player.id(),
                entry.getKey(),
                entry.getValue().id()
            );
        }

        List<ItemRecord> remaining = candidates.stream()
            .filter(item -> !equippedIds.contains(item.id()))
            .sorted(sortComparator("quality"))
            .toList();
        for (int i = 0; i < remaining.size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
                player.id(),
                i,
                remaining.get(i).id()
            );
        }
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
        int gainedGold = item.sellPrice() * Math.max(1, item.quantity());
        jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", gainedGold, player.id());
        return snapshot(new PlayerRecord(
            player.id(),
            player.accountId(),
            player.name(),
            player.profession(),
            player.level(),
            player.experience(),
            player.gold() + gainedGold,
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
        return enhance(player, itemId, List.of());
    }

    @Transactional
    public EnhanceResult enhance(PlayerRecord player, long itemId, List<Long> stoneItemIds) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        if (!item.equipment()) {
            throw ApiException.badRequest("Only equipment can be enhanced");
        }
        if (item.enhancementLevel() >= 15) {
            throw ApiException.badRequest("装备已强化到上限");
        }
        int targetLevel = item.enhancementLevel() + 1;
        List<Long> selectedStones = stoneItemIds == null ? List.of() : stoneItemIds.stream().filter(id -> id != null && id > 0).limit(4).toList();
        if (selectedStones.size() > 3) {
            throw ApiException.badRequest("At most three enhancement stones can be used");
        }
        double stoneBonus = 0;
        for (long stoneItemId : selectedStones) {
            ItemRecord stone = requireOwnedItem(player.id(), stoneItemId);
            inventorySlot(player.id(), stone.id()).orElseThrow(() -> ApiException.badRequest("Enhancement stones must be in the inventory"));
            if (!"enhancementStone".equals(stone.effectType())) {
                throw ApiException.badRequest(stone.name() + " is not an enhancement stone");
            }
            if (targetLevel < stone.minEnhanceLevel() || targetLevel > stone.maxEnhanceLevel()) {
                throw ApiException.badRequest(stone.name() + " cannot be used for +" + targetLevel);
            }
            stoneBonus += Math.max(0, stone.enhanceBonusRate());
        }
        int cost = Math.max(1, item.requiredLevel()) * Math.max(1, item.requiredLevel()) * targetLevel * 10;
        if (player.gold() < cost) {
            throw ApiException.badRequest("金币不足，需要 " + cost + " 金");
        }
        double chance = Math.min(0.95, baseSuccessRate(targetLevel) + item.enhancementLuck() * 0.05 + stoneBonus);
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
        for (long stoneItemId : selectedStones) {
            consumeOne(player.id(), stoneItemId);
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
        if (success) {
            announcementService.publishEnhancementMilestone(player.name(), item.name(), nextLevel);
        }
        return new EnhanceResult(success, cost, chance, selectedStones.size(), stoneBonus, nextLevel, snapshot(updatedPlayer));
    }

    @Transactional
    public UseItemResult useItem(PlayerRecord player, long itemId) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        inventorySlot(player.id(), itemId).orElseThrow(() -> ApiException.badRequest("Item must be in the inventory"));
        if (item.effectType() == null || item.effectType().isBlank()) {
            throw ApiException.badRequest("This item cannot be used");
        }

        if ("staminaPotion".equals(item.effectType())) {
            int amount = effectInt(item, "amount", 0);
            if (amount <= 0) {
                throw ApiException.badRequest("Stamina potion is missing an amount");
            }
            consumeOne(player.id(), item.id());
            StaminaSnapshot stamina = staminaService.add(player.id(), amount);
            return new UseItemResult(item.name(), "staminaPotion", List.of(), stamina, snapshot(playerById(player.id())));
        }

        if ("attributePotion".equals(item.effectType())) {
            JsonNode effect = effectNode(item);
            String attribute = effect.path("attribute").asText("");
            int amount = effect.path("amount").asInt(0);
            int maxLevel = effect.path("maxLevel").asInt(90);
            if (amount <= 0 || attribute.isBlank()) {
                throw ApiException.badRequest("Attribute potion is missing an effect");
            }
            if (player.level() > maxLevel) {
                throw ApiException.badRequest("This potion can only be used up to level " + maxLevel);
            }
            String column = attributeColumn(attribute);
            jdbcTemplate.update("UPDATE player SET " + column + " = " + column + " + ? WHERE id = ?", amount, player.id());
            consumeOne(player.id(), item.id());
            return new UseItemResult(item.name(), "attributePotion", List.of(), staminaService.snapshot(player.id()), snapshot(playerById(player.id())));
        }

        if ("chest".equals(item.effectType())) {
            ChestLoot loot = rollChestLoot(item.templateId(), new Random(System.nanoTime() + item.id()));
            consumeOne(player.id(), item.id());
            List<ItemRecord> rewards = grantItem(player.id(), loot.rewardTemplateId(), loot.quantity(), new Random(System.nanoTime() + loot.rewardTemplateId().hashCode()));
            return new UseItemResult(item.name(), "chest", rewards, staminaService.snapshot(player.id()), snapshot(playerById(player.id())));
        }

        throw ApiException.badRequest("This item effect is not implemented: " + item.effectType());
    }

    @Transactional
    public CraftResult craftRecipe(PlayerRecord player, String recipeId) {
        CraftRecipe recipe = requireRecipe(recipeId);
        if (player.level() < recipe.requiredLevel()) {
            throw ApiException.badRequest("Recipe requires level " + recipe.requiredLevel());
        }
        List<CraftCost> costs = recipeCosts(recipeId);
        if (costs.isEmpty()) {
            throw ApiException.badRequest("Recipe has no material costs");
        }
        for (CraftCost cost : costs) {
            int owned = stackableQuantity(player.id(), cost.itemTemplateId());
            if (owned < cost.quantity()) {
                throw ApiException.badRequest("Missing material " + cost.itemTemplateId() + ": " + owned + "/" + cost.quantity());
            }
        }
        for (CraftCost cost : costs) {
            consumeQuantityByTemplate(player.id(), cost.itemTemplateId(), cost.quantity());
        }
        List<ItemRecord> rewards = grantItem(player.id(), recipe.resultTemplateId(), recipe.resultQuantity(), new Random(System.nanoTime() + recipe.id().hashCode()));
        return new CraftResult(recipe.id(), recipe.name(), rewards, snapshot(playerById(player.id())));
    }

    @Transactional
    public EnhancementTransferResult transferEnhancement(PlayerRecord player, long sourceItemId, long targetItemId) {
        if (sourceItemId == targetItemId) {
            throw ApiException.badRequest("来源装备和目标装备不能相同");
        }
        ItemRecord source = requireOwnedItem(player.id(), sourceItemId);
        ItemRecord target = requireOwnedItem(player.id(), targetItemId);
        if (source.enhancementLevel() <= 0) {
            throw ApiException.badRequest("来源装备没有可转移的强化等级");
        }
        if (source.enhancementLevel() <= target.enhancementLevel()) {
            throw ApiException.badRequest("目标装备强化等级不低于来源装备");
        }

        jdbcTemplate.update(
            """
            UPDATE item_instance
            SET enhancement_level = CASE WHEN id = ? THEN 0 WHEN id = ? THEN ? ELSE enhancement_level END,
                enhancement_luck = 0
            WHERE player_id = ? AND id IN (?, ?)
            """,
            sourceItemId,
            targetItemId,
            source.enhancementLevel(),
            player.id(),
            sourceItemId,
            targetItemId
        );
        return new EnhancementTransferResult(requireItem(sourceItemId), requireItem(targetItemId), snapshot(player));
    }

    @Transactional
    public BulkSellResult bulkSell(PlayerRecord player, List<String> qualities, List<String> itemTypes) {
        Set<String> qualitySet = qualities == null ? Set.of() : qualities.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        Set<String> typeSet = itemTypes == null ? Set.of() : itemTypes.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        List<ItemRecord> soldItems = inventoryItems(player.id()).stream()
            .filter(item -> qualitySet.isEmpty() || qualitySet.contains(item.quality()))
            .filter(item -> typeSet.isEmpty() || typeSet.contains(item.itemType()))
            .toList();
        int goldGained = soldItems.stream().mapToInt(item -> item.sellPrice() * Math.max(1, item.quantity())).sum();
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
        return combatPowerService.combatPower(
            combatStatsService.playerStats(player, equipment),
            combatStatsService.playerStats(player, List.of()),
            equipment.stream().mapToInt(this::equipmentPower).sum()
        );
    }

    public ItemRecord requireItem(long itemId) {
        return jdbcTemplate.query(
                """
                SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                       it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level
                FROM item_instance ii
                JOIN item_template it ON it.id = ii.template_id
                WHERE ii.id = ?
                """,
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

    private void consumeOne(long playerId, long itemId) {
        ItemRecord item = requireOwnedItem(playerId, itemId);
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("Item must be in the inventory"));
        if (item.stackable() && item.quantity() > 1) {
            jdbcTemplate.update("UPDATE item_instance SET quantity = quantity - 1 WHERE id = ? AND player_id = ?", itemId, playerId);
            return;
        }
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, itemId);
        jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", itemId, playerId);
    }

    private void consumeQuantityByTemplate(long playerId, String templateId, int quantity) {
        int remaining = Math.max(0, quantity);
        List<ItemRecord> stacks = jdbcTemplate.query(
            """
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ? AND ii.template_id = ?
            ORDER BY s.slot_index
            """,
            (rs, rowNum) -> mapItem(rs),
            playerId,
            templateId
        );
        for (ItemRecord stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            int consume = Math.min(remaining, Math.max(1, stack.quantity()));
            if (consume >= stack.quantity()) {
                jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, stack.id());
                jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", stack.id(), playerId);
            } else {
                jdbcTemplate.update("UPDATE item_instance SET quantity = quantity - ? WHERE id = ? AND player_id = ?", consume, stack.id(), playerId);
            }
            remaining -= consume;
        }
        if (remaining > 0) {
            throw ApiException.badRequest("Missing material " + templateId);
        }
    }

    private int stackableQuantity(long playerId, String templateId) {
        Integer total = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(ii.quantity), 0)
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            WHERE s.player_id = ? AND ii.template_id = ?
            """,
            Integer.class,
            playerId,
            templateId
        );
        return total == null ? 0 : total;
    }

    private JsonNode effectNode(ItemRecord item) {
        try {
            return objectMapper.readTree(item.effectValueJson() == null || item.effectValueJson().isBlank() ? "{}" : item.effectValueJson());
        } catch (Exception error) {
            throw ApiException.badRequest("Invalid item effect config for " + item.templateId());
        }
    }

    private int effectInt(ItemRecord item, String field, int defaultValue) {
        return effectNode(item).path(field).asInt(defaultValue);
    }

    private String attributeColumn(String attribute) {
        return switch (attribute) {
            case "strength", "agility", "constitution", "intelligence", "spirit" -> attribute;
            default -> throw ApiException.badRequest("Unsupported attribute: " + attribute);
        };
    }

    private ChestLoot rollChestLoot(String chestTemplateId, Random random) {
        List<ChestLootOption> options = jdbcTemplate.query(
            """
            SELECT reward_template_id, min_quantity, max_quantity, weight
            FROM chest_loot
            WHERE chest_template_id = ?
            """,
            (rs, rowNum) -> new ChestLootOption(
                rs.getString("reward_template_id"),
                rs.getInt("min_quantity"),
                rs.getInt("max_quantity"),
                Math.max(1, rs.getInt("weight"))
            ),
            chestTemplateId
        );
        if (options.isEmpty()) {
            throw ApiException.badRequest("Chest has no loot table: " + chestTemplateId);
        }
        int totalWeight = options.stream().mapToInt(ChestLootOption::weight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (ChestLootOption option : options) {
            cursor += option.weight();
            if (roll < cursor) {
                int min = Math.max(1, option.minQuantity());
                int max = Math.max(min, option.maxQuantity());
                int quantity = min + random.nextInt(max - min + 1);
                return new ChestLoot(option.rewardTemplateId(), quantity);
            }
        }
        ChestLootOption fallback = options.getLast();
        return new ChestLoot(fallback.rewardTemplateId(), Math.max(1, fallback.minQuantity()));
    }

    private CraftRecipe requireRecipe(String recipeId) {
        return jdbcTemplate.query(
            "SELECT id, name, result_template_id, result_quantity, required_level FROM craft_recipe WHERE id = ?",
            (rs, rowNum) -> new CraftRecipe(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("result_template_id"),
                rs.getInt("result_quantity"),
                rs.getInt("required_level")
            ),
            recipeId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("Recipe not found: " + recipeId));
    }

    private List<CraftCost> recipeCosts(String recipeId) {
        return jdbcTemplate.query(
            "SELECT item_template_id, quantity FROM craft_recipe_cost WHERE recipe_id = ?",
            (rs, rowNum) -> new CraftCost(rs.getString("item_template_id"), rs.getInt("quantity")),
            recipeId
        );
    }

    private PlayerRecord playerById(long playerId) {
        return jdbcTemplate.query(
            "SELECT * FROM player WHERE id = ?",
            (rs, rowNum) -> new PlayerRecord(
                rs.getLong("id"),
                rs.getObject("account_id") == null ? 0 : rs.getLong("account_id"),
                rs.getString("name"),
                rs.getString("profession"),
                rs.getInt("level"),
                rs.getInt("experience"),
                rs.getLong("gold"),
                rs.getLong("real_money"),
                rs.getInt("wealth_tier_level"),
                rs.getString("wealth_tier"),
                rs.getInt("strength"),
                rs.getInt("agility"),
                rs.getInt("constitution"),
                rs.getInt("intelligence"),
                rs.getInt("spirit"),
                rs.getInt("free_points")
            ),
            playerId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("Player not found"));
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

    private Map<String, ItemRecord> bestEquipmentSet(List<ItemRecord> candidates) {
        Map<String, ItemRecord> result = new LinkedHashMap<>();
        Comparator<ItemRecord> byPower = Comparator.comparingInt(this::equipmentPower).thenComparingLong(ItemRecord::id);
        for (String slot : EQUIPMENT_SLOT_ORDER) {
            if (slot.startsWith("ring")) {
                continue;
            }
            candidates.stream()
                .filter(ItemRecord::equipment)
                .filter(item -> slot.equals(equipSlotFor(item.itemType()).orElse(null)))
                .max(byPower)
                .ifPresent(item -> result.put(slot, item));
        }
        List<ItemRecord> bestRings = candidates.stream()
            .filter(ItemRecord::equipment)
            .filter(item -> "ring".equals(item.itemType()))
            .sorted(byPower.reversed())
            .limit(2)
            .toList();
        for (int i = 0; i < bestRings.size(); i++) {
            result.put(i == 0 ? "ring1" : "ring2", bestRings.get(i));
        }
        return result;
    }

    public int equipmentPower(ItemRecord item) {
        return Math.max(
            1,
            (int) Math.round(
                item.enhancedAttackBonus() * 45
                    + item.enhancedDefenseBonus() * 30
                    + item.enhancedResistanceBonus() * 30
                    + item.enhancedHpBonus() * 4.0
                    + item.enhancedMpBonus() * 2.0
                    + item.enhancedCritBonus() * 3000
                    + item.enhancementLevel() * 100
                    + item.requiredLevel() * 20
                    + qualityRank(item.quality()) * 60
            )
        );
    }

    public int qualityRank(String quality) {
        return switch (quality) {
            case "immortal" -> 6;
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
            rs.getString("item_category"),
            rs.getString("quality"),
            rs.getInt("required_level"),
            rs.getInt("attack_bonus"),
            rs.getInt("defense_bonus"),
            rs.getInt("resistance_bonus"),
            rs.getInt("hp_bonus"),
            rs.getInt("mp_bonus"),
            rs.getBigDecimal("crit_bonus"),
            rs.getInt("sell_price"),
            rs.getInt("quantity"),
            rs.getBoolean("stackable"),
            rs.getString("effect_type"),
            rs.getString("effect_value_json"),
            rs.getDouble("enhance_bonus_rate"),
            rs.getInt("min_enhance_level"),
            rs.getInt("max_enhance_level"),
            rs.getInt("enhancement_level"),
            rs.getInt("enhancement_luck")
        );
    }

    public record InventorySnapshot(
        List<ItemRecord> inventory,
        Map<String, ItemRecord> equippedItems,
        int combatPower,
        long gold,
        int capacity
    ) {
        @JsonProperty("items")
        public List<ItemRecord> items() {
            return inventory;
        }
    }

    public record EnhanceResult(
        boolean success,
        int cost,
        double chance,
        int usedStoneCount,
        double stoneBonus,
        int enhancementLevel,
        InventorySnapshot inventory
    ) {
    }

    public record BulkSellResult(int soldCount, int goldGained, InventorySnapshot inventory) {
    }

    public record EnhancementTransferResult(ItemRecord sourceItem, ItemRecord targetItem, InventorySnapshot inventory) {
    }

    public record UseItemResult(
        String itemName,
        String effectType,
        List<ItemRecord> rewards,
        StaminaSnapshot stamina,
        InventorySnapshot inventory
    ) {
    }

    public record CraftResult(String recipeId, String recipeName, List<ItemRecord> rewards, InventorySnapshot inventory) {
    }

    private record ChestLootOption(String rewardTemplateId, int minQuantity, int maxQuantity, int weight) {
    }

    private record ChestLoot(String rewardTemplateId, int quantity) {
    }

    private record CraftRecipe(String id, String name, String resultTemplateId, int resultQuantity, int requiredLevel) {
    }

    private record CraftCost(String itemTemplateId, int quantity) {
    }
}
