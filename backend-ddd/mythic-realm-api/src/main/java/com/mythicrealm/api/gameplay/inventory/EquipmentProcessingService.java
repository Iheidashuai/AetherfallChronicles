package com.mythicrealm.api.gameplay.inventory;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentProcessingService {
    private static final List<String> MATERIAL_IDS = List.of(
        "mat_abyss_essence",
        "mat_tempering_shard",
        "mat_reforge_orb",
        "mat_socket_core",
        "mat_gem_dust",
        "mat_affix_lock",
        "mat_ascension_core",
        "mat_ascension_guard"
    );
    private static final List<String> AFFIX_STATS = List.of("attack", "defense", "resistance", "hp", "mp", "crit");

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final GameConfigService gameConfigService;
    private final PlayerService playerService;

    public EquipmentProcessingService(
        JdbcTemplate jdbcTemplate,
        InventoryService inventoryService,
        GameConfigService gameConfigService,
        PlayerService playerService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.gameConfigService = gameConfigService;
        this.playerService = playerService;
    }

    @Transactional
    public ProcessingSnapshot snapshot(PlayerRecord player) {
        List<ItemRecord> allEquipment = new ArrayList<>();
        allEquipment.addAll(inventoryService.equippedItems(player.id()).values());
        allEquipment.addAll(inventoryService.inventoryItems(player.id()).stream().filter(ItemRecord::equipment).toList());
        List<ProcessingItemView> equipment = allEquipment.stream()
            .sorted(Comparator.comparingInt(inventoryService::equipmentPower).reversed().thenComparingLong(ItemRecord::id))
            .map(this::itemView)
            .toList();
        List<ItemRecord> gems = inventoryService.inventoryItems(player.id()).stream()
            .filter(item -> "gem".equals(item.effectType()))
            .sorted(Comparator.comparing(ItemRecord::templateId).thenComparingLong(ItemRecord::id))
            .toList();
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (String materialId : MATERIAL_IDS) {
            materials.put(materialId, inventoryService.materialQuantity(player.id(), materialId));
        }
        return new ProcessingSnapshot(
            equipment,
            gems,
            materials,
            recentLogs(player.id()),
            inventoryService.snapshot(playerService.requireById(player.id()))
        );
    }

    @Transactional
    public ProcessingResult unlockSocket(PlayerRecord player, long itemId) {
        ItemRecord item = requireEquipment(player.id(), itemId);
        int limit = socketLimit(item);
        int unlocked = unlockedSocketCount(item.id());
        if (unlocked >= limit) {
            throw ApiException.badRequest("当前品质孔位已全部开启");
        }
        SocketUnlockCost cost = socketUnlockCost(item, unlocked + 1);
        requireMaterials(player, cost.goldCost(), Map.of(
            "mat_socket_core", cost.socketCores(),
            "mat_gem_dust", cost.gemDust(),
            "mat_abyss_essence", cost.essence()
        ));
        int beforePower = inventoryService.combatPower(player);
        consumeMaterials(player, cost.goldCost(), List.of(
            new MaterialCost("mat_socket_core", cost.socketCores()),
            new MaterialCost("mat_gem_dust", cost.gemDust()),
            new MaterialCost("mat_abyss_essence", cost.essence())
        ));
        boolean success = roll(cost.chance(), player.id(), itemId, "socket");
        if (success) {
            jdbcTemplate.update(
                "INSERT INTO equipment_socket (item_id, socket_index, unlocked) VALUES (?, ?, TRUE)",
                itemId,
                unlocked
            );
        }
        return result(player.id(), itemId, "socket", success,
            success ? "孔位开启成功" : "开孔失败，材料已消耗但装备未降级",
            beforePower,
            List.of(
                new MaterialCost("gold", (int) cost.goldCost()),
                new MaterialCost("mat_socket_core", cost.socketCores()),
                new MaterialCost("mat_gem_dust", cost.gemDust()),
                new MaterialCost("mat_abyss_essence", cost.essence())
            )
        );
    }

    @Transactional
    public ProcessingResult socketGem(PlayerRecord player, long itemId, int socketIndex, long gemItemId) {
        ItemRecord item = requireEquipment(player.id(), itemId);
        ItemRecord gem = inventoryService.requireOwnedItem(player.id(), gemItemId);
        inventoryService.inventorySlot(player.id(), gemItemId).orElseThrow(() -> ApiException.badRequest("宝石必须在背包中"));
        if (!"gem".equals(gem.effectType())) {
            throw ApiException.badRequest("请选择宝石进行镶嵌");
        }
        SocketRow socket = requireSocket(item.id(), socketIndex);
        if (socket.gemItemId() != null) {
            throw ApiException.badRequest("该孔位已有宝石，请先取下");
        }
        requireGemTemplate(gem.templateId());
        int beforePower = inventoryService.combatPower(player);
        inventoryService.removeFromInventory(player.id(), gemItemId);
        jdbcTemplate.update(
            "UPDATE equipment_socket SET gem_item_id = ? WHERE item_id = ? AND socket_index = ?",
            gemItemId,
            itemId,
            socketIndex
        );
        return result(player.id(), itemId, "socket", true,
            "已镶嵌 " + gem.displayName(),
            beforePower,
            List.of()
        );
    }

    @Transactional
    public ProcessingResult unsocketGem(PlayerRecord player, long itemId, int socketIndex) {
        ItemRecord item = requireEquipment(player.id(), itemId);
        SocketRow socket = requireSocket(item.id(), socketIndex);
        if (socket.gemItemId() == null) {
            throw ApiException.badRequest("该孔位没有宝石");
        }
        int beforePower = inventoryService.combatPower(player);
        jdbcTemplate.update(
            "UPDATE equipment_socket SET gem_item_id = NULL WHERE item_id = ? AND socket_index = ?",
            itemId,
            socketIndex
        );
        inventoryService.addExistingItemToInventory(player.id(), socket.gemItemId());
        return result(player.id(), itemId, "unsocket", true, "宝石已取下并放回背包", beforePower, List.of());
    }

    @Transactional
    public ProcessingResult upgradeGems(PlayerRecord player, List<Long> gemItemIds) {
        if (gemItemIds == null || gemItemIds.size() != 3) {
            throw ApiException.badRequest("宝石升级需要 3 颗同类同阶宝石");
        }
        List<ItemRecord> gems = gemItemIds.stream().map(id -> inventoryService.requireOwnedItem(player.id(), id)).toList();
        for (ItemRecord gem : gems) {
            inventoryService.inventorySlot(player.id(), gem.id()).orElseThrow(() -> ApiException.badRequest("宝石必须在背包中"));
            if (!"gem".equals(gem.effectType())) {
                throw ApiException.badRequest("只能升级宝石");
            }
        }
        String templateId = gems.getFirst().templateId();
        if (gems.stream().anyMatch(gem -> !templateId.equals(gem.templateId()))) {
            throw ApiException.badRequest("请选择 3 颗同类同阶宝石");
        }
        GemTemplate gemTemplate = requireGemTemplate(templateId);
        if (gemTemplate.nextTemplateId() == null || gemTemplate.nextTemplateId().isBlank()) {
            throw ApiException.badRequest("该宝石已达到最高阶");
        }
        requireMaterials(player, 0, Map.of("mat_gem_dust", 8 * gemTemplate.rankLevel()));
        int beforePower = inventoryService.combatPower(player);
        consumeMaterials(player, 0, List.of(new MaterialCost("mat_gem_dust", 8 * gemTemplate.rankLevel())));
        for (ItemRecord gem : gems) {
            inventoryService.removeFromInventory(player.id(), gem.id());
            jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", gem.id(), player.id());
        }
        ItemRecord created = inventoryService.addRewardItem(player.id(), gemTemplate.nextTemplateId(), new Random(System.nanoTime()));
        return result(player.id(), created.id(), "gem_upgrade", true,
            "合成 " + created.displayName(),
            beforePower,
            List.of(new MaterialCost("mat_gem_dust", 8 * gemTemplate.rankLevel()))
        );
    }

    @Transactional
    public ProcessingResult reforge(PlayerRecord player, long itemId, List<Integer> lockedAffixIndexes) {
        ItemRecord item = requireEquipment(player.id(), itemId);
        int limit = affixLimit(item);
        if (limit <= 0) {
            throw ApiException.badRequest("该品质暂未解锁后期词条，至少需要史诗品质或升阶后的稀有装备");
        }
        Set<Integer> locked = lockedAffixIndexes == null ? Set.of() : lockedAffixIndexes.stream()
            .filter(index -> index != null && index >= 0 && index < limit)
            .collect(Collectors.toSet());
        ReforgeCost cost = reforgeCost(item, limit, locked.size());
        requireMaterials(player, cost.goldCost(), Map.of(
            "mat_reforge_orb", cost.orbs(),
            "mat_abyss_essence", cost.essence(),
            "mat_affix_lock", cost.lockStones()
        ));
        int beforePower = inventoryService.combatPower(player);
        ensureAffixes(item, limit, new Random(System.nanoTime() + itemId));
        jdbcTemplate.update("UPDATE equipment_affix SET locked = FALSE WHERE item_id = ?", itemId);
        for (Integer index : locked) {
            jdbcTemplate.update("UPDATE equipment_affix SET locked = TRUE WHERE item_id = ? AND affix_index = ?", itemId, index);
        }
        consumeMaterials(player, cost.goldCost(), List.of(
            new MaterialCost("mat_reforge_orb", cost.orbs()),
            new MaterialCost("mat_abyss_essence", cost.essence()),
            new MaterialCost("mat_affix_lock", cost.lockStones())
        ));
        boolean success = roll(cost.chance(), player.id(), itemId, "reforge");
        Random random = new Random(System.nanoTime() + player.id() * 31 + itemId);
        if (success) {
            for (int index = 0; index < limit; index++) {
                if (!locked.contains(index)) {
                    writeAffix(item, index, rollAffixTier(item, random), random);
                }
            }
        } else {
            downgradeUnlockedAffixes(itemId, locked);
        }
        return result(player.id(), itemId, "reforge", success,
            success ? "词条重铸完成" : "重铸失败，未锁定词条档位下降",
            beforePower,
            List.of(
                new MaterialCost("gold", (int) cost.goldCost()),
                new MaterialCost("mat_reforge_orb", cost.orbs()),
                new MaterialCost("mat_abyss_essence", cost.essence()),
                new MaterialCost("mat_affix_lock", cost.lockStones())
            )
        );
    }

    @Transactional
    public ProcessingResult ascend(PlayerRecord player, long itemId, boolean useProtector) {
        ItemRecord item = requireEquipment(player.id(), itemId);
        if (item.ascensionLevel() >= 5) {
            throw ApiException.badRequest("装备已达到升阶上限");
        }
        AscensionCost cost = ascensionCost(item, useProtector);
        requireMaterials(player, cost.goldCost(), Map.of(
            "mat_ascension_core", cost.ascensionCores(),
            "mat_abyss_essence", cost.essence(),
            "mat_tempering_shard", cost.shards(),
            "mat_ascension_guard", cost.guards()
        ));
        int beforePower = inventoryService.combatPower(player);
        consumeMaterials(player, cost.goldCost(), List.of(
            new MaterialCost("mat_ascension_core", cost.ascensionCores()),
            new MaterialCost("mat_abyss_essence", cost.essence()),
            new MaterialCost("mat_tempering_shard", cost.shards()),
            new MaterialCost("mat_ascension_guard", cost.guards())
        ));
        boolean success = roll(cost.chance(), player.id(), itemId, "ascend");
        int nextLevel = item.ascensionLevel();
        int nextLuck = item.ascensionLuck();
        String message;
        if (success) {
            nextLevel += 1;
            nextLuck = 0;
            message = "升阶成功，装备加工上限提高";
        } else {
            nextLuck += 1;
            if (item.ascensionLevel() >= 3 && !useProtector) {
                nextLevel = Math.max(0, item.ascensionLevel() - 1);
                message = "升阶失败，装备掉落 1 阶";
            } else {
                message = useProtector ? "升阶失败，护阶符防止掉阶" : "升阶失败，幸运值提升";
            }
        }
        jdbcTemplate.update(
            "UPDATE item_instance SET ascension_level = ?, ascension_luck = ? WHERE id = ? AND player_id = ?",
            nextLevel,
            nextLuck,
            itemId,
            player.id()
        );
        return result(player.id(), itemId, "ascend", success, message, beforePower, List.of(
            new MaterialCost("gold", (int) cost.goldCost()),
            new MaterialCost("mat_ascension_core", cost.ascensionCores()),
            new MaterialCost("mat_abyss_essence", cost.essence()),
            new MaterialCost("mat_tempering_shard", cost.shards()),
            new MaterialCost("mat_ascension_guard", cost.guards())
        ));
    }

    public ProcessingItemView itemView(ItemRecord item) {
        int socketLimit = socketLimit(item);
        int affixLimit = affixLimit(item);
        return new ProcessingItemView(
            item,
            socketLimit,
            affixLimit,
            unlockedSocketCount(item.id()),
            item.affixes().size(),
            socketUnlockCost(item, Math.min(socketLimit, unlockedSocketCount(item.id()) + 1)),
            reforgeCost(item, Math.max(1, affixLimit), 0),
            ascensionCost(item, false)
        );
    }

    private ProcessingResult result(long playerId, long itemId, String actionType, boolean success, String message, int beforePower, List<MaterialCost> costs) {
        PlayerRecord updatedPlayer = playerService.requireById(playerId);
        ItemRecord updatedItem = inventoryService.requireItem(itemId);
        int afterPower = inventoryService.combatPower(updatedPlayer);
        jdbcTemplate.update(
            "INSERT INTO equipment_processing_log (player_id, item_id, action_type, success, summary, power_before, power_after) VALUES (?, ?, ?, ?, ?, ?, ?)",
            playerId,
            itemId,
            actionType,
            success,
            message,
            beforePower,
            afterPower
        );
        return new ProcessingResult(
            actionType,
            success,
            message,
            updatedItem,
            costs.stream().filter(cost -> cost.quantity() > 0).toList(),
            beforePower,
            afterPower,
            snapshot(updatedPlayer)
        );
    }

    private ItemRecord requireEquipment(long playerId, long itemId) {
        ItemRecord item = inventoryService.requireOwnedItem(playerId, itemId);
        if (!item.equipment()) {
            throw ApiException.badRequest("只能加工装备");
        }
        return item;
    }

    private SocketRow requireSocket(long itemId, int socketIndex) {
        return jdbcTemplate.query(
            "SELECT item_id, socket_index, gem_item_id FROM equipment_socket WHERE item_id = ? AND socket_index = ? AND unlocked = TRUE",
            (rs, rowNum) -> new SocketRow(
                rs.getLong("item_id"),
                rs.getInt("socket_index"),
                rs.getObject("gem_item_id") == null ? null : rs.getLong("gem_item_id")
            ),
            itemId,
            socketIndex
        ).stream().findFirst().orElseThrow(() -> ApiException.badRequest("孔位未开启"));
    }

    private GemTemplate requireGemTemplate(String templateId) {
        return jdbcTemplate.query(
            "SELECT template_id, gem_kind, rank_level, stat_key, stat_value, next_template_id FROM gem_template WHERE template_id = ?",
            (rs, rowNum) -> new GemTemplate(
                rs.getString("template_id"),
                rs.getString("gem_kind"),
                rs.getInt("rank_level"),
                rs.getString("stat_key"),
                rs.getDouble("stat_value"),
                rs.getString("next_template_id")
            ),
            templateId
        ).stream().findFirst().orElseThrow(() -> ApiException.badRequest("宝石配置不存在"));
    }

    private int unlockedSocketCount(long itemId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM equipment_socket WHERE item_id = ? AND unlocked = TRUE",
            Integer.class,
            itemId
        );
        return count == null ? 0 : count;
    }

    private int socketLimit(ItemRecord item) {
        int base = switch (item.quality()) {
            case "immortal" -> 4;
            case "legendary" -> 3;
            case "epic" -> 2;
            case "rare" -> 1;
            default -> 0;
        };
        return Math.min(4, base + (item.ascensionLevel() >= 4 ? 1 : 0));
    }

    private int affixLimit(ItemRecord item) {
        int base = switch (item.quality()) {
            case "immortal" -> 3;
            case "legendary" -> 2;
            case "epic" -> 1;
            default -> item.ascensionLevel() >= 2 ? 1 : 0;
        };
        return Math.min(3, base + (item.ascensionLevel() >= 5 ? 1 : 0));
    }

    private SocketUnlockCost socketUnlockCost(ItemRecord item, int socketNumber) {
        int slot = Math.max(1, socketNumber);
        double chance = switch (slot) {
            case 1 -> 1.0;
            case 2 -> 0.85;
            case 3 -> 0.65;
            default -> 0.45;
        };
        chance = Math.min(0.98, chance + item.ascensionLevel() * 0.02);
        return new SocketUnlockCost(
            (long) Math.max(1, item.requiredLevel()) * 450L * slot,
            slot,
            10 * slot,
            slot >= 3 ? 8 * slot : 0,
            chance
        );
    }

    private ReforgeCost reforgeCost(ItemRecord item, int affixCount, int lockedCount) {
        int count = Math.max(1, affixCount);
        double chance = Math.max(0.38, 0.78 - lockedCount * 0.08 + item.ascensionLevel() * 0.025);
        return new ReforgeCost(
            (long) Math.max(1, item.requiredLevel()) * 1200L * (count + lockedCount),
            1 + lockedCount,
            18 * count + 10 * lockedCount,
            lockedCount,
            chance
        );
    }

    private AscensionCost ascensionCost(ItemRecord item, boolean useProtector) {
        int next = Math.min(5, item.ascensionLevel() + 1);
        double baseChance = switch (next) {
            case 1 -> 0.90;
            case 2 -> 0.75;
            case 3 -> 0.60;
            case 4 -> 0.45;
            default -> 0.32;
        };
        double chance = Math.min(0.92, baseChance + item.ascensionLuck() * 0.05);
        return new AscensionCost(
            (long) Math.max(1, item.requiredLevel()) * 1800L * next,
            next,
            24 * next,
            Math.max(0, next - 1) * 6,
            useProtector && item.ascensionLevel() >= 3 ? 1 : 0,
            chance
        );
    }

    private void ensureAffixes(ItemRecord item, int limit, Random random) {
        int existing = item.affixes().size();
        for (int index = existing; index < limit; index++) {
            writeAffix(item, index, 1, random);
        }
    }

    private void writeAffix(ItemRecord item, int index, int tier, Random random) {
        String stat = AFFIX_STATS.get(random.nextInt(AFFIX_STATS.size()));
        double value = affixValue(item, stat, tier);
        jdbcTemplate.update(
            """
            INSERT INTO equipment_affix (item_id, affix_index, stat_key, stat_value, tier, locked)
            VALUES (?, ?, ?, ?, ?, FALSE)
            ON DUPLICATE KEY UPDATE stat_key = VALUES(stat_key), stat_value = VALUES(stat_value), tier = VALUES(tier)
            """,
            item.id(),
            index,
            stat,
            value,
            tier
        );
    }

    private void downgradeUnlockedAffixes(long itemId, Set<Integer> locked) {
        List<ItemRecord.EquipmentAffixView> affixes = inventoryService.requireItem(itemId).affixes();
        for (ItemRecord.EquipmentAffixView affix : affixes) {
            if (locked.contains(affix.affixIndex())) {
                continue;
            }
            int nextTier = Math.max(1, affix.tier() - 1);
            double nextValue = affix.statValue() * (nextTier / (double) Math.max(1, affix.tier()));
            jdbcTemplate.update(
                "UPDATE equipment_affix SET tier = ?, stat_value = ? WHERE item_id = ? AND affix_index = ?",
                nextTier,
                nextValue,
                itemId,
                affix.affixIndex()
            );
        }
    }

    private int rollAffixTier(ItemRecord item, Random random) {
        int bonus = inventoryService.qualityRank(item.quality()) + item.ascensionLevel();
        int roll = random.nextInt(100) + bonus * 3;
        if (roll >= 96) {
            return 5;
        }
        if (roll >= 84) {
            return 4;
        }
        if (roll >= 62) {
            return 3;
        }
        if (roll >= 32) {
            return 2;
        }
        return 1;
    }

    private double affixValue(ItemRecord item, String stat, int tier) {
        double level = Math.max(60, item.requiredLevel());
        double quality = inventoryService.qualityRank(item.quality());
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

    private void requireMaterials(PlayerRecord player, long goldCost, Map<String, Integer> materials) {
        if (playerService.requireById(player.id()).gold() < goldCost) {
            throw ApiException.badRequest("金币不足，需要 " + goldCost + " 金");
        }
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            int quantity = Math.max(0, entry.getValue());
            if (quantity > 0 && inventoryService.materialQuantity(player.id(), entry.getKey()) < quantity) {
                ItemTemplate template = gameConfigService.requireItem(entry.getKey());
                throw ApiException.badRequest(template.name() + "不足，需要 " + quantity);
            }
        }
    }

    private void consumeMaterials(PlayerRecord player, long goldCost, List<MaterialCost> materials) {
        if (goldCost > 0) {
            jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", goldCost, player.id());
        }
        for (MaterialCost material : materials) {
            if (!"gold".equals(material.templateId()) && material.quantity() > 0) {
                consumeQuantityByTemplate(player.id(), material.templateId(), material.quantity());
            }
        }
    }

    private void consumeQuantityByTemplate(long playerId, String templateId, int quantity) {
        int remaining = Math.max(0, quantity);
        List<ItemRecord> stacks = inventoryService.inventoryItems(playerId).stream()
            .filter(item -> templateId.equals(item.templateId()))
            .toList();
        for (ItemRecord stack : stacks) {
            if (remaining <= 0) {
                return;
            }
            int consume = Math.min(remaining, Math.max(1, stack.quantity()));
            if (consume >= stack.quantity()) {
                inventoryService.removeFromInventory(playerId, stack.id());
                jdbcTemplate.update("DELETE FROM item_instance WHERE id = ? AND player_id = ?", stack.id(), playerId);
            } else {
                jdbcTemplate.update("UPDATE item_instance SET quantity = quantity - ? WHERE id = ? AND player_id = ?", consume, stack.id(), playerId);
            }
            remaining -= consume;
        }
        if (remaining > 0) {
            throw ApiException.badRequest("材料不足: " + templateId);
        }
    }

    private boolean roll(double chance, long playerId, long itemId, String salt) {
        Random random = new Random(System.nanoTime() + playerId * 37 + itemId * 13 + salt.hashCode());
        return random.nextDouble() <= chance;
    }

    private List<ProcessingLogView> recentLogs(long playerId) {
        return jdbcTemplate.query(
            """
            SELECT action_type, success, summary, power_before, power_after, created_at
            FROM equipment_processing_log
            WHERE player_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 8
            """,
            (rs, rowNum) -> new ProcessingLogView(
                rs.getString("action_type"),
                rs.getBoolean("success"),
                rs.getString("summary"),
                rs.getInt("power_before"),
                rs.getInt("power_after"),
                rs.getString("created_at")
            ),
            playerId
        );
    }

    private record SocketRow(long itemId, int socketIndex, Long gemItemId) {
    }

    private record GemTemplate(String templateId, String gemKind, int rankLevel, String statKey, double statValue, String nextTemplateId) {
    }

    public record MaterialCost(String templateId, int quantity) {
    }

    public record SocketUnlockCost(long goldCost, int socketCores, int gemDust, int essence, double chance) {
    }

    public record ReforgeCost(long goldCost, int orbs, int essence, int lockStones, double chance) {
    }

    public record AscensionCost(long goldCost, int ascensionCores, int essence, int shards, int guards, double chance) {
    }

    public record ProcessingItemView(
        ItemRecord item,
        int socketLimit,
        int affixLimit,
        int unlockedSocketCount,
        int affixCount,
        SocketUnlockCost nextSocketCost,
        ReforgeCost reforgeCost,
        AscensionCost ascensionCost
    ) {
    }

    public record ProcessingLogView(String actionType, boolean success, String summary, int powerBefore, int powerAfter, String createdAt) {
    }

    public record ProcessingSnapshot(
        List<ProcessingItemView> equipment,
        List<ItemRecord> gems,
        Map<String, Integer> materials,
        List<ProcessingLogView> recentLogs,
        InventoryService.InventorySnapshot inventory
    ) {
    }

    public record ProcessingResult(
        String actionType,
        boolean success,
        String message,
        ItemRecord item,
        List<MaterialCost> consumed,
        int powerBefore,
        int powerAfter,
        ProcessingSnapshot snapshot
    ) {
    }
}
