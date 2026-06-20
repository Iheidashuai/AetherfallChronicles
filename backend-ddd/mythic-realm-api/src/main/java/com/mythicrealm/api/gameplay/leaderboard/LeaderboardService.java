package com.mythicrealm.api.gameplay.leaderboard;

import com.mythicrealm.api.gameplay.combat.CombatStats;
import com.mythicrealm.api.gameplay.combat.CombatPowerService;
import com.mythicrealm.api.gameplay.combat.CombatStatsService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.robot.RobotEquipmentService;
import com.mythicrealm.api.gameplay.skill.SkillService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {
    private static final List<String> SLOT_ORDER = List.of("weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace", "ring1", "ring2");

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final RobotEquipmentService robotEquipmentService;
    private final CombatStatsService combatStatsService;
    private final CombatPowerService combatPowerService;
    private final SkillService skillService;

    public LeaderboardService(
        JdbcTemplate jdbcTemplate,
        InventoryService inventoryService,
        RobotEquipmentService robotEquipmentService,
        CombatStatsService combatStatsService,
        CombatPowerService combatPowerService,
        SkillService skillService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.robotEquipmentService = robotEquipmentService;
        this.combatStatsService = combatStatsService;
        this.combatPowerService = combatPowerService;
        this.skillService = skillService;
    }

    public List<LeaderboardEntry> entries(PlayerRecord player) {
        List<LeaderboardPlayer> players = jdbcTemplate.query(
            """
            SELECT id, account_id, name, profession, level, experience, gold, real_money,
                   wealth_tier_level, wealth_tier, strength, agility, constitution, intelligence,
                   spirit, free_points, title
            FROM player
            WHERE controller_type IN ('player', 'robot')
            """,
            (rs, rowNum) -> new LeaderboardPlayer(mapPlayer(rs), rs.getString("title"))
        );
        Map<Long, Map<String, ItemRecord>> equippedByPlayer = equippedItemsByPlayer(players.stream().map(row -> row.player().id()).toList());
        Map<Long, Integer> skillPowerByPlayer = skillService.skillPowerForPlayers(players.stream().map(LeaderboardPlayer::player).toList());
        List<LeaderboardEntry> entries = new ArrayList<>(players.stream()
            .map(row -> leaderboardEntry(row, player.id(), equippedByPlayer.getOrDefault(row.player().id(), Map.of()), skillPowerByPlayer.getOrDefault(row.player().id(), 0)))
            .toList());
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
                entry.derivedStats(),
                entry.equipmentPower(),
                entry.equipment()
            ));
        }
        return ranked;
    }

    private LeaderboardEntry leaderboardEntry(LeaderboardPlayer row, long viewerPlayerId, Map<String, ItemRecord> equipped, int skillPower) {
        PlayerRecord entryPlayer = row.player();
        List<ItemRecord> equippedItems = equipped.values().stream().toList();
        CombatStats derived = combatStatsService.playerStats(entryPlayer, equippedItems);
        CombatStats base = combatStatsService.playerStats(entryPlayer, List.of());
        int equipmentPower = equippedItems.stream().mapToInt(inventoryService::equipmentPower).sum();
        int power = combatPowerService.combatPower(derived, base, equipmentPower) + skillPower;
        return new LeaderboardEntry(
            0,
            entryPlayer.name(),
            entryPlayer.id() == viewerPlayerId ? "你" : row.title(),
            entryPlayer.profession(),
            entryPlayer.level(),
            power,
            entryPlayer.id() == viewerPlayerId,
            entryPlayer.experience(),
            entryPlayer.gold(),
            entryPlayer.strength(),
            entryPlayer.agility(),
            entryPlayer.constitution(),
            entryPlayer.intelligence(),
            entryPlayer.spirit(),
            entryPlayer.freePoints(),
            DerivedStats.from(derived),
            equipmentPower,
            equipmentSummaries(equipped)
        );
    }

    public List<EquipmentSummary> equipmentForPlayer(PlayerRecord player) {
        var equipped = inventoryService.equippedItems(player.id());
        return SLOT_ORDER.stream()
            .filter(equipped::containsKey)
            .map(slot -> equipmentSummary(slot, equipped.get(slot)))
            .toList();
    }

    public DerivedStats derivedStatsForPlayer(PlayerRecord player) {
        CombatStats stats = combatStatsService.playerStats(player, inventoryService.equippedItems(player.id()).values());
        return DerivedStats.from(stats);
    }

    public int equipmentPowerForPlayer(PlayerRecord player) {
        return equipmentForPlayer(player).stream().mapToInt(EquipmentSummary::power).sum();
    }

    private List<EquipmentSummary> equipmentSummaries(Map<String, ItemRecord> equipped) {
        return SLOT_ORDER.stream()
            .filter(equipped::containsKey)
            .map(slot -> equipmentSummary(slot, equipped.get(slot)))
            .toList();
    }

    private Map<Long, Map<String, ItemRecord>> equippedItemsByPlayer(List<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(playerIds.size(), "?"));
        Map<Long, Map<String, ItemRecord>> equipped = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
            SELECT es.player_id AS owner_player_id, es.slot_name, ii.*, it.item_category, it.stackable,
                   it.effect_type, it.effect_value_json, it.enhance_bonus_rate, it.min_enhance_level,
                   it.max_enhance_level, it.description,
                   COALESCE(sock.socket_attack, 0) AS socket_attack_bonus,
                   COALESCE(sock.socket_defense, 0) AS socket_defense_bonus,
                   COALESCE(sock.socket_resistance, 0) AS socket_resistance_bonus,
                   COALESCE(sock.socket_hp, 0) AS socket_hp_bonus,
                   COALESCE(sock.socket_mp, 0) AS socket_mp_bonus,
                   COALESCE(sock.socket_crit, 0) AS socket_crit_bonus,
                   COALESCE(aff.affix_attack, 0) AS affix_attack_bonus,
                   COALESCE(aff.affix_defense, 0) AS affix_defense_bonus,
                   COALESCE(aff.affix_resistance, 0) AS affix_resistance_bonus,
                   COALESCE(aff.affix_hp, 0) AS affix_hp_bonus,
                   COALESCE(aff.affix_mp, 0) AS affix_mp_bonus,
                   COALESCE(aff.affix_crit, 0) AS affix_crit_bonus
            FROM equipment_slot es
            JOIN item_instance ii ON ii.id = es.item_id
            JOIN item_template it ON it.id = ii.template_id
            LEFT JOIN (
                SELECT s.item_id,
                       SUM(CASE WHEN gt.stat_key = 'attack' THEN ROUND(gt.stat_value) ELSE 0 END) AS socket_attack,
                       SUM(CASE WHEN gt.stat_key = 'defense' THEN ROUND(gt.stat_value) ELSE 0 END) AS socket_defense,
                       SUM(CASE WHEN gt.stat_key = 'resistance' THEN ROUND(gt.stat_value) ELSE 0 END) AS socket_resistance,
                       SUM(CASE WHEN gt.stat_key = 'hp' THEN ROUND(gt.stat_value) ELSE 0 END) AS socket_hp,
                       SUM(CASE WHEN gt.stat_key = 'mp' THEN ROUND(gt.stat_value) ELSE 0 END) AS socket_mp,
                       SUM(CASE WHEN gt.stat_key = 'crit' THEN gt.stat_value ELSE 0 END) AS socket_crit
                FROM equipment_socket s
                JOIN equipment_slot equipped_socket ON equipped_socket.item_id = s.item_id
                LEFT JOIN item_instance gem ON gem.id = s.gem_item_id
                LEFT JOIN gem_template gt ON gt.template_id = gem.template_id
                GROUP BY s.item_id
            ) sock ON sock.item_id = ii.id
            LEFT JOIN (
                SELECT ea.item_id,
                       SUM(CASE WHEN stat_key = 'attack' THEN ROUND(stat_value) ELSE 0 END) AS affix_attack,
                       SUM(CASE WHEN stat_key = 'defense' THEN ROUND(stat_value) ELSE 0 END) AS affix_defense,
                       SUM(CASE WHEN stat_key = 'resistance' THEN ROUND(stat_value) ELSE 0 END) AS affix_resistance,
                       SUM(CASE WHEN stat_key = 'hp' THEN ROUND(stat_value) ELSE 0 END) AS affix_hp,
                       SUM(CASE WHEN stat_key = 'mp' THEN ROUND(stat_value) ELSE 0 END) AS affix_mp,
                       SUM(CASE WHEN stat_key = 'crit' THEN stat_value ELSE 0 END) AS affix_crit
                FROM equipment_affix ea
                JOIN equipment_slot equipped_affix ON equipped_affix.item_id = ea.item_id
                GROUP BY ea.item_id
            ) aff ON aff.item_id = ii.id
            WHERE es.player_id IN (
            """ + placeholders + """
            )
            ORDER BY es.player_id, es.slot_name
            """,
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> equipped
                .computeIfAbsent(rs.getLong("owner_player_id"), ignored -> new LinkedHashMap<>())
                .put(rs.getString("slot_name"), mapItem(rs)),
            playerIds.toArray()
        );
        return equipped;
    }

    private EquipmentSummary equipmentSummary(String slot, ItemRecord item) {
        return new EquipmentSummary(
            item.templateId(),
            slot,
            slotName(slot),
            item.displayName(),
            item.quality(),
            item.requiredLevel(),
            inventoryService.equipmentPower(item),
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

    public List<EquipmentSummary> equipmentForRobot(long robotId, String name, String profession, int level, int power) {
        return robotEquipmentService.equipmentForRobot(robotId, name, profession, level, power);
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
            rs.getLong("real_money"),
            rs.getInt("wealth_tier_level"),
            rs.getString("wealth_tier"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
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
            rs.getInt("enhancement_luck"),
            rs.getInt("refine_level"),
            rs.getString("refine_focus"),
            rs.getInt("ascension_level"),
            rs.getInt("ascension_luck"),
            rs.getInt("socket_attack_bonus"),
            rs.getInt("socket_defense_bonus"),
            rs.getInt("socket_resistance_bonus"),
            rs.getInt("socket_hp_bonus"),
            rs.getInt("socket_mp_bonus"),
            rs.getDouble("socket_crit_bonus"),
            rs.getInt("affix_attack_bonus"),
            rs.getInt("affix_defense_bonus"),
            rs.getInt("affix_resistance_bonus"),
            rs.getInt("affix_hp_bonus"),
            rs.getInt("affix_mp_bonus"),
            rs.getDouble("affix_crit_bonus"),
            List.of(),
            List.of(),
            rs.getString("description") == null ? "" : rs.getString("description")
        );
    }

    private record LeaderboardPlayer(PlayerRecord player, String title) {
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
        DerivedStats derivedStats,
        int equipmentPower,
        List<EquipmentSummary> equipment
    ) {
    }

    public record DerivedStats(
        int maxHp,
        int maxMp,
        int attackPower,
        int armor,
        int resistance,
        int speed,
        double accuracy,
        double evasion,
        double critChance,
        double critDamage
    ) {
        static DerivedStats from(CombatStats stats) {
            return new DerivedStats(
                stats.maxHp(),
                stats.maxMp(),
                stats.attackPower(),
                stats.armor(),
                stats.resistance(),
                stats.speed(),
                stats.accuracy(),
                stats.evasion(),
                stats.critChance(),
                stats.critDamage()
            );
        }
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
        int resistanceBonus,
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
