package com.mythicrealm.domain.equipment;

import com.mythicrealm.common.domain.AggregateRoot;
import com.mythicrealm.domain.equipment.event.CombatPowerChangedEvent;
import com.mythicrealm.domain.equipment.event.EquipmentEquippedEvent;
import com.mythicrealm.domain.equipment.event.EquipmentUnequippedEvent;
import java.util.*;

/**
 * 装备聚合根
 * 管理玩家的所有装备槽位
 */
public class Equipment implements AggregateRoot {
    private final long playerId;
    private final Map<SlotType, EquipmentSlot> slots;
    private final List<Object> domainEvents;

    private Equipment(long playerId, Map<SlotType, EquipmentSlot> slots) {
        this.playerId = playerId;
        this.slots = slots;
        this.domainEvents = new ArrayList<>();
    }

    /**
     * 创建新的装备聚合
     */
    public static Equipment create(long playerId) {
        Map<SlotType, EquipmentSlot> slots = new EnumMap<>(SlotType.class);
        for (SlotType slotType : SlotType.values()) {
            slots.put(slotType, new EquipmentSlot(slotType));
        }
        return new Equipment(playerId, slots);
    }

    /**
     * 从现有数据重建装备聚合
     */
    public static Equipment rebuild(long playerId, Map<SlotType, Long> equippedItems) {
        Map<SlotType, EquipmentSlot> slots = new EnumMap<>(SlotType.class);
        for (SlotType slotType : SlotType.values()) {
            Long itemId = equippedItems.get(slotType);
            slots.put(slotType, new EquipmentSlot(slotType, itemId));
        }
        return new Equipment(playerId, slots);
    }

    public long getPlayerId() {
        return playerId;
    }

    public Map<SlotType, EquipmentSlot> getSlots() {
        return Collections.unmodifiableMap(slots);
    }

    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * 装备物品
     * @param itemType 物品类型
     * @param itemId 物品ID
     * @return 被替换下来的物品ID (如果有)
     */
    public Long equip(String itemType, Long itemId) {
        SlotType targetSlot = determineTargetSlot(itemType);
        EquipmentSlot slot = slots.get(targetSlot);
        Long replacedItemId = slot.equip(itemId);

        // 发布装备事件
        domainEvents.add(new EquipmentEquippedEvent(playerId, targetSlot, itemId, replacedItemId));

        return replacedItemId;
    }

    /**
     * 卸下装备
     * @param itemId 要卸下的物品ID
     * @return 被卸下的槽位类型
     */
    public SlotType unequip(Long itemId) {
        SlotType slotType = findSlotByItemId(itemId);
        if (slotType == null) {
            throw new IllegalArgumentException("物品 " + itemId + " 未装备");
        }

        EquipmentSlot slot = slots.get(slotType);
        slot.unequip();

        // 发布卸下装备事件
        domainEvents.add(new EquipmentUnequippedEvent(playerId, slotType, itemId));

        return slotType;
    }

    /**
     * 获取所有已装备的物品ID
     */
    public Map<SlotType, Long> getEquippedItems() {
        Map<SlotType, Long> result = new EnumMap<>(SlotType.class);
        for (Map.Entry<SlotType, EquipmentSlot> entry : slots.entrySet()) {
            if (entry.getValue().isOccupied()) {
                result.put(entry.getKey(), entry.getValue().getItemId());
            }
        }
        return result;
    }

    /**
     * 检查物品是否已装备
     */
    public boolean isEquipped(Long itemId) {
        return findSlotByItemId(itemId) != null;
    }

    /**
     * 获取指定槽位的装备
     */
    public Optional<Long> getEquippedItem(SlotType slotType) {
        EquipmentSlot slot = slots.get(slotType);
        return slot.isEmpty() ? Optional.empty() : Optional.of(slot.getItemId());
    }

    /**
     * 发布战斗力变化事件
     */
    public void notifyCombatPowerChanged(CombatPower oldPower, CombatPower newPower) {
        domainEvents.add(new CombatPowerChangedEvent(playerId, oldPower, newPower));
    }

    /**
     * 确定装备的目标槽位
     * 对于戒指,选择空闲的槽位,或默认ring1
     */
    private SlotType determineTargetSlot(String itemType) {
        SlotType baseSlot = SlotType.fromItemType(itemType);

        // 如果不是戒指,直接返回对应槽位
        if (!baseSlot.isRingSlot()) {
            return baseSlot;
        }

        // 戒指逻辑: 优先选择空闲槽位
        EquipmentSlot ring1 = slots.get(SlotType.RING1);
        EquipmentSlot ring2 = slots.get(SlotType.RING2);

        if (ring1.isEmpty()) {
            return SlotType.RING1;
        }
        if (ring2.isEmpty()) {
            return SlotType.RING2;
        }

        // 都被占用,默认替换ring1
        return SlotType.RING1;
    }

    /**
     * 查找物品所在的槽位
     */
    private SlotType findSlotByItemId(Long itemId) {
        for (Map.Entry<SlotType, EquipmentSlot> entry : slots.entrySet()) {
            if (entry.getValue().contains(itemId)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
