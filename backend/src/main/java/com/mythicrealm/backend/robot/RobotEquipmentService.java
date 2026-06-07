package com.mythicrealm.backend.robot;

import com.mythicrealm.backend.common.ApiException;
import com.mythicrealm.backend.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.backend.gameconfig.GameConfigService;
import com.mythicrealm.backend.inventory.InventoryService;
import com.mythicrealm.backend.inventory.ItemRecord;
import com.mythicrealm.backend.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.backend.player.PlayerRecord;
import com.mythicrealm.backend.player.PlayerService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotEquipmentService {
    private static final List<String> SLOT_ORDER = List.of("weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace", "ring1", "ring2");

    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final InventoryService inventoryService;
    private final PlayerService playerService;

    public RobotEquipmentService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        InventoryService inventoryService,
        PlayerService playerService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.inventoryService = inventoryService;
        this.playerService = playerService;
    }

    @Transactional
    public List<EquipmentSummary> equipmentForRobot(long robotId, String name, String profession, int level, int power) {
        PlayerRecord robot = playerService.requireById(robotId);
        ensureEquipment(robot);
        return equipmentForPlayer(robot.id());
    }

    @Transactional
    public DropResolution resolveDropIfUpgrade(PlayerRecord robot, ItemRecord item, Random random) {
        ensureEquipment(robot);
        String slot = slotForDrop(robot.id(), item.itemType(), random);
        int oldPower = currentSlotPower(robot.id(), slot);
        int itemPower = itemPower(item);
        if (itemPower <= oldPower) {
            return new DropResolution(item, null);
        }
        equipIntoSlot(robot.id(), item.id(), slot);
        EquipmentChange change = new EquipmentChange(
            item.displayName(),
            slotName(slot),
            item.enhancementLevel(),
            Math.max(1, itemPower - oldPower),
            itemPower,
            true
        );
        return new DropResolution(item, change);
    }

    @Transactional
    public EquipmentChange enhanceRandomEquipment(PlayerRecord robot, Random random) {
        ensureEquipment(robot);
        List<EquippedItem> rows = jdbcTemplate.query(
            """
            SELECT es.slot_name, ii.*
            FROM equipment_slot es
            JOIN item_instance ii ON ii.id = es.item_id
            WHERE es.player_id = ? AND ii.enhancement_level < 15
            ORDER BY RAND()
            LIMIT 1
            """,
            (rs, rowNum) -> new EquippedItem(rs.getString("slot_name"), mapItem(rs)),
            robot.id()
        );
        if (rows.isEmpty()) {
            return null;
        }

        EquippedItem equipped = rows.get(0);
        int beforePower = inventoryService.combatPower(robot);
        InventoryService.EnhanceResult result;
        try {
            result = inventoryService.enhance(robot, equipped.item().id());
        } catch (ApiException error) {
            return null;
        }
        ItemRecord updatedItem = inventoryService.requireItem(equipped.item().id());
        return new EquipmentChange(
            updatedItem.displayName(),
            slotName(equipped.slot()),
            updatedItem.enhancementLevel(),
            result.inventory().combatPower() - beforePower,
            itemPower(updatedItem),
            result.success()
        );
    }

    private void ensureEquipment(PlayerRecord robot) {
        Map<String, ItemRecord> equipped = inventoryService.equippedItems(robot.id());
        if (equipped.size() >= SLOT_ORDER.size()) {
            return;
        }
        for (String slot : SLOT_ORDER) {
            if (equipped.containsKey(slot)) {
                continue;
            }
            Random random = new Random(Objects.hash(robot.id(), robot.name(), robot.profession(), robot.level(), slot));
            String itemType = slot.replaceAll("\\d", "");
            ItemTemplate template = chooseBootstrapTemplate(itemType, robot, random);
            long itemId = inventoryService.createItem(robot.id(), template, random);
            jdbcTemplate.update(
                "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
                robot.id(),
                slot,
                itemId
            );
        }
    }

    private List<EquipmentSummary> equipmentForPlayer(long playerId) {
        Map<String, ItemRecord> equipped = inventoryService.equippedItems(playerId);
        return SLOT_ORDER.stream()
            .filter(equipped::containsKey)
            .map(slot -> equipmentSummary(slot, equipped.get(slot)))
            .toList();
    }

    private EquipmentSummary equipmentSummary(String slot, ItemRecord item) {
        return new EquipmentSummary(
            item.templateId(),
            slot,
            slotName(slot),
            item.displayName(),
            item.quality(),
            item.requiredLevel(),
            itemPower(item),
            item.attackBonus(),
            item.defenseBonus(),
            item.hpBonus(),
            item.mpBonus(),
            item.critBonus().doubleValue(),
            item.sellPrice(),
            item.enhancementLevel(),
            item.enhancementLuck(),
            "角色装备栏 · " + item.templateId()
        );
    }

    private ItemTemplate chooseBootstrapTemplate(String itemType, PlayerRecord robot, Random random) {
        String targetQuality = qualityForLevel(robot.level());
        int targetQualityRank = qualityRank(targetQuality);
        List<ItemTemplate> candidates = gameConfigService.itemTemplates().stream()
            .filter(item -> item.type().equals(itemType))
            .filter(item -> item.requiredLevel() <= Math.max(1, robot.level()))
            .filter(item -> qualityRank(item.quality()) <= targetQualityRank)
            .sorted(Comparator
                .comparingInt((ItemTemplate item) -> Math.abs(item.requiredLevel() - Math.max(1, robot.level())))
                .thenComparingInt(item -> Math.abs(qualityRank(item.quality()) - targetQualityRank))
                .thenComparing(ItemTemplate::id))
            .toList();
        if (candidates.isEmpty()) {
            candidates = gameConfigService.itemTemplates().stream()
                .filter(item -> item.type().equals(itemType))
                .sorted(Comparator.comparingInt(ItemTemplate::requiredLevel).thenComparing(ItemTemplate::id))
                .toList();
        }
        int spread = Math.min(6, candidates.size());
        return candidates.get(random.nextInt(Math.max(1, spread)));
    }

    private String slotForDrop(long playerId, String itemType, Random random) {
        if (!"ring".equals(itemType)) {
            return itemType;
        }
        int ring1Power = currentSlotPower(playerId, "ring1");
        int ring2Power = currentSlotPower(playerId, "ring2");
        if (ring1Power == ring2Power) {
            return random.nextBoolean() ? "ring1" : "ring2";
        }
        return ring1Power <= ring2Power ? "ring1" : "ring2";
    }

    private int currentSlotPower(long playerId, String slot) {
        return jdbcTemplate.query(
            """
            SELECT ii.*
            FROM equipment_slot es
            JOIN item_instance ii ON ii.id = es.item_id
            WHERE es.player_id = ? AND es.slot_name = ?
            """,
            (rs, rowNum) -> itemPower(mapItem(rs)),
            playerId,
            slot
        ).stream().findFirst().orElse(0);
    }

    private void equipIntoSlot(long playerId, long itemId, String slot) {
        inventoryService.removeFromInventory(playerId, itemId);
        List<Long> previous = jdbcTemplate.queryForList(
            "SELECT item_id FROM equipment_slot WHERE player_id = ? AND slot_name = ?",
            Long.class,
            playerId,
            slot
        );
        if (!previous.isEmpty()) {
            jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ? AND slot_name = ?", playerId, slot);
            inventoryService.addExistingItemToInventory(playerId, previous.get(0));
        }
        jdbcTemplate.update(
            "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
            playerId,
            slot,
            itemId
        );
    }

    private int itemPower(ItemRecord item) {
        return Math.max(1, item.enhancedAttackBonus() * 12
            + item.enhancedDefenseBonus() * 8
            + item.enhancedHpBonus() / 2
            + item.enhancedMpBonus() / 2
            + item.enhancementLevel() * 16);
    }

    private String qualityForLevel(int level) {
        if (level >= 55) {
            return "epic";
        }
        if (level >= 35) {
            return "rare";
        }
        if (level >= 15) {
            return "uncommon";
        }
        return "common";
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

    private String slotName(String slot) {
        return switch (slot) {
            case "weapon" -> "武器";
            case "helmet" -> "头盔";
            case "armor" -> "护甲";
            case "legs" -> "护腿";
            case "boots" -> "靴子";
            case "gloves" -> "护手";
            case "necklace" -> "项链";
            case "ring1" -> "戒指一";
            case "ring2" -> "戒指二";
            default -> slot;
        };
    }

    private ItemRecord mapItem(ResultSet rs) throws SQLException {
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

    private record EquippedItem(String slot, ItemRecord item) {
    }

    public record DropResolution(ItemRecord item, EquipmentChange change) {
        public boolean equipped() {
            return change != null;
        }
    }

    public record EquipmentChange(
        String itemName,
        String slotName,
        int enhancementLevel,
        int powerGain,
        int itemPower,
        boolean success
    ) {
    }
}
