package com.mythicrealm.domain.equipment.application;

import com.mythicrealm.domain.equipment.CombatPower;
import com.mythicrealm.domain.equipment.Equipment;
import com.mythicrealm.domain.equipment.EquipmentStats;
import com.mythicrealm.domain.equipment.SlotType;
import com.mythicrealm.domain.equipment.application.command.EquipCommand;
import com.mythicrealm.domain.equipment.application.command.UnequipCommand;
import com.mythicrealm.domain.equipment.repository.EquipmentRepository;
import com.mythicrealm.domain.equipment.service.CombatPowerCalculator;
import java.util.List;
import java.util.Map;

/**
 * 装备应用服务
 */
public class EquipmentApplicationService {
    private final EquipmentRepository equipmentRepository;
    private final CombatPowerCalculator combatPowerCalculator;

    public EquipmentApplicationService(
        EquipmentRepository equipmentRepository,
        CombatPowerCalculator combatPowerCalculator
    ) {
        this.equipmentRepository = equipmentRepository;
        this.combatPowerCalculator = combatPowerCalculator;
    }

    /**
     * 装备物品
     */
    public EquipResult equip(EquipCommand command) {
        // 验证物品所有权
        if (!equipmentRepository.isItemOwnedByPlayer(command.itemId(), command.playerId())) {
            throw new IllegalArgumentException("物品不属于当前玩家");
        }

        // 获取物品类型
        String itemType = equipmentRepository.findItemType(command.itemId())
            .orElseThrow(() -> new IllegalArgumentException("物品不存在"));

        // 获取或创建装备聚合
        Equipment equipment = equipmentRepository.findByPlayerId(command.playerId())
            .orElse(Equipment.create(command.playerId()));

        // 执行装备操作
        Long replacedItemId = equipment.equip(itemType, command.itemId());

        // 保存装备聚合
        equipmentRepository.save(equipment);

        return new EquipResult(replacedItemId);
    }

    /**
     * 卸下装备
     */
    public UnequipResult unequip(UnequipCommand command) {
        // 获取装备聚合
        Equipment equipment = equipmentRepository.findByPlayerId(command.playerId())
            .orElseThrow(() -> new IllegalArgumentException("未找到玩家的装备数据"));

        // 验证物品已装备
        if (!equipment.isEquipped(command.itemId())) {
            throw new IllegalArgumentException("物品未装备");
        }

        // 执行卸下操作
        SlotType slotType = equipment.unequip(command.itemId());

        // 保存装备聚合
        equipmentRepository.save(equipment);

        return new UnequipResult(slotType);
    }

    /**
     * 获取已装备的物品
     */
    public Map<SlotType, Long> getEquippedItems(long playerId) {
        return equipmentRepository.findByPlayerId(playerId)
            .map(Equipment::getEquippedItems)
            .orElseGet(Map::of);
    }

    /**
     * 计算战斗力
     */
    public CombatPower calculateCombatPower(long playerId, CombatPowerCalculator.PlayerStats playerStats) {
        List<EquipmentStats> equipmentStats = equipmentRepository.findEquippedItemStats(playerId);
        return combatPowerCalculator.calculate(playerStats, equipmentStats);
    }

    /**
     * 装备结果
     */
    public record EquipResult(Long replacedItemId) {
        public boolean hasReplacedItem() {
            return replacedItemId != null;
        }
    }

    /**
     * 卸下装备结果
     */
    public record UnequipResult(SlotType slotType) {
    }
}
