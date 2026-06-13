package com.mythicrealm.domain.equipment.interfaces.dto;

import com.mythicrealm.domain.equipment.SlotType;
import java.util.Map;

/**
 * 装备数据传输对象
 */
public class EquipmentDTO {

    /**
     * 装备请求
     */
    public record EquipRequest(long itemId) {
    }

    /**
     * 卸下装备请求
     */
    public record UnequipRequest(long itemId) {
    }

    /**
     * 装备响应
     */
    public record EquipResponse(
        boolean success,
        String message,
        Long replacedItemId,
        Map<String, Long> equippedItems
    ) {
    }

    /**
     * 卸下装备响应
     */
    public record UnequipResponse(
        boolean success,
        String message,
        String slotName,
        Map<String, Long> equippedItems
    ) {
    }

    /**
     * 已装备物品响应
     */
    public record EquippedItemsResponse(
        Map<String, Long> equippedItems,
        int combatPower
    ) {
    }

    /**
     * 将领域对象转换为DTO格式
     */
    public static Map<String, Long> toSlotMap(Map<SlotType, Long> domainMap) {
        Map<String, Long> result = new java.util.HashMap<>();
        for (Map.Entry<SlotType, Long> entry : domainMap.entrySet()) {
            result.put(entry.getKey().getCode(), entry.getValue());
        }
        return result;
    }
}
