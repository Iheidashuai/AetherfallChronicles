package com.mythicrealm.domain.equipment.event;

import com.mythicrealm.common.domain.DomainEvent;
import com.mythicrealm.domain.equipment.SlotType;

/**
 * 装备已卸下事件
 */
public record EquipmentUnequippedEvent(
    long playerId,
    SlotType slotType,
    Long unequippedItemId
) implements DomainEvent {
}
