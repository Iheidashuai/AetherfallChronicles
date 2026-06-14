package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.leaderboard.LeaderboardService.EquipmentSummary;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
    private final RechargeService rechargeService;

    public RobotEquipmentService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        InventoryService inventoryService,
        PlayerService playerService,
        RechargeService rechargeService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.inventoryService = inventoryService;
        this.playerService = playerService;
        this.rechargeService = rechargeService;
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

    public EnhancementOpportunity enhancementOpportunity(PlayerRecord robot) {
        ensureEquipment(robot);
        EquippedItem target = bestEnhancementTarget(enhancementCandidates(robot));
        if (target == null) {
            return null;
        }
        long cost = enhancementCost(target.item());
        boolean affordableNow = robot.gold() >= cost;
        boolean affordableWithRecharge = robot.gold() + robot.realMoney() * RechargeService.GOLD_PER_RMB >= cost;
        return new EnhancementOpportunity(
            target.item().id(),
            target.item().displayName(),
            slotName(target.slot()),
            target.item().enhancementLevel(),
            target.item().enhancementLevel() + 1,
            cost,
            affordableNow,
            affordableWithRecharge,
            itemPower(target.item())
        );
    }

    public EquipmentChange enhancePlannedEquipment(PlayerRecord robot, Random random) {
        ensureEquipment(robot);
        EquippedItem target = bestEnhancementTarget(enhancementCandidates(robot));
        if (target == null) {
            return null;
        }
        return enhanceEquipment(robot, target);
    }

    public EquipmentChange enhanceRandomEquipment(PlayerRecord robot, Random random) {
        ensureEquipment(robot);
        List<EquippedItem> rows = enhancementCandidates(robot);
        if (rows.isEmpty()) {
            return null;
        }
        return enhanceEquipment(robot, rows.get(random.nextInt(rows.size())));
    }

    private List<EquippedItem> enhancementCandidates(PlayerRecord robot) {
        return jdbcTemplate.query(
            """
            SELECT es.slot_name, ii.*
            FROM equipment_slot es
            JOIN item_instance ii ON ii.id = es.item_id
            WHERE es.player_id = ? AND ii.enhancement_level < 15
            """,
            (rs, rowNum) -> new EquippedItem(rs.getString("slot_name"), mapItem(rs)),
            robot.id()
        );
    }

    private EquippedItem bestEnhancementTarget(List<EquippedItem> candidates) {
        return candidates.stream()
            .max(Comparator
                .comparingInt((EquippedItem item) -> plannedEnhancementValue(item.item()))
                .thenComparing(item -> -item.item().enhancementLevel())
                .thenComparing(item -> -SLOT_ORDER.indexOf(item.slot())))
            .orElse(null);
    }

    private int plannedEnhancementValue(ItemRecord item) {
        int levelPressure = Math.max(0, 15 - item.enhancementLevel()) * 10;
        return itemPower(item) + qualityRank(item.quality()) * 80 + levelPressure;
    }

    private EquipmentChange enhanceEquipment(PlayerRecord robot, EquippedItem equipped) {
        long cost = enhancementCost(equipped.item());
        RechargeService.RechargeResult recharge = null;
        PlayerRecord fundedRobot = robot;
        if (fundedRobot.gold() < cost) {
            recharge = rechargeService.rechargeForGoldNeed(
                    fundedRobot.id(),
                    cost,
                    "robot_enhance",
                    "强化【" + equipped.item().displayName() + "】"
                )
                .orElse(null);
            if (recharge == null) {
                return null;
            }
            fundedRobot = recharge.player();
            if (fundedRobot.gold() < cost) {
                return null;
            }
        }
        int beforePower = inventoryService.combatPower(fundedRobot);
        InventoryService.EnhanceResult result;
        try {
            result = inventoryService.enhance(
                fundedRobot,
                equipped.item().id(),
                selectEnhancementStones(fundedRobot.id(), equipped.item().enhancementLevel() + 1)
            );
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
            result.success(),
            result.usedStoneCount(),
            recharge != null,
            recharge == null ? 0 : recharge.rmbAmount(),
            recharge == null ? 0 : recharge.goldAmount()
        );
    }

    private List<Long> selectEnhancementStones(long playerId, int nextLevel) {
        List<ItemRecord> candidates = inventoryService.inventoryItems(playerId).stream()
            .filter(item -> "enhancementStone".equals(item.effectType()))
            .filter(item -> item.minEnhanceLevel() <= nextLevel && item.maxEnhanceLevel() >= nextLevel)
            .sorted(Comparator
                .<ItemRecord>comparingDouble(ItemRecord::enhanceBonusRate)
                .reversed()
                .thenComparingInt(ItemRecord::sellPrice)
                .thenComparing(ItemRecord::templateId))
            .toList();
        List<Long> selected = new ArrayList<>();
        for (ItemRecord stone : candidates) {
            int usable = Math.max(1, stone.quantity());
            for (int index = 0; index < usable && selected.size() < 3; index++) {
                selected.add(stone.id());
            }
            if (selected.size() >= 3) {
                break;
            }
        }
        return selected;
    }

    private long enhancementCost(ItemRecord item) {
        int targetLevel = item.enhancementLevel() + 1;
        int itemLevel = Math.max(1, item.requiredLevel());
        return (long) itemLevel * itemLevel * targetLevel * 10;
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
            item.resistanceBonus(),
            item.hpBonus(),
            item.mpBonus(),
            item.critBonus().doubleValue(),
            item.sellPrice(),
            item.enhancementLevel(),
            item.enhancementLuck(),
            originFor(item.templateId())
        );
    }

    private String originFor(String templateId) {
        int tier = itemTier(templateId);
        if (tier >= 37) {
            return "副本掉落 · 龙眠王庭";
        }
        if (tier >= 31) {
            return "副本掉落 · 星陨荒原";
        }
        if (tier >= 25) {
            return "副本掉落 · 黑曜山脉";
        }
        if (tier >= 19) {
            return "副本掉落 · 古堡回廊";
        }
        if (tier >= 7) {
            return "副本掉落 · 腐沼边境";
        }
        if (tier >= 1) {
            return "副本掉落 · 蛛影林地";
        }
        return "冒险者商会流通";
    }

    private int itemTier(String templateId) {
        int start = templateId.indexOf("eq_t");
        if (start < 0) {
            return 0;
        }
        int index = start + 4;
        StringBuilder value = new StringBuilder();
        while (index < templateId.length() && Character.isDigit(templateId.charAt(index))) {
            value.append(templateId.charAt(index));
            index++;
        }
        if (value.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value.toString());
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
        return inventoryService.equipmentPower(item);
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
            case "immortal" -> 6;
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
            rs.getInt("resistance_bonus"),
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

    public record EnhancementOpportunity(
        long itemId,
        String itemName,
        String slotName,
        int currentLevel,
        int nextLevel,
        long cost,
        boolean affordableNow,
        boolean affordableWithRecharge,
        int itemPower
    ) {
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
        boolean success,
        int usedStoneCount,
        boolean recharged,
        long rechargeRmb,
        long rechargeGold
    ) {
        public EquipmentChange(String itemName, String slotName, int enhancementLevel, int powerGain, int itemPower, boolean success) {
            this(itemName, slotName, enhancementLevel, powerGain, itemPower, success, 0, false, 0, 0);
        }
    }
}
