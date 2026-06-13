package com.mythicrealm.domain.equipment.infrastructure.persistence;

import com.mythicrealm.domain.equipment.Equipment;
import com.mythicrealm.domain.equipment.EquipmentStats;
import com.mythicrealm.domain.equipment.SlotType;
import com.mythicrealm.domain.equipment.repository.EquipmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/**
 * 装备仓储实现
 */
@Repository
public class EquipmentRepositoryImpl implements EquipmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public EquipmentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Equipment> findByPlayerId(long playerId) {
        List<EquipmentSlotPO> slots = jdbcTemplate.query(
            "SELECT * FROM equipment_slot WHERE player_id = ?",
            (rs, rowNum) -> {
                EquipmentSlotPO po = new EquipmentSlotPO();
                po.setId(rs.getLong("id"));
                po.setPlayerId(rs.getLong("player_id"));
                po.setSlotName(rs.getString("slot_name"));
                po.setItemId(rs.getLong("item_id"));
                return po;
            },
            playerId
        );

        if (slots.isEmpty()) {
            return Optional.empty();
        }

        Map<SlotType, Long> equippedItems = new EnumMap<>(SlotType.class);
        for (EquipmentSlotPO slot : slots) {
            SlotType slotType = SlotType.fromCode(slot.getSlotName());
            equippedItems.put(slotType, slot.getItemId());
        }

        return Optional.of(Equipment.rebuild(playerId, equippedItems));
    }

    @Override
    @Transactional
    public void save(Equipment equipment) {
        long playerId = equipment.getPlayerId();

        // 删除旧的装备槽位数据
        jdbcTemplate.update("DELETE FROM equipment_slot WHERE player_id = ?", playerId);

        // 插入新的装备槽位数据
        Map<SlotType, Long> equippedItems = equipment.getEquippedItems();
        for (Map.Entry<SlotType, Long> entry : equippedItems.entrySet()) {
            jdbcTemplate.update(
                "INSERT INTO equipment_slot (player_id, slot_name, item_id) VALUES (?, ?, ?)",
                playerId,
                entry.getKey().getCode(),
                entry.getValue()
            );
        }
    }

    @Override
    public List<EquipmentStats> findEquippedItemStats(long playerId) {
        return jdbcTemplate.query(
            """
            SELECT ii.attack_bonus, ii.defense_bonus, ii.hp_bonus, ii.mp_bonus,
                   ii.crit_bonus, ii.enhancement_level
            FROM equipment_slot es
            JOIN item_instance ii ON ii.id = es.item_id
            WHERE es.player_id = ?
            """,
            (rs, rowNum) -> new EquipmentStats(
                rs.getInt("attack_bonus"),
                rs.getInt("defense_bonus"),
                rs.getInt("hp_bonus"),
                rs.getInt("mp_bonus"),
                rs.getBigDecimal("crit_bonus"),
                rs.getInt("enhancement_level")
            ),
            playerId
        );
    }

    @Override
    public Optional<EquipmentStats> findItemStats(long itemId) {
        List<EquipmentStats> stats = jdbcTemplate.query(
            """
            SELECT attack_bonus, defense_bonus, hp_bonus, mp_bonus, crit_bonus, enhancement_level
            FROM item_instance
            WHERE id = ?
            """,
            (rs, rowNum) -> new EquipmentStats(
                rs.getInt("attack_bonus"),
                rs.getInt("defense_bonus"),
                rs.getInt("hp_bonus"),
                rs.getInt("mp_bonus"),
                rs.getBigDecimal("crit_bonus"),
                rs.getInt("enhancement_level")
            ),
            itemId
        );
        return stats.isEmpty() ? Optional.empty() : Optional.of(stats.get(0));
    }

    @Override
    public boolean isItemOwnedByPlayer(long itemId, long playerId) {
        List<Long> result = jdbcTemplate.queryForList(
            "SELECT player_id FROM item_instance WHERE id = ?",
            Long.class,
            itemId
        );
        return !result.isEmpty() && result.get(0).equals(playerId);
    }

    @Override
    public Optional<String> findItemType(long itemId) {
        List<String> result = jdbcTemplate.queryForList(
            "SELECT item_type FROM item_instance WHERE id = ?",
            String.class,
            itemId
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
