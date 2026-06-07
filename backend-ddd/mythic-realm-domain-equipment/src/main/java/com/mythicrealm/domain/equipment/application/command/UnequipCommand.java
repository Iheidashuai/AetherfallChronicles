package com.mythicrealm.domain.equipment.application.command;

/**
 * 卸下装备命令
 */
public record UnequipCommand(
    long playerId,
    long itemId
) {
    public UnequipCommand {
        if (playerId <= 0) {
            throw new IllegalArgumentException("玩家ID必须大于0");
        }
        if (itemId <= 0) {
            throw new IllegalArgumentException("物品ID必须大于0");
        }
    }
}
