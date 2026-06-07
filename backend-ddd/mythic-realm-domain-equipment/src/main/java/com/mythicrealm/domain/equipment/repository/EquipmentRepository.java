package com.mythicrealm.domain.equipment.repository;

import com.mythicrealm.domain.equipment.Equipment;
import com.mythicrealm.domain.equipment.EquipmentStats;
import java.util.List;
import java.util.Optional;

/**
 * 装备仓储接口
 */
public interface EquipmentRepository {

    /**
     * 根据玩家ID查找装备聚合
     */
    Optional<Equipment> findByPlayerId(long playerId);

    /**
     * 保存装备聚合
     */
    void save(Equipment equipment);

    /**
     * 获取玩家所有已装备物品的属性
     */
    List<EquipmentStats> findEquippedItemStats(long playerId);

    /**
     * 获取指定物品的属性
     */
    Optional<EquipmentStats> findItemStats(long itemId);

    /**
     * 检查物品是否属于指定玩家
     */
    boolean isItemOwnedByPlayer(long itemId, long playerId);

    /**
     * 获取物品类型
     */
    Optional<String> findItemType(long itemId);
}
