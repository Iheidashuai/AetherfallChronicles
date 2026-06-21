package com.mythicrealm.api.gameplay.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.combat.CombatPowerService;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.skill.SkillService;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    public static final int MAX_SLOTS = 1000;
    private static final int MAX_BULK_ACTIONS = 999;
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
    private static final List<String> LEVEL_BOOST_EQUIPMENT_SLOTS = List.of(
        "weapon",
        "helmet",
        "armor",
        "legs",
        "boots",
        "gloves",
        "necklace",
        "ring",
        "ring"
    );
    private static final List<String> PACKAGE_GEM_KINDS = List.of("ruby", "topaz", "emerald", "sapphire");
    private static final List<String> PACKAGE_AFFIX_STATS = List.of("attack", "crit", "hp");

    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final AnnouncementService announcementService;
    private final CombatStatsService combatStatsService;
    private final CombatPowerService combatPowerService;
    private final SkillService skillService;
    private final StaminaService staminaService;
    private final ObjectProvider<ItemEffectEngine> itemEffectEngineProvider;
    private final Object slotAllocationMonitor = new Object();

    public InventoryService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        AnnouncementService announcementService,
        CombatStatsService combatStatsService,
        CombatPowerService combatPowerService,
        SkillService skillService,
        StaminaService staminaService,
        ObjectProvider<ItemEffectEngine> itemEffectEngineProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.announcementService = announcementService;
        this.combatStatsService = combatStatsService;
        this.combatPowerService = combatPowerService;
        this.skillService = skillService;
        this.staminaService = staminaService;
        this.itemEffectEngineProvider = itemEffectEngineProvider;
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

    private long createItemInInventory(long playerId, ItemTemplate template, Random random) {
        synchronized (slotAllocationMonitor) {
            int slot = nextFreeSlot(playerId);
            long itemId = createItem(playerId, template, random);
            insertInventorySlot(playerId, slot, itemId);
            return itemId;
        }
    }

    private void addItemToNextFreeSlot(long playerId, long itemId) {
        synchronized (slotAllocationMonitor) {
            if (inventorySlot(playerId, itemId).isPresent()) {
                return;
            }
            for (int attempt = 0; attempt < 3; attempt++) {
                int slot = nextFreeSlot(playerId);
                try {
                    insertInventorySlot(playerId, slot, itemId);
                    return;
                } catch (DuplicateKeyException error) {
                    if (inventorySlot(playerId, itemId).isPresent()) {
                        return;
                    }
                    if (attempt == 2) {
                        throw error;
                    }
                }
            }
        }
    }

    private void insertInventorySlot(long playerId, int slot, long itemId) {
        jdbcTemplate.update(
            "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
            playerId,
            slot,
            itemId
        );
    }

    public ItemRecord addLootToInventory(long playerId, ItemTemplate template, Random random) {
        if (template.stackable()) {
            return addStackableItems(playerId, template, 1).getFirst();
        }
        long itemId = createItemInInventory(playerId, template, random);
        return requireItem(itemId);
    }

    public ItemRecord addRewardItem(long playerId, String templateId, Random random) {
        return grantItem(playerId, templateId, 1, random).getFirst();
    }

    public List<ItemRecord> grantItem(long playerId, String templateId, int amount, Random random) {
        ItemTemplate template = gameConfigService.requireItem(templateId);
        int count = Math.max(1, amount);
        if (template.stackable()) {
            return addStackableItems(playerId, template, count);
        }
        List<ItemRecord> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(addLootToInventory(playerId, template, random));
        }
        return result;
    }

    public ItemRecord grantSynthesizedChestGear(long playerId, String templateId, String slot, Random random) {
        ItemTemplate template = gameConfigService.requireItem(templateId);
        if (template.stackable()) {
            return addStackableItems(playerId, template, 1).getFirst();
        }
        int randomRange = Math.max(0, template.randomRange());
        return addMarketItemToInventory(
            playerId,
            template.id(),
            synthesizedChestGearName(template.name(), slot),
            template.type(),
            template.quality(),
            template.requiredLevel(),
            template.attackBonus() + roll(random, randomRange),
            template.defenseBonus() + roll(random, randomRange),
            template.resistanceBonus() + roll(random, randomRange),
            template.hpBonus(),
            template.mpBonus(),
            template.critBonus() == null ? 0 : template.critBonus().doubleValue(),
            template.sellPrice(),
            0,
            0
        );
    }

    static String synthesizedChestGearName(String baseName, String slot) {
        return switch (slot == null ? "" : slot) {
            case "ring1" -> baseName + "·左戒";
            case "ring2" -> baseName + "·右戒";
            default -> baseName;
        };
    }

    private List<ItemRecord> addStackableItems(long playerId, ItemTemplate template, int amount) {
        int count = Math.max(1, amount);
        synchronized (slotAllocationMonitor) {
            coalesceInventoryStacks(playerId);
            List<StackableStack> existingStacks = findInventoryStacks(playerId, template.id()).stream()
                .map(stack -> new StackableStack(stack.id(), stack.quantity()))
                .toList();
            List<StackableGrantStep> plan = planStackableGrant(existingStacks, count);
            List<Long> affectedItemIds = new ArrayList<>();
            for (StackableGrantStep step : plan) {
                if (step.itemId() == null) {
                    affectedItemIds.add(createStackableStack(playerId, template, step.quantity()));
                } else {
                    jdbcTemplate.update(
                        "UPDATE item_instance SET quantity = quantity + ? WHERE id = ? AND player_id = ?",
                        step.quantity(),
                        step.itemId(),
                        playerId
                    );
                    affectedItemIds.add(step.itemId());
                }
            }
            return affectedItemIds.stream().map(this::requireItem).toList();
        }
    }

    static List<StackableGrantStep> planStackableGrant(List<StackableStack> existingStacks, int amount) {
        int remaining = Math.max(1, amount);
        if (existingStacks != null && !existingStacks.isEmpty()) {
            return List.of(new StackableGrantStep(existingStacks.getFirst().itemId(), remaining));
        }
        return List.of(new StackableGrantStep(null, remaining));
    }

    public static boolean canTransferEquipmentProgressBetween(ItemRecord source, ItemRecord target) {
        return source.equipment()
            && target.equipment()
            && source.itemType().equals(target.itemType());
    }

    public static boolean hasTransferableEquipmentProgress(ItemRecord item) {
        return item.equipment()
            && (item.enhancementLevel() > 0
                || item.enhancementLuck() > 0
                || item.refineLevel() > 0
                || item.ascensionLevel() > 0
                || item.ascensionLuck() > 0
                || !item.sockets().isEmpty()
                || !item.affixes().isEmpty());
    }

    private long createStackableStack(long playerId, ItemTemplate template, int quantity) {
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
            ps.setInt(8, Math.max(1, quantity));
            return ps;
        }, keyHolder);
        long itemId = keyHolder.getKey().longValue();
        insertInventorySlot(playerId, slot, itemId);
        return itemId;
    }

    private List<ItemRecord> findInventoryStacks(long playerId, String templateId) {
        return jdbcTemplate.query(
            """
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level, it.description
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ? AND ii.template_id = ? AND it.stackable = TRUE
            ORDER BY s.slot_index
            """,
            (rs, rowNum) -> mapItem(rs),
            playerId,
            templateId
        );
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
        synchronized (slotAllocationMonitor) {
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
            insertInventorySlot(playerId, slot, itemId);
            return requireItem(itemId);
        }
    }

    public List<ItemRecord> inventoryItems(long playerId) {
        restoreOrphanedInventoryItems(playerId);
        coalesceInventoryStacks(playerId);
        return jdbcTemplate.query(
            """
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level, it.description
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
                       it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level, it.description
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
            addItemToNextFreeSlot(player.id(), oldEquipped.get(0));
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
        synchronized (slotAllocationMonitor) {
            List<ItemRecord> currentInventory = inventoryItems(player.id());
            List<ItemRecord> nonEquipmentInventory = currentInventory.stream()
                .filter(item -> !item.equipment())
                .toList();
            List<ItemRecord> candidates = new ArrayList<>(currentInventory.stream().filter(ItemRecord::equipment).toList());
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

            List<ItemRecord> remaining = new ArrayList<>(nonEquipmentInventory);
            remaining.addAll(candidates.stream()
                .filter(item -> !equippedIds.contains(item.id()))
                .sorted(sortComparator("quality"))
                .toList());
            for (int i = 0; i < remaining.size(); i++) {
                insertInventorySlot(player.id(), i, remaining.get(i).id());
            }
            return snapshot(player);
        }
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
        jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ? AND item_id = ?", player.id(), itemId);
        addItemToNextFreeSlot(player.id(), itemId);
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
        return useItem(player, itemId, 1);
    }

    @Transactional
    public UseItemResult useItem(PlayerRecord player, long itemId, int quantity) {
        int count = normalizeBulkActionQuantity(quantity);
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        inventorySlot(player.id(), itemId).orElseThrow(() -> ApiException.badRequest("Item must be in the inventory"));
        if ((item.effectType() == null || item.effectType().isBlank()) && !"chest".equals(item.itemCategory())) {
            throw ApiException.badRequest("This item cannot be used");
        }
        if (!item.stackable() && count > 1) {
            throw ApiException.badRequest("该物品不可批量使用");
        }
        if (item.stackable() && item.quantity() < count) {
            throw ApiException.badRequest("使用数量不足，当前只有 " + Math.max(1, item.quantity()) + " 件");
        }

        List<ItemRecord> rewards = new ArrayList<>();
        List<ItemEffectEvent> events = new ArrayList<>();
        StaminaSnapshot stamina = null;
        String effectType = "";
        String message = "";
        for (int i = 0; i < count; i++) {
            ItemRecord currentItem = requireOwnedItem(player.id(), itemId);
            inventorySlot(player.id(), itemId).orElseThrow(() -> ApiException.badRequest("Item must be in the inventory"));
            ItemEffectEngine.ApplyResult result = itemEffectEngineProvider.getObject().apply(playerById(player.id()), currentItem);
            effectType = result.effectType();
            message = result.message();
            rewards.addAll(result.rewards());
            events.addAll(result.events());
            stamina = result.stamina();
        }
        PlayerRecord updatedPlayer = playerById(player.id());
        InventorySnapshot inventory = snapshot(updatedPlayer);
        events.add(new ItemEffectEvent("combatPowerReached", null, inventory.combatPower()));
        return new UseItemResult(
            item.name(),
            effectType,
            count == 1 ? message : bulkUseMessage(item.name(), count, rewards),
            rewards,
            stamina,
            inventory,
            events,
            count
        );
    }

    void boostPlayerToLevel(PlayerRecord player, int targetLevel) {
        int delta = Math.max(0, targetLevel - player.level());
        int strength = player.strength() + delta;
        int agility = player.agility() + delta;
        int constitution = player.constitution() + delta;
        int intelligence = player.intelligence() + delta;
        int spirit = player.spirit() + delta;
        int freePoints = player.freePoints() + delta * 3;
        switch (player.profession()) {
            case "warrior" -> {
                strength += delta;
                constitution += delta;
            }
            case "ranger" -> {
                agility += delta;
                strength += delta;
            }
            case "mage" -> {
                intelligence += delta;
                spirit += delta;
            }
            default -> {
            }
        }
        jdbcTemplate.update(
            """
            UPDATE player
            SET level = ?, experience = 0, strength = ?, agility = ?, constitution = ?,
                intelligence = ?, spirit = ?, free_points = ?
            WHERE id = ?
            """,
            targetLevel,
            strength,
            agility,
            constitution,
            intelligence,
            spirit,
            freePoints,
            player.id()
        );
    }

    List<ItemRecord> ownedPlayableEquipment(long playerId) {
        return jdbcTemplate.query(
            """
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level, it.description
            FROM item_instance ii
            JOIN item_template it ON it.id = ii.template_id
            LEFT JOIN inventory_slot inv ON inv.item_id = ii.id AND inv.player_id = ii.player_id
            LEFT JOIN equipment_slot eq ON eq.item_id = ii.id AND eq.player_id = ii.player_id
            WHERE ii.player_id = ? AND it.item_category = 'equipment'
              AND (inv.item_id IS NOT NULL OR eq.item_id IS NOT NULL)
            ORDER BY ii.required_level DESC, ii.id
            """,
            (rs, rowNum) -> mapItem(rs),
            playerId
        );
    }

    List<ItemRecord> grantEquipmentPackage(PlayerRecord player, JsonNode effect, boolean professionNamed) {
        List<ItemRecord> rewards = new ArrayList<>();
        int equipmentLevel = effect.path("equipmentLevel").asInt(player.level());
        String quality = effect.path("equipmentQuality").asText("epic");
        int enhancementLevel = clamp(effect.path("enhancementLevel").asInt(0), 0, 15);
        int ringIndex = 0;
        for (String slot : LEVEL_BOOST_EQUIPMENT_SLOTS) {
            if ("ring".equals(slot)) {
                ringIndex++;
            }
            ItemTemplate template = gameConfigService.requireItem(equipmentPackageTemplateId(effect, slot, quality));
            EquipmentStats stats = professionAdjustedStats(template, player.profession());
            String itemName = professionNamed
                ? professionGearName(player.profession(), template.name(), slot, ringIndex)
                : equipmentPackageName(template.name(), slot, ringIndex);
            ItemRecord item = addMarketItemToInventory(
                player.id(),
                template.id(),
                itemName,
                template.type(),
                template.quality(),
                Math.max(equipmentLevel, template.requiredLevel()),
                stats.attackBonus(),
                stats.defenseBonus(),
                stats.resistanceBonus(),
                stats.hpBonus(),
                stats.mpBonus(),
                stats.critBonus(),
                template.sellPrice(),
                enhancementLevel,
                0
            );
            rewards.add(applyEquipmentPackageFinishing(player, effect, item, slot));
        }
        return rewards;
    }

    private ItemRecord applyEquipmentPackageFinishing(PlayerRecord player, JsonNode effect, ItemRecord item, String slot) {
        int refineLevel = clamp(effect.path("refineLevel").asInt(0), 0, 5);
        int ascensionLevel = clamp(effect.path("ascensionLevel").asInt(0), 0, 5);
        if (refineLevel > 0 || ascensionLevel > 0) {
            jdbcTemplate.update(
                """
                UPDATE item_instance
                SET refine_level = ?,
                    refine_focus = ?,
                    ascension_level = ?,
                    ascension_luck = 0
                WHERE id = ? AND player_id = ?
                """,
                refineLevel,
                effect.path("refineFocus").asText(packageRefineFocus(player.profession())),
                ascensionLevel,
                item.id(),
                player.id()
            );
            item = requireItem(item.id());
        }
        int gemRank = clamp(effect.path("socketGemRank").asInt(0), 0, 9);
        if (gemRank > 0) {
            fillPackageSockets(player.id(), item, slot, gemRank);
            item = requireItem(item.id());
        }
        int affixTier = clamp(effect.path("affixTier").asInt(effect.path("perfectAffixes").asBoolean(false) ? 5 : 0), 0, 5);
        if (affixTier > 0) {
            writePackageAffixes(item, affixTier);
            item = requireItem(item.id());
        }
        return item;
    }

    private String packageRefineFocus(String profession) {
        return switch (profession) {
            case "ranger" -> "crit";
            default -> "attack";
        };
    }

    private void fillPackageSockets(long playerId, ItemRecord item, String slot, int gemRank) {
        int socketLimit = packageSocketLimit(item);
        jdbcTemplate.update("DELETE FROM equipment_socket WHERE item_id = ?", item.id());
        for (int socketIndex = 0; socketIndex < socketLimit; socketIndex++) {
            String gemTemplateId = packageGemTemplate(slot, socketIndex, gemRank);
            long gemItemId = createDetachedPackageItem(playerId, gameConfigService.requireItem(gemTemplateId));
            jdbcTemplate.update(
                """
                INSERT INTO equipment_socket (item_id, socket_index, unlocked, gem_item_id)
                VALUES (?, ?, TRUE, ?)
                """,
                item.id(),
                socketIndex,
                gemItemId
            );
        }
    }

    private String packageGemTemplate(String slot, int socketIndex, int gemRank) {
        List<String> cycle = "weapon".equals(slot) || "gloves".equals(slot) || "necklace".equals(slot) || "ring".equals(slot)
            ? List.of("ruby", "topaz", "ruby", "topaz")
            : PACKAGE_GEM_KINDS;
        String kind = cycle.get(socketIndex % cycle.size());
        return "gem_" + kind + "_" + gemRank;
    }

    private void writePackageAffixes(ItemRecord item, int affixTier) {
        int affixLimit = packageAffixLimit(item);
        jdbcTemplate.update("DELETE FROM equipment_affix WHERE item_id = ?", item.id());
        for (int affixIndex = 0; affixIndex < affixLimit; affixIndex++) {
            String stat = PACKAGE_AFFIX_STATS.get(affixIndex % PACKAGE_AFFIX_STATS.size());
            jdbcTemplate.update(
                """
                INSERT INTO equipment_affix (item_id, affix_index, stat_key, stat_value, tier, locked)
                VALUES (?, ?, ?, ?, ?, FALSE)
                """,
                item.id(),
                affixIndex,
                stat,
                packageAffixValue(item, stat, affixTier),
                affixTier
            );
        }
    }

    private long createDetachedPackageItem(long playerId, ItemTemplate template) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO item_instance
                (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
                 defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, quantity,
                 enhancement_level, enhancement_luck, refine_level, refine_focus, ascension_level, ascension_luck)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 0, 0, 'balanced', 0, 0)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, template.id());
            ps.setString(3, template.name());
            ps.setString(4, template.type());
            ps.setString(5, template.quality());
            ps.setInt(6, template.requiredLevel());
            ps.setInt(7, template.attackBonus());
            ps.setInt(8, template.defenseBonus());
            ps.setInt(9, template.resistanceBonus());
            ps.setInt(10, template.hpBonus());
            ps.setInt(11, template.mpBonus());
            ps.setBigDecimal(12, template.critBonus());
            ps.setInt(13, template.sellPrice());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private int packageSocketLimit(ItemRecord item) {
        int base = switch (item.quality()) {
            case "immortal" -> 4;
            case "legendary" -> 3;
            case "epic" -> 2;
            case "rare" -> 1;
            default -> 0;
        };
        return Math.min(4, base + (item.ascensionLevel() >= 4 ? 1 : 0));
    }

    private int packageAffixLimit(ItemRecord item) {
        int base = switch (item.quality()) {
            case "immortal" -> 3;
            case "legendary" -> 2;
            case "epic" -> 1;
            default -> item.ascensionLevel() >= 2 ? 1 : 0;
        };
        return Math.min(3, base + (item.ascensionLevel() >= 5 ? 1 : 0));
    }

    private double packageAffixValue(ItemRecord item, String stat, int tier) {
        double level = Math.max(60, item.requiredLevel());
        double quality = qualityRank(item.quality());
        double scalar = tier * (0.65 + quality * 0.08);
        return switch (stat) {
            case "attack" -> Math.round(level * scalar * 0.55);
            case "defense", "resistance" -> Math.round(level * scalar * 0.42);
            case "hp" -> Math.round(level * scalar * 2.8);
            case "mp" -> Math.round(level * scalar * 1.7);
            case "crit" -> Math.round((0.004 + tier * 0.005 + quality * 0.001) * 10_000.0) / 10_000.0;
            default -> 1;
        };
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String equipmentPackageTemplateId(JsonNode effect, String slot, String quality) {
        JsonNode templates = effect.path("equipmentTemplates");
        if (templates.hasNonNull(slot)) {
            return templates.path(slot).asText();
        }
        String prefix = effect.path("equipmentTemplatePrefix").asText("");
        if (!prefix.isBlank()) {
            return prefix + "_" + slot + "_" + quality;
        }
        String tier = effect.path("equipmentTier").asText("");
        if (tier.isBlank()) {
            throw ApiException.badRequest("Equipment package is missing equipment tier or prefix");
        }
        return tier + "_" + slot + "_" + qualityCode(quality);
    }

    private String qualityCode(String quality) {
        return switch (quality) {
            case "common" -> "01";
            case "uncommon" -> "02";
            case "rare" -> "03";
            case "epic" -> "04";
            case "legendary" -> "05";
            case "immortal" -> "06";
            default -> throw ApiException.badRequest("Unsupported equipment package quality: " + quality);
        };
    }

    private EquipmentStats professionAdjustedStats(ItemTemplate template, String profession) {
        int attack = template.attackBonus();
        int defense = template.defenseBonus();
        int resistance = template.resistanceBonus();
        int hp = template.hpBonus();
        int mp = template.mpBonus();
        double crit = template.critBonus() == null ? 0 : template.critBonus().doubleValue();
        return switch (profession) {
            case "warrior" -> new EquipmentStats(
                scale(attack, 1.06),
                scale(defense, 1.08),
                resistance,
                scale(hp, 1.10),
                mp,
                crit
            );
            case "ranger" -> new EquipmentStats(
                scale(attack, 1.08),
                defense,
                resistance,
                scale(hp, 1.04),
                mp,
                crit * 1.08
            );
            case "mage" -> new EquipmentStats(
                scale(attack, 1.05),
                defense,
                scale(resistance, 1.06),
                hp,
                scale(mp, 1.10),
                crit * 1.06
            );
            default -> new EquipmentStats(attack, defense, resistance, hp, mp, crit);
        };
    }

    private int scale(int value, double multiplier) {
        return Math.max(0, (int) Math.round(value * multiplier));
    }

    private String professionGearName(String profession, String baseName, String slot, int ringIndex) {
        String prefix = switch (profession) {
            case "warrior" -> "战士传承";
            case "ranger" -> "游侠传承";
            case "mage" -> "法师传承";
            default -> "远征传承";
        };
        String suffix = "ring".equals(slot) ? (ringIndex == 1 ? "·左戒" : "·右戒") : "";
        return prefix + "·" + baseName + suffix;
    }

    private String equipmentPackageName(String baseName, String slot, int ringIndex) {
        if (!"ring".equals(slot)) {
            return baseName;
        }
        return baseName + (ringIndex == 1 ? "·左戒" : "·右戒");
    }

    @Transactional
    public CraftResult craftRecipe(PlayerRecord player, String recipeId) {
        return craftRecipe(player, recipeId, 1);
    }

    @Transactional
    public CraftResult craftRecipe(PlayerRecord player, String recipeId, int quantity) {
        int count = normalizeBulkActionQuantity(quantity);
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
            int required = safeIntMultiply(cost.quantity(), count, "合成材料数量过大");
            if (owned < required) {
                throw ApiException.badRequest("Missing material " + cost.itemTemplateId() + ": " + owned + "/" + required);
            }
        }
        for (CraftCost cost : costs) {
            consumeQuantityByTemplate(player.id(), cost.itemTemplateId(), safeIntMultiply(cost.quantity(), count, "合成材料数量过大"));
        }
        List<ItemRecord> rewards = grantItem(
            player.id(),
            recipe.resultTemplateId(),
            safeIntMultiply(recipe.resultQuantity(), count, "合成产物数量过大"),
            new Random(System.nanoTime() + recipe.id().hashCode())
        );
        return new CraftResult(recipe.id(), recipe.name(), rewards, snapshot(playerById(player.id())), count);
    }

    private int normalizeBulkActionQuantity(int quantity) {
        if (quantity < 1) {
            throw ApiException.badRequest("操作数量必须大于 0");
        }
        if (quantity > MAX_BULK_ACTIONS) {
            throw ApiException.badRequest("单次最多操作 " + MAX_BULK_ACTIONS + " 次");
        }
        return quantity;
    }

    private int safeIntMultiply(int left, int right, String message) {
        long value = (long) left * right;
        if (value > Integer.MAX_VALUE) {
            throw ApiException.badRequest(message);
        }
        return (int) value;
    }

    private String bulkUseMessage(String itemName, int quantity, List<ItemRecord> rewards) {
        String rewardText = rewards.isEmpty() ? "" : "，获得 " + rewards.size() + " 件奖励";
        return "已使用 " + itemName + " x" + quantity + rewardText;
    }

    @Transactional
    public EnhancementTransferResult transferEnhancement(PlayerRecord player, long sourceItemId, long targetItemId) {
        if (sourceItemId == targetItemId) {
            throw ApiException.badRequest("来源装备和目标装备不能相同");
        }
        ItemRecord source = requireOwnedItem(player.id(), sourceItemId);
        ItemRecord target = requireOwnedItem(player.id(), targetItemId);
        if (!hasTransferableEquipmentProgress(source)) {
            throw ApiException.badRequest("来源装备没有可转移的养成进度");
        }
        if (!canTransferEquipmentProgressBetween(source, target)) {
            throw ApiException.badRequest("装备转移只能转移到相同部位装备");
        }

        List<Long> targetSocketedGemIds = jdbcTemplate.queryForList(
            "SELECT gem_item_id FROM equipment_socket WHERE item_id = ? AND gem_item_id IS NOT NULL",
            Long.class,
            targetItemId
        );
        jdbcTemplate.update("DELETE FROM equipment_socket WHERE item_id = ?", targetItemId);
        jdbcTemplate.update("DELETE FROM equipment_affix WHERE item_id = ?", targetItemId);
        for (Long gemItemId : targetSocketedGemIds) {
            addExistingItemToInventory(player.id(), gemItemId);
        }
        jdbcTemplate.update("UPDATE equipment_socket SET item_id = ? WHERE item_id = ?", targetItemId, sourceItemId);
        jdbcTemplate.update("UPDATE equipment_affix SET item_id = ? WHERE item_id = ?", targetItemId, sourceItemId);
        jdbcTemplate.update(
            """
            UPDATE item_instance
            SET enhancement_level = CASE WHEN id = ? THEN 0 WHEN id = ? THEN ? ELSE enhancement_level END,
                enhancement_luck = CASE WHEN id = ? THEN 0 WHEN id = ? THEN ? ELSE enhancement_luck END,
                refine_level = CASE WHEN id = ? THEN 0 WHEN id = ? THEN ? ELSE refine_level END,
                refine_focus = CASE WHEN id = ? THEN 'balanced' WHEN id = ? THEN ? ELSE refine_focus END,
                ascension_level = CASE WHEN id = ? THEN 0 WHEN id = ? THEN ? ELSE ascension_level END,
                ascension_luck = CASE WHEN id = ? THEN 0 WHEN id = ? THEN ? ELSE ascension_luck END
            WHERE player_id = ? AND id IN (?, ?)
            """,
            sourceItemId,
            targetItemId,
            source.enhancementLevel(),
            sourceItemId,
            targetItemId,
            source.enhancementLuck(),
            sourceItemId,
            targetItemId,
            source.refineLevel(),
            sourceItemId,
            targetItemId,
            normalizeRefineFocus(source.refineFocus()),
            sourceItemId,
            targetItemId,
            source.ascensionLevel(),
            sourceItemId,
            targetItemId,
            source.ascensionLuck(),
            player.id(),
            sourceItemId,
            targetItemId
        );
        return new EnhancementTransferResult(requireItem(sourceItemId), requireItem(targetItemId), snapshot(player));
    }

    @Transactional
    public RefineResult refine(PlayerRecord player, long itemId, String focus) {
        ItemRecord item = requireOwnedItem(player.id(), itemId);
        if (!item.equipment()) {
            throw ApiException.badRequest("Only equipment can be refined");
        }
        if (item.refineLevel() >= 5) {
            throw ApiException.badRequest("装备已达到深渊淬炼上限");
        }
        String normalizedFocus = normalizeRefineFocus(focus);
        int nextLevel = item.refineLevel() + 1;
        int essenceCost = 12 * nextLevel * nextLevel;
        int shardCost = Math.max(0, nextLevel - 2) * 3;
        int orbCost = nextLevel >= 5 ? 1 : 0;
        long goldCost = Math.max(1, item.requiredLevel()) * 800L * nextLevel;
        if (player.gold() < goldCost) {
            throw ApiException.badRequest("金币不足，需要 " + goldCost + " 金");
        }
        if (stackableQuantity(player.id(), "mat_abyss_essence") < essenceCost) {
            throw ApiException.badRequest("深渊精华不足");
        }
        if (shardCost > 0 && stackableQuantity(player.id(), "mat_tempering_shard") < shardCost) {
            throw ApiException.badRequest("淬炼碎片不足");
        }
        if (orbCost > 0 && stackableQuantity(player.id(), "mat_reforge_orb") < orbCost) {
            throw ApiException.badRequest("重铸宝珠不足");
        }

        consumeQuantityByTemplate(player.id(), "mat_abyss_essence", essenceCost);
        if (shardCost > 0) {
            consumeQuantityByTemplate(player.id(), "mat_tempering_shard", shardCost);
        }
        if (orbCost > 0) {
            consumeQuantityByTemplate(player.id(), "mat_reforge_orb", orbCost);
        }
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", goldCost, player.id());
        jdbcTemplate.update(
            "UPDATE item_instance SET refine_level = ?, refine_focus = ? WHERE id = ? AND player_id = ?",
            nextLevel,
            normalizedFocus,
            itemId,
            player.id()
        );
        PlayerRecord updatedPlayer = playerById(player.id());
        ItemRecord refinedItem = requireItem(itemId);
        return new RefineResult(
            refinedItem,
            nextLevel,
            normalizedFocus,
            goldCost,
            essenceCost,
            shardCost,
            orbCost,
            snapshot(updatedPlayer)
        );
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
        synchronized (slotAllocationMonitor) {
            List<ItemRecord> sorted = inventoryItems(player.id()).stream()
                .sorted(sortComparator(sort))
                .toList();
            jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ?", player.id());
            for (int i = 0; i < sorted.size(); i++) {
                insertInventorySlot(player.id(), i, sorted.get(i).id());
            }
            return snapshot(player);
        }
    }

    public int combatPower(PlayerRecord player) {
        var equipment = equippedItems(player.id()).values();
        return combatPowerService.combatPower(
            combatStatsService.playerStats(player, equipment),
            combatStatsService.playerStats(player, List.of()),
            equipment.stream().mapToInt(this::equipmentPower).sum()
        ) + skillService.skillPower(player);
    }

    public ItemRecord requireItem(long itemId) {
        return jdbcTemplate.query(
                """
                SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                       it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level, it.description
                FROM item_instance ii
                JOIN item_template it ON it.id = ii.template_id
                WHERE ii.id = ?
                """,
                (rs, rowNum) -> mapItem(rs),
                itemId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("物品不存在"));
    }

    public ItemRecord requireOwnedItem(long playerId, long itemId) {
        ItemRecord item = requireItem(itemId);
        if (item.playerId() != playerId) {
            throw ApiException.badRequest("物品不属于当前角色");
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
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("物品不在背包中"));
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, itemId);
    }

    public ItemRecord detachOneFromInventory(long playerId, long itemId) {
        ItemRecord item = requireOwnedItem(playerId, itemId);
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("物品不在背包中"));
        if (item.stackable() && item.quantity() > 1) {
            jdbcTemplate.update("UPDATE item_instance SET quantity = quantity - 1 WHERE id = ? AND player_id = ?", itemId, playerId);
            return cloneItemInstance(playerId, item, 1);
        }
        removeFromInventory(playerId, itemId);
        return requireItem(itemId);
    }

    public void consumeInventoryQuantity(long playerId, long itemId, int quantity) {
        int count = Math.max(1, quantity);
        ItemRecord item = requireOwnedItem(playerId, itemId);
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("物品不在背包中"));
        if (!item.stackable() && count > 1) {
            throw ApiException.badRequest("不可堆叠物品数量不足");
        }
        if (count > Math.max(1, item.quantity())) {
            throw ApiException.badRequest(item.name() + " 数量不足");
        }
        if (count >= Math.max(1, item.quantity())) {
            jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, itemId);
            jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", itemId, playerId);
            return;
        }
        jdbcTemplate.update("UPDATE item_instance SET quantity = quantity - ? WHERE id = ? AND player_id = ?", count, itemId, playerId);
    }

    public int templateQuantity(long playerId, String templateId) {
        Integer quantity = jdbcTemplate.queryForObject(
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
        return quantity == null ? 0 : Math.max(0, quantity);
    }

    public void consumeTemplateQuantity(long playerId, String templateId, int quantity) {
        consumeQuantityByTemplate(playerId, templateId, quantity);
    }

    public void addExistingItemToInventory(long playerId, long itemId) {
        ItemRecord item = requireOwnedItem(playerId, itemId);
        if (!item.stackable()) {
            addItemToNextFreeSlot(playerId, itemId);
            return;
        }
        synchronized (slotAllocationMonitor) {
            coalesceInventoryStacks(playerId);
            List<ItemRecord> existingStacks = findInventoryStacks(playerId, item.templateId());
            if (!existingStacks.isEmpty() && existingStacks.getFirst().id() != itemId) {
                jdbcTemplate.update(
                    "UPDATE item_instance SET quantity = quantity + ? WHERE id = ? AND player_id = ?",
                    Math.max(1, item.quantity()),
                    existingStacks.getFirst().id(),
                    playerId
                );
                jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, itemId);
                jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", itemId, playerId);
                return;
            }
            addItemToNextFreeSlot(playerId, itemId);
            coalesceInventoryStacks(playerId);
        }
    }

    public ItemRecord splitItemForMarket(long playerId, long itemId, int quantity) {
        ItemRecord item = requireOwnedItem(playerId, itemId);
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("只能寄售背包中的物品"));
        int listingQuantity = item.stackable() ? Math.max(1, quantity) : 1;
        if (listingQuantity > Math.max(1, item.quantity())) {
            throw ApiException.badRequest("寄售数量不足，当前只有 " + Math.max(1, item.quantity()) + " 件");
        }
        if (!item.stackable() || listingQuantity >= Math.max(1, item.quantity())) {
            removeFromInventory(playerId, itemId);
            return item;
        }
        jdbcTemplate.update(
            "UPDATE item_instance SET quantity = quantity - ? WHERE id = ? AND player_id = ?",
            listingQuantity,
            itemId,
            playerId
        );
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO item_instance
                (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
                 defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, quantity,
                 enhancement_level, enhancement_luck, refine_level, refine_focus, ascension_level, ascension_luck)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, item.templateId());
            ps.setString(3, item.name());
            ps.setString(4, item.itemType());
            ps.setString(5, item.quality());
            ps.setInt(6, item.requiredLevel());
            ps.setInt(7, item.attackBonus());
            ps.setInt(8, item.defenseBonus());
            ps.setInt(9, item.resistanceBonus());
            ps.setInt(10, item.hpBonus());
            ps.setInt(11, item.mpBonus());
            ps.setBigDecimal(12, item.critBonus());
            ps.setInt(13, item.sellPrice());
            ps.setInt(14, listingQuantity);
            ps.setInt(15, item.enhancementLevel());
            ps.setInt(16, item.enhancementLuck());
            ps.setInt(17, item.refineLevel());
            ps.setString(18, item.refineFocus());
            ps.setInt(19, item.ascensionLevel());
            ps.setInt(20, item.ascensionLuck());
            return ps;
        }, keyHolder);
        return requireItem(keyHolder.getKey().longValue());
    }

    private ItemRecord cloneItemInstance(long playerId, ItemRecord item, int quantity) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO item_instance
                (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
                 defense_bonus, resistance_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, quantity,
                 enhancement_level, enhancement_luck, refine_level, refine_focus, ascension_level, ascension_luck)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, playerId);
            ps.setString(2, item.templateId());
            ps.setString(3, item.name());
            ps.setString(4, item.itemType());
            ps.setString(5, item.quality());
            ps.setInt(6, item.requiredLevel());
            ps.setInt(7, item.attackBonus());
            ps.setInt(8, item.defenseBonus());
            ps.setInt(9, item.resistanceBonus());
            ps.setInt(10, item.hpBonus());
            ps.setInt(11, item.mpBonus());
            ps.setBigDecimal(12, item.critBonus());
            ps.setInt(13, item.sellPrice());
            ps.setInt(14, Math.max(1, quantity));
            ps.setInt(15, item.enhancementLevel());
            ps.setInt(16, item.enhancementLuck());
            ps.setInt(17, item.refineLevel());
            ps.setString(18, item.refineFocus());
            ps.setInt(19, item.ascensionLevel());
            ps.setInt(20, item.ascensionLuck());
            return ps;
        }, keyHolder);
        return requireItem(keyHolder.getKey().longValue());
    }

    private void restoreOrphanedInventoryItems(long playerId) {
        List<Long> orphanedItemIds = jdbcTemplate.queryForList(
            """
            SELECT ii.id
            FROM item_instance ii
            LEFT JOIN inventory_slot inv ON inv.item_id = ii.id
            LEFT JOIN equipment_slot eq ON eq.item_id = ii.id
            LEFT JOIN equipment_socket sock ON sock.gem_item_id = ii.id
            LEFT JOIN market_listing listed_market ON listed_market.item_id = ii.id AND listed_market.status = 'listed'
            WHERE ii.player_id = ?
              AND inv.item_id IS NULL
              AND eq.item_id IS NULL
              AND sock.gem_item_id IS NULL
              AND listed_market.item_id IS NULL
            ORDER BY ii.id
            """,
            Long.class,
            playerId
        );
        for (Long itemId : orphanedItemIds) {
            addItemToNextFreeSlot(playerId, itemId);
        }
    }

    private void coalesceInventoryStacks(long playerId) {
        List<String> duplicatedTemplateIds = jdbcTemplate.queryForList(
            """
            SELECT ii.template_id
            FROM inventory_slot s
            JOIN item_instance ii ON ii.id = s.item_id
            JOIN item_template it ON it.id = ii.template_id
            WHERE s.player_id = ? AND it.stackable = TRUE
            GROUP BY ii.template_id
            HAVING COUNT(*) > 1
            """,
            String.class,
            playerId
        );
        for (String templateId : duplicatedTemplateIds) {
            List<ItemRecord> stacks = findInventoryStacks(playerId, templateId);
            if (stacks.size() <= 1) {
                continue;
            }
            ItemRecord target = stacks.getFirst();
            int totalQuantity = stacks.stream().mapToInt(stack -> Math.max(1, stack.quantity())).sum();
            jdbcTemplate.update(
                "UPDATE item_instance SET quantity = ? WHERE id = ? AND player_id = ?",
                totalQuantity,
                target.id(),
                playerId
            );
            for (ItemRecord duplicate : stacks.subList(1, stacks.size())) {
                jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, duplicate.id());
                jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", duplicate.id(), playerId);
            }
        }
    }

    public void transferItemOwner(long itemId, long playerId) {
        jdbcTemplate.update("UPDATE item_instance SET player_id = ? WHERE id = ?", playerId, itemId);
    }

    void consumeOne(long playerId, long itemId) {
        ItemRecord item = requireOwnedItem(playerId, itemId);
        inventorySlot(playerId, itemId).orElseThrow(() -> ApiException.badRequest("Item must be in the inventory"));
        if (item.stackable() && item.quantity() > 1) {
            jdbcTemplate.update("UPDATE item_instance SET quantity = quantity - 1 WHERE id = ? AND player_id = ?", itemId, playerId);
            return;
        }
        jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", playerId, itemId);
        jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", itemId, playerId);
    }

    void consumeQuantityByTemplate(long playerId, String templateId, int quantity) {
        int remaining = Math.max(0, quantity);
        List<ItemRecord> stacks = jdbcTemplate.query(
            """
            SELECT ii.*, it.item_category, it.stackable, it.effect_type, it.effect_value_json,
                   it.enhance_bonus_rate, it.min_enhance_level, it.max_enhance_level, it.description
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

    int stackableQuantity(long playerId, String templateId) {
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

    public int materialQuantity(long playerId, String templateId) {
        return stackableQuantity(playerId, templateId);
    }

    private String normalizeRefineFocus(String focus) {
        String value = focus == null || focus.isBlank() ? "balanced" : focus.trim();
        return switch (value) {
            case "attack", "defense", "resistance", "hp", "mp", "crit", "balanced" -> value;
            default -> throw ApiException.badRequest("Unsupported refine focus: " + value);
        };
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
        long itemId = rs.getLong("id");
        ProcessingStats processingStats = processingStats(itemId);
        return new ItemRecord(
            itemId,
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
            rs.getInt("enhancement_luck"),
            rs.getInt("refine_level"),
            rs.getString("refine_focus"),
            rs.getInt("ascension_level"),
            rs.getInt("ascension_luck"),
            processingStats.socketAttack(),
            processingStats.socketDefense(),
            processingStats.socketResistance(),
            processingStats.socketHp(),
            processingStats.socketMp(),
            processingStats.socketCrit(),
            processingStats.affixAttack(),
            processingStats.affixDefense(),
            processingStats.affixResistance(),
            processingStats.affixHp(),
            processingStats.affixMp(),
            processingStats.affixCrit(),
            processingStats.sockets(),
            processingStats.affixes(),
            descriptionOf(rs)
        );
    }

    private String descriptionOf(java.sql.ResultSet rs) {
        try {
            String value = rs.getString("description");
            return value == null ? "" : value;
        } catch (java.sql.SQLException ignored) {
            return "";
        }
    }

    private ProcessingStats processingStats(long itemId) {
        List<ItemRecord.EquipmentSocketView> sockets = jdbcTemplate.query(
            """
            SELECT s.socket_index, s.unlocked, s.gem_item_id, gt.template_id, it.name, it.quality,
                   gt.stat_key, gt.stat_value, gt.rank_level
            FROM equipment_socket s
            LEFT JOIN item_instance gem ON gem.id = s.gem_item_id
            LEFT JOIN item_template it ON it.id = gem.template_id
            LEFT JOIN gem_template gt ON gt.template_id = gem.template_id
            WHERE s.item_id = ?
            ORDER BY s.socket_index
            """,
            (rs, rowNum) -> new ItemRecord.EquipmentSocketView(
                rs.getInt("socket_index"),
                rs.getBoolean("unlocked"),
                rs.getObject("gem_item_id") == null ? null : rs.getLong("gem_item_id"),
                rs.getString("template_id"),
                rs.getString("name"),
                rs.getString("quality"),
                rs.getString("stat_key"),
                rs.getDouble("stat_value"),
                rs.getInt("rank_level")
            ),
            itemId
        );
        List<ItemRecord.EquipmentAffixView> affixes = jdbcTemplate.query(
            """
            SELECT affix_index, stat_key, stat_value, tier, locked
            FROM equipment_affix
            WHERE item_id = ?
            ORDER BY affix_index
            """,
            (rs, rowNum) -> new ItemRecord.EquipmentAffixView(
                rs.getInt("affix_index"),
                rs.getString("stat_key"),
                rs.getDouble("stat_value"),
                rs.getInt("tier"),
                rs.getBoolean("locked")
            ),
            itemId
        );
        StatBucket socketStats = new StatBucket();
        for (ItemRecord.EquipmentSocketView socket : sockets) {
            socketStats.add(socket.statKey(), socket.statValue());
        }
        StatBucket affixStats = new StatBucket();
        for (ItemRecord.EquipmentAffixView affix : affixes) {
            affixStats.add(affix.statKey(), affix.statValue());
        }
        return new ProcessingStats(
            socketStats.attack,
            socketStats.defense,
            socketStats.resistance,
            socketStats.hp,
            socketStats.mp,
            socketStats.crit,
            affixStats.attack,
            affixStats.defense,
            affixStats.resistance,
            affixStats.hp,
            affixStats.mp,
            affixStats.crit,
            sockets,
            affixes
        );
    }

    private static class StatBucket {
        private int attack;
        private int defense;
        private int resistance;
        private int hp;
        private int mp;
        private double crit;

        private void add(String statKey, double statValue) {
            if (statKey == null || statValue <= 0) {
                return;
            }
            switch (statKey) {
                case "attack" -> attack += (int) Math.round(statValue);
                case "defense" -> defense += (int) Math.round(statValue);
                case "resistance" -> resistance += (int) Math.round(statValue);
                case "hp" -> hp += (int) Math.round(statValue);
                case "mp" -> mp += (int) Math.round(statValue);
                case "crit" -> crit += statValue;
                default -> {
                }
            }
        }
    }

    private record ProcessingStats(
        int socketAttack,
        int socketDefense,
        int socketResistance,
        int socketHp,
        int socketMp,
        double socketCrit,
        int affixAttack,
        int affixDefense,
        int affixResistance,
        int affixHp,
        int affixMp,
        double affixCrit,
        List<ItemRecord.EquipmentSocketView> sockets,
        List<ItemRecord.EquipmentAffixView> affixes
    ) {
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

    public record RefineResult(
        ItemRecord item,
        int refineLevel,
        String refineFocus,
        long goldCost,
        int essenceCost,
        int shardCost,
        int orbCost,
        InventorySnapshot inventory
    ) {
    }

    public record UseItemResult(
        String itemName,
        String effectType,
        String message,
        List<ItemRecord> rewards,
        StaminaSnapshot stamina,
        InventorySnapshot inventory,
        List<ItemEffectEvent> events,
        int quantity
    ) {
    }

    public record CraftResult(String recipeId, String recipeName, List<ItemRecord> rewards, InventorySnapshot inventory, int quantity) {
    }

    private record EquipmentStats(
        int attackBonus,
        int defenseBonus,
        int resistanceBonus,
        int hpBonus,
        int mpBonus,
        double critBonus
    ) {
    }

    private record CraftRecipe(String id, String name, String resultTemplateId, int resultQuantity, int requiredLevel) {
    }

    private record CraftCost(String itemTemplateId, int quantity) {
    }

    record StackableStack(long itemId, int quantity) {
    }

    record StackableGrantStep(Long itemId, int quantity) {
    }
}
