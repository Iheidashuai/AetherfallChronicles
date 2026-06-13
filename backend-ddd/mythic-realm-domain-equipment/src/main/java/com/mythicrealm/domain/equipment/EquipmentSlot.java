package com.mythicrealm.domain.equipment;

import com.mythicrealm.common.domain.Entity;

/**
 * 装备槽位实体
 */
public class EquipmentSlot implements Entity {
    private final SlotType slotType;
    private Long itemId;

    public EquipmentSlot(SlotType slotType) {
        this.slotType = slotType;
        this.itemId = null;
    }

    public EquipmentSlot(SlotType slotType, Long itemId) {
        this.slotType = slotType;
        this.itemId = itemId;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public Long getItemId() {
        return itemId;
    }

    public boolean isEmpty() {
        return itemId == null;
    }

    public boolean isOccupied() {
        return itemId != null;
    }

    /**
     * 装备物品到此槽位
     * @return 被替换下来的物品ID (如果有)
     */
    public Long equip(Long newItemId) {
        if (newItemId == null) {
            throw new IllegalArgumentException("装备ID不能为空");
        }
        Long oldItemId = this.itemId;
        this.itemId = newItemId;
        return oldItemId;
    }

    /**
     * 卸下此槽位的装备
     * @return 被卸下的物品ID
     */
    public Long unequip() {
        if (isEmpty()) {
            throw new IllegalStateException("槽位 " + slotType.getDisplayName() + " 上没有装备");
        }
        Long oldItemId = this.itemId;
        this.itemId = null;
        return oldItemId;
    }

    /**
     * 检查是否包含指定物品
     */
    public boolean contains(Long itemId) {
        return this.itemId != null && this.itemId.equals(itemId);
    }
}
