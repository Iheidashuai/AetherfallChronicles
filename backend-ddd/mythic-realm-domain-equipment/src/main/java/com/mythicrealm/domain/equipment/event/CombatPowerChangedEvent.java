package com.mythicrealm.domain.equipment.event;

import com.mythicrealm.common.domain.DomainEvent;
import com.mythicrealm.domain.equipment.CombatPower;

/**
 * 战斗力变化事件
 */
public record CombatPowerChangedEvent(
    long playerId,
    CombatPower oldPower,
    CombatPower newPower
) implements DomainEvent {

    public int getPowerDifference() {
        return newPower.value() - oldPower.value();
    }
}
