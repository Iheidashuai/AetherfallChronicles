package com.mythicrealm.api.gameplay.build;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.endgame.EndgameRiftService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.skill.SkillService;
import com.mythicrealm.api.gameplay.skill.SkillService.SkillSnapshot;
import com.mythicrealm.api.gameplay.skill.SkillService.SkillView;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuildService {
    private static final Set<String> STRATEGIES = Set.of("balanced", "aggressive", "survival", "speed");
    private static final Set<String> TRIGGERS = Set.of("opener", "default", "burst", "execute", "defensive", "heal");
    private static final Set<String> EQUIPMENT_SLOTS = Set.of("weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace", "ring1", "ring2");
    private static final int MAX_TALENTS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final SkillService skillService;
    private final EndgameRiftService riftService;
    private final BuildCombatPlanService buildCombatPlanService;
    private final PlayerService playerService;

    public BuildService(
        JdbcTemplate jdbcTemplate,
        InventoryService inventoryService,
        SkillService skillService,
        EndgameRiftService riftService,
        BuildCombatPlanService buildCombatPlanService,
        PlayerService playerService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.skillService = skillService;
        this.riftService = riftService;
        this.buildCombatPlanService = buildCombatPlanService;
        this.playerService = playerService;
    }

    public BuildSnapshot snapshot(PlayerRecord player) {
        SkillSnapshot skills = skillService.snapshot(player);
        List<ItemRecord> equipment = availableEquipment(player);
        List<BuildPresetView> presets = presets(player.profession());
        List<PlayerBuildView> builds = builds(player.id(), equipment, skills.skills());
        PlayerBuildView active = builds.stream().filter(PlayerBuildView::active).findFirst().orElse(null);
        int tier = Math.max(1, riftService.progress(player.id()).bestTier() + 1);
        return new BuildSnapshot(
            player,
            presets,
            builds,
            active,
            equipment,
            skills.skills(),
            tier,
            riftService.minimumPower(tier)
        );
    }

    @Transactional
    public BuildSnapshot create(PlayerRecord player, BuildMutationRequest request) {
        String name = cleanName(request == null ? null : request.name(), "自定义构筑 " + (buildCount(player.id()) + 1));
        long buildId = insertBuild(player, name, null, "custom", strategyOrDefault(request == null ? null : request.strategy()), "balanced");
        applyMutation(player, buildId, request);
        return snapshot(playerService.requireById(player.id()));
    }

    @Transactional
    public BuildSnapshot copyPreset(PlayerRecord player, String presetId) {
        PresetRow preset = requirePreset(player, presetId);
        long buildId = insertBuild(player, preset.name(), preset.id(), preset.archetype(), preset.strategy(), preset.refineFocus());
        jdbcTemplate.update(
            """
            INSERT INTO player_build_skill_slot (build_id, slot_index, trigger_kind, skill_id)
            SELECT ?, slot_index, trigger_kind, skill_id FROM build_preset_skill_slot WHERE preset_id = ?
            """,
            buildId,
            preset.id()
        );
        jdbcTemplate.update(
            """
            INSERT INTO player_build_talent (build_id, node_id)
            SELECT ?, node_id FROM build_preset_talent WHERE preset_id = ? ORDER BY sort_order LIMIT ?
            """,
            buildId,
            preset.id(),
            MAX_TALENTS
        );
        for (Map.Entry<String, ItemRecord> entry : inventoryService.equippedItems(player.id()).entrySet()) {
            jdbcTemplate.update(
                "INSERT INTO player_build_equipment_slot (build_id, slot_name, item_id, preferred_item_type) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE item_id = VALUES(item_id)",
                buildId,
                entry.getKey(),
                entry.getValue().id(),
                entry.getValue().itemType()
            );
        }
        return snapshot(playerService.requireById(player.id()));
    }

    @Transactional
    public BuildSnapshot update(PlayerRecord player, long buildId, BuildMutationRequest request) {
        requireBuild(player.id(), buildId);
        applyMutation(player, buildId, request);
        return snapshot(playerService.requireById(player.id()));
    }

    @Transactional
    public BuildActivationResult activate(PlayerRecord player, long buildId) {
        PlayerBuildRow build = requireBuild(player.id(), buildId);
        List<String> warnings = new ArrayList<>();
        Map<String, Long> configured = equipmentSlots(buildId).entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        List<ItemRecord> ownedEquipment = availableEquipment(player);
        Map<Long, ItemRecord> ownedById = ownedEquipment.stream().collect(Collectors.toMap(ItemRecord::id, item -> item));
        Map<String, ItemRecord> selected = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : configured.entrySet()) {
            if (!EQUIPMENT_SLOTS.contains(entry.getKey())) {
                warnings.add("未知装备槽位已跳过：" + entry.getKey());
                continue;
            }
            ItemRecord item = ownedById.get(entry.getValue());
            if (item == null) {
                warnings.add("装备已不存在：" + entry.getValue());
                continue;
            }
            selected.put(entry.getKey(), item);
        }

        Map<String, ItemRecord> before = inventoryService.equippedItems(player.id());
        int beforePower = inventoryService.combatPower(player);
        jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ?", player.id());
        for (ItemRecord item : selected.values()) {
            jdbcTemplate.update("DELETE FROM inventory_slot WHERE player_id = ? AND item_id = ?", player.id(), item.id());
        }
        for (Map.Entry<String, ItemRecord> entry : selected.entrySet()) {
            jdbcTemplate.update(
                "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
                player.id(),
                entry.getKey(),
                entry.getValue().id()
            );
        }
        Set<Long> selectedIds = selected.values().stream().map(ItemRecord::id).collect(Collectors.toSet());
        for (ItemRecord oldItem : before.values()) {
            if (!selectedIds.contains(oldItem.id()) && inventoryService.inventorySlot(player.id(), oldItem.id()).isEmpty()) {
                jdbcTemplate.update(
                    "INSERT INTO inventory_slot (player_id, slot_index, item_id) VALUES (?, ?, ?)",
                    player.id(),
                    inventoryService.nextFreeSlot(player.id()),
                    oldItem.id()
                );
            }
        }

        warnMissingSkills(player, buildId, warnings);
        jdbcTemplate.update("UPDATE player_build SET active = FALSE WHERE player_id = ?", player.id());
        jdbcTemplate.update("UPDATE player_build SET active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND player_id = ?", buildId, player.id());
        int afterPower = inventoryService.combatPower(playerService.requireById(player.id()));
        return new BuildActivationResult(buildId, build.name(), selected.size(), skillSlots(buildId).size(), beforePower, afterPower, warnings, snapshot(playerService.requireById(player.id())));
    }

    @Transactional
    public EndgameRiftService.RiftSimulationResult simulate(PlayerRecord player, long buildId, int tier) {
        requireBuild(player.id(), buildId);
        return riftService.simulate(player, Math.max(1, tier), buildId, buildCombatPlanService.planForBuild(player, buildId));
    }

    private void applyMutation(PlayerRecord player, long buildId, BuildMutationRequest request) {
        if (request == null) {
            return;
        }
        String strategy = strategyOrDefault(request.strategy());
        String refineFocus = request.refineFocus() == null || request.refineFocus().isBlank() ? "balanced" : request.refineFocus().trim();
        jdbcTemplate.update(
            "UPDATE player_build SET name = ?, strategy = ?, refine_focus = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND player_id = ?",
            cleanName(request.name(), requireBuild(player.id(), buildId).name()),
            strategy,
            refineFocus,
            buildId,
            player.id()
        );
        if (request.equipmentSlots() != null) {
            jdbcTemplate.update("DELETE FROM player_build_equipment_slot WHERE build_id = ?", buildId);
            for (EquipmentSlotMutation slot : request.equipmentSlots()) {
                if (slot == null || slot.slotName() == null || slot.slotName().isBlank()) {
                    continue;
                }
                Long itemId = slot.itemId();
                if (itemId != null && itemId > 0) {
                    ItemRecord item = inventoryService.requireOwnedItem(player.id(), itemId);
                    if (!item.equipment()) {
                        throw ApiException.badRequest("构筑装备槽只能选择装备");
                    }
                }
                jdbcTemplate.update(
                    "INSERT INTO player_build_equipment_slot (build_id, slot_name, item_id, preferred_item_type) VALUES (?, ?, ?, ?)",
                    buildId,
                    slot.slotName(),
                    itemId == null || itemId <= 0 ? null : itemId,
                    slot.preferredItemType() == null ? "" : slot.preferredItemType()
                );
            }
        }
        if (request.skillSlots() != null) {
            jdbcTemplate.update("DELETE FROM player_build_skill_slot WHERE build_id = ?", buildId);
            for (SkillSlotMutation slot : request.skillSlots()) {
                if (slot == null || slot.skillId() == null || slot.skillId().isBlank()) {
                    continue;
                }
                String trigger = TRIGGERS.contains(slot.triggerKind()) ? slot.triggerKind() : "default";
                jdbcTemplate.update(
                    "INSERT INTO player_build_skill_slot (build_id, slot_index, trigger_kind, skill_id) VALUES (?, ?, ?, ?)",
                    buildId,
                    Math.max(0, slot.slotIndex()),
                    trigger,
                    slot.skillId()
                );
            }
        }
        if (request.talents() != null) {
            List<String> talents = request.talents().stream().filter(value -> value != null && !value.isBlank()).distinct().limit(MAX_TALENTS).toList();
            validateTalents(buildId, talents);
            jdbcTemplate.update("DELETE FROM player_build_talent WHERE build_id = ?", buildId);
            for (String nodeId : talents) {
                jdbcTemplate.update("INSERT INTO player_build_talent (build_id, node_id) VALUES (?, ?)", buildId, nodeId);
            }
        }
    }

    private List<ItemRecord> availableEquipment(PlayerRecord player) {
        List<ItemRecord> items = new ArrayList<>(inventoryService.inventoryItems(player.id()).stream().filter(ItemRecord::equipment).toList());
        items.addAll(inventoryService.equippedItems(player.id()).values());
        return items.stream()
            .sorted(Comparator.comparingInt(inventoryService::equipmentPower).reversed())
            .toList();
    }

    private List<BuildPresetView> presets(String profession) {
        return jdbcTemplate.query(
            "SELECT * FROM build_preset WHERE enabled = TRUE AND profession = ? ORDER BY sort_order",
            (rs, rowNum) -> new BuildPresetView(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("profession"),
                rs.getString("archetype"),
                rs.getString("strategy"),
                rs.getString("description"),
                rs.getString("refine_focus"),
                talentsForPreset(rs.getString("id")),
                presetSkillSlots(rs.getString("id"))
            ),
            profession
        );
    }

    private List<PlayerBuildView> builds(long playerId, List<ItemRecord> equipment, List<SkillView> skills) {
        return jdbcTemplate.query(
            "SELECT * FROM player_build WHERE player_id = ? ORDER BY active DESC, updated_at DESC, id DESC",
            (rs, rowNum) -> {
                long buildId = rs.getLong("id");
                List<BuildEquipmentSlot> equipmentSlots = buildEquipmentSlots(buildId);
                List<BuildSkillSlot> skillSlots = skillSlots(buildId);
                List<String> talents = selectedTalents(buildId);
                return new PlayerBuildView(
                    buildId,
                    rs.getString("name"),
                    rs.getString("source_preset_id"),
                    rs.getString("profession"),
                    rs.getString("archetype"),
                    rs.getString("strategy"),
                    rs.getString("refine_focus"),
                    rs.getBoolean("active"),
                    equipmentSlots,
                    skillSlots,
                    talents,
                    score(equipmentSlots, skillSlots, talents, equipment, skills)
                );
            },
            playerId
        );
    }

    private BuildScore score(List<BuildEquipmentSlot> equipmentSlots, List<BuildSkillSlot> skillSlots, List<String> talents, List<ItemRecord> equipment, List<SkillView> skills) {
        Set<Long> selectedEquipment = equipmentSlots.stream().map(BuildEquipmentSlot::itemId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Set<String> selectedSkills = skillSlots.stream().map(BuildSkillSlot::skillId).filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        Map<Long, ItemRecord> equipmentById = equipment.stream().collect(Collectors.toMap(ItemRecord::id, item -> item, (a, b) -> a));
        Map<String, SkillView> skillById = skills.stream().collect(Collectors.toMap(SkillView::id, skill -> skill));
        int damage = clampScore(selectedEquipment.stream().map(equipmentById::get).filter(item -> item != null).mapToInt(item -> item.enhancedAttackBonus() * 5 + (int) Math.round(item.enhancedCritBonus() * 500)).sum() + selectedSkills.size() * 8 + talents.size() * 4);
        int defense = clampScore(selectedEquipment.stream().map(equipmentById::get).filter(item -> item != null).mapToInt(item -> item.enhancedDefenseBonus() * 4 + item.enhancedResistanceBonus() * 4 + item.enhancedHpBonus() / 8).sum() + talents.size() * 3);
        int sustain = clampScore((int) selectedSkills.stream().map(skillById::get).filter(skill -> skill != null && ("heal".equals(skill.effectType()) || "shield".equals(skill.effectType()))).count() * 28 + talents.size() * 4);
        int speed = clampScore(selectedSkills.contains("skill_ranger_quickshot") || selectedSkills.contains("skill_ranger_flurry") ? 76 : 42 + talents.size() * 5);
        int rift = clampScore((damage + defense + sustain) / 3 + talents.size() * 6);
        int completion = clampScore(selectedEquipment.size() * 8 + selectedSkills.size() * 12 + talents.size() * 10);
        return new BuildScore(damage, defense, sustain, speed, rift, completion);
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private long insertBuild(PlayerRecord player, String name, String presetId, String archetype, String strategy, String refineFocus) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO player_build (player_id, name, source_preset_id, profession, archetype, strategy, refine_focus)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, player.id());
            ps.setString(2, name);
            ps.setString(3, presetId);
            ps.setString(4, player.profession());
            ps.setString(5, archetype);
            ps.setString(6, strategy);
            ps.setString(7, refineFocus);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void warnMissingSkills(PlayerRecord player, long buildId, List<String> warnings) {
        Set<String> learned = jdbcTemplate.queryForList("SELECT skill_id FROM player_skill WHERE player_id = ?", String.class, player.id()).stream().collect(Collectors.toSet());
        for (BuildSkillSlot slot : skillSlots(buildId)) {
            if (slot.skillId() != null && !slot.skillId().isBlank() && !learned.contains(slot.skillId())) {
                warnings.add("技能未学习，战斗中会跳过：" + slot.skillName());
            }
        }
    }

    private void validateTalents(long buildId, List<String> talents) {
        PlayerBuildRow build = requireBuildById(buildId);
        if (build.sourcePresetId() == null || build.sourcePresetId().isBlank()) {
            return;
        }
        Set<String> valid = talentsForPreset(build.sourcePresetId()).stream().map(BuildTalent::nodeId).collect(Collectors.toSet());
        for (String talent : talents) {
            if (!valid.contains(talent)) {
                throw ApiException.badRequest("天赋节点不属于当前流派：" + talent);
            }
        }
    }

    private PresetRow requirePreset(PlayerRecord player, String presetId) {
        return jdbcTemplate.query(
            "SELECT * FROM build_preset WHERE id = ? AND enabled = TRUE",
            (rs, rowNum) -> new PresetRow(rs.getString("id"), rs.getString("name"), rs.getString("profession"), rs.getString("archetype"), rs.getString("strategy"), rs.getString("refine_focus")),
            presetId
        ).stream().filter(preset -> preset.profession().equals(player.profession())).findFirst().orElseThrow(() -> ApiException.notFound("构筑预设不存在或职业不匹配"));
    }

    private PlayerBuildRow requireBuild(long playerId, long buildId) {
        return jdbcTemplate.query(
            "SELECT * FROM player_build WHERE player_id = ? AND id = ?",
            (rs, rowNum) -> new PlayerBuildRow(rs.getLong("id"), rs.getString("name"), rs.getString("source_preset_id")),
            playerId,
            buildId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("构筑不存在"));
    }

    private PlayerBuildRow requireBuildById(long buildId) {
        return jdbcTemplate.query(
            "SELECT * FROM player_build WHERE id = ?",
            (rs, rowNum) -> new PlayerBuildRow(rs.getLong("id"), rs.getString("name"), rs.getString("source_preset_id")),
            buildId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("构筑不存在"));
    }

    private int buildCount(long playerId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_build WHERE player_id = ?", Integer.class, playerId);
        return count == null ? 0 : count;
    }

    private String cleanName(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? fallback : clean.substring(0, Math.min(64, clean.length()));
    }

    private String strategyOrDefault(String value) {
        return STRATEGIES.contains(value) ? value : "balanced";
    }

    private List<BuildTalent> talentsForPreset(String presetId) {
        return jdbcTemplate.query(
            "SELECT * FROM build_preset_talent WHERE preset_id = ? ORDER BY sort_order",
            (rs, rowNum) -> new BuildTalent(rs.getString("node_id"), rs.getString("name"), rs.getString("description"), rs.getString("stat_key"), rs.getDouble("stat_value")),
            presetId
        );
    }

    private List<BuildSkillSlot> presetSkillSlots(String presetId) {
        return jdbcTemplate.query(
            """
            SELECT bs.slot_index, bs.trigger_kind, bs.skill_id, st.name AS skill_name
            FROM build_preset_skill_slot bs
            JOIN skill_template st ON st.id = bs.skill_id
            WHERE bs.preset_id = ?
            ORDER BY bs.slot_index
            """,
            (rs, rowNum) -> new BuildSkillSlot(rs.getInt("slot_index"), rs.getString("trigger_kind"), rs.getString("skill_id"), rs.getString("skill_name"), true),
            presetId
        );
    }

    private Map<String, Long> equipmentSlots(long buildId) {
        return buildEquipmentSlots(buildId).stream()
            .filter(slot -> slot.itemId() != null && slot.itemId() > 0)
            .collect(Collectors.toMap(BuildEquipmentSlot::slotName, BuildEquipmentSlot::itemId, (a, b) -> a, LinkedHashMap::new));
    }

    private List<BuildEquipmentSlot> buildEquipmentSlots(long buildId) {
        return jdbcTemplate.query(
            "SELECT * FROM player_build_equipment_slot WHERE build_id = ? ORDER BY slot_name",
            (rs, rowNum) -> new BuildEquipmentSlot(rs.getString("slot_name"), nullableLong(rs, "item_id"), rs.getString("preferred_item_type")),
            buildId
        );
    }

    private List<BuildSkillSlot> skillSlots(long buildId) {
        return jdbcTemplate.query(
            """
            SELECT bs.slot_index, bs.trigger_kind, bs.skill_id, st.name AS skill_name, ps.skill_rank
            FROM player_build_skill_slot bs
            LEFT JOIN skill_template st ON st.id = bs.skill_id
            LEFT JOIN player_build b ON b.id = bs.build_id
            LEFT JOIN player_skill ps ON ps.player_id = b.player_id AND ps.skill_id = bs.skill_id
            WHERE bs.build_id = ?
            ORDER BY bs.slot_index
            """,
            (rs, rowNum) -> new BuildSkillSlot(rs.getInt("slot_index"), rs.getString("trigger_kind"), rs.getString("skill_id"), rs.getString("skill_name"), rs.getInt("skill_rank") > 0),
            buildId
        );
    }

    private List<String> selectedTalents(long buildId) {
        return jdbcTemplate.queryForList("SELECT node_id FROM player_build_talent WHERE build_id = ? ORDER BY node_id", String.class, buildId);
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record PresetRow(String id, String name, String profession, String archetype, String strategy, String refineFocus) {
    }

    private record PlayerBuildRow(long id, String name, String sourcePresetId) {
    }

    public record BuildSnapshot(
        PlayerRecord player,
        List<BuildPresetView> presets,
        List<PlayerBuildView> builds,
        PlayerBuildView activeBuild,
        List<ItemRecord> availableEquipment,
        List<SkillView> availableSkills,
        int suggestedTier,
        int suggestedMinimumPower
    ) {
    }

    public record BuildPresetView(String id, String name, String profession, String archetype, String strategy, String description, String refineFocus, List<BuildTalent> talents, List<BuildSkillSlot> skillSlots) {
    }

    public record PlayerBuildView(long id, String name, String sourcePresetId, String profession, String archetype, String strategy, String refineFocus, boolean active, List<BuildEquipmentSlot> equipmentSlots, List<BuildSkillSlot> skillSlots, List<String> talents, BuildScore score) {
    }

    public record BuildEquipmentSlot(String slotName, Long itemId, String preferredItemType) {
    }

    public record BuildSkillSlot(int slotIndex, String triggerKind, String skillId, String skillName, boolean learned) {
    }

    public record BuildTalent(String nodeId, String name, String description, String statKey, double statValue) {
    }

    public record BuildScore(int damage, int defense, int sustain, int speed, int rift, int completion) {
    }

    public record BuildActivationResult(long buildId, String buildName, int appliedEquipmentCount, int configuredSkillCount, int beforePower, int afterPower, List<String> warnings, BuildSnapshot snapshot) {
    }

    public record BuildMutationRequest(String name, String strategy, String refineFocus, List<EquipmentSlotMutation> equipmentSlots, List<SkillSlotMutation> skillSlots, List<String> talents) {
    }

    public record EquipmentSlotMutation(String slotName, Long itemId, String preferredItemType) {
    }

    public record SkillSlotMutation(int slotIndex, String triggerKind, String skillId) {
    }
}
