package com.mythicrealm.domain.equipment.event;

import com.mythicrealm.common.domain.DomainEvent;
import com.mythicrealm.domain.equipment.SlotType;

/**
 * 装备已装备事件
 */
public record EquipmentEquippedEvent(
    long playerId,
    SlotType slotType,
    Long equippedItemId,
    Long replacedItemId
) implements DomainEvent {
}
