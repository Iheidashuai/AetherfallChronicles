package com.mythicrealm.api.gameplay.leaderboard;

import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.robot.RobotEquipmentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {
    private static final List<String> SLOT_ORDER = List.of("weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace", "ring1", "ring2");

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final RobotEquipmentService robotEquipmentService;

    public LeaderboardService(JdbcTemplate jdbcTemplate, InventoryService inventoryService, RobotEquipmentService robotEquipmentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.robotEquipmentService = robotEquipmentService;
    }

    public List<LeaderboardEntry> entries(PlayerRecord player) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        entries.addAll(jdbcTemplate.query(
            """
            SELECT id, account_id, name, profession, level, experience, gold, strength, agility,
                   constitution, intelligence, spirit, free_points, controller_type, title
            FROM player
            WHERE controller_type IN ('player', 'robot')
            """,
            (rs, rowNum) -> {
                PlayerRecord entryPlayer = mapPlayer(rs);
                boolean self = entryPlayer.id() == player.id();
                boolean robot = "robot".equals(rs.getString("controller_type"));
                return new LeaderboardEntry(
                    0,
                    entryPlayer.name(),
                    self ? "你" : rs.getString("title"),
                    entryPlayer.profession(),
                    entryPlayer.level(),
                    inventoryService.combatPower(entryPlayer),
                    self,
                    entryPlayer.experience(),
                    entryPlayer.gold(),
                    entryPlayer.strength(),
                    entryPlayer.agility(),
                    entryPlayer.constitution(),
                    entryPlayer.intelligence(),
                    entryPlayer.spirit(),
                    entryPlayer.freePoints(),
                    robot
                        ? robotEquipmentService.equipmentForRobot(entryPlayer.id(), entryPlayer.name(), entryPlayer.profession(), entryPlayer.level(), inventoryService.combatPower(entryPlayer))
                        : equipmentForPlayer(entryPlayer)
                );
            }
        ));
        entries.sort(Comparator.comparingInt(LeaderboardEntry::power).reversed());
        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            ranked.add(new LeaderboardEntry(
                i + 1,
                entry.name(),
                entry.title(),
                entry.profession(),
                entry.level(),
                entry.power(),
                entry.player(),
                entry.experience(),
                entry.gold(),
                entry.strength(),
                entry.agility(),
                entry.constitution(),
                entry.intelligence(),
                entry.spirit(),
                entry.freePoints(),
                entry.equipment()
            ));
        }
        return ranked;
    }

    public List<EquipmentSummary> equipmentForPlayer(PlayerRecord player) {
        var equipped = inventoryService.equippedItems(player.id());
        return SLOT_ORDER.stream()
            .filter(equipped::containsKey)
            .map(slot -> equipmentSummary(slot, equipped.get(slot)))
            .toList();
    }

    private EquipmentSummary equipmentSummary(String slot, ItemRecord item) {
        int power = item.enhancedAttackBonus() * 12
            + item.enhancedDefenseBonus() * 8
            + item.enhancedHpBonus() / 2
            + item.enhancedMpBonus() / 2
            + item.enhancementLevel() * 16;
        return new EquipmentSummary(
            item.templateId(),
            slot,
            slotName(slot),
            item.displayName(),
            item.quality(),
            item.requiredLevel(),
            Math.max(1, power),
            item.attackBonus(),
            item.defenseBonus(),
            item.hpBonus(),
            item.mpBonus(),
            item.critBonus().doubleValue(),
            item.sellPrice(),
            item.enhancementLevel(),
            item.enhancementLuck(),
            originFor(item.templateId())
        );
    }

    public List<EquipmentSummary> equipmentForRobot(long robotId, String name, String profession, int level, int power) {
        return robotEquipmentService.equipmentForRobot(robotId, name, profession, level, power);
    }

    private String originFor(String templateId) {
        return "角色装备栏 · " + templateId;
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

    private PlayerRecord mapPlayer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlayerRecord(
            rs.getLong("id"),
            rs.getObject("account_id") == null ? 0 : rs.getLong("account_id"),
            rs.getString("name"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getInt("experience"),
            rs.getLong("gold"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
    }

    public record LeaderboardEntry(
        int rank,
        String name,
        String title,
        String profession,
        int level,
        int power,
        boolean player,
        int experience,
        long gold,
        int strength,
        int agility,
        int constitution,
        int intelligence,
        int spirit,
        int freePoints,
        List<EquipmentSummary> equipment
    ) {
    }

    public record EquipmentSummary(
        String templateId,
        String slot,
        String slotName,
        String name,
        String quality,
        int level,
        int power,
        int attackBonus,
        int defenseBonus,
        int hpBonus,
        int mpBonus,
        double critBonus,
        int sellPrice,
        int enhancementLevel,
        int enhancementLuck,
        String origin
    ) {
    }
}
