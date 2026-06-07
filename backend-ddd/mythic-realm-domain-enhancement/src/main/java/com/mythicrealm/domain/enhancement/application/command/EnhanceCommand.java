package com.mythicrealm.domain.enhancement.application.command;

/**
 * 强化命令
 */
public record EnhanceCommand(
    long playerId,
    long itemId
) {
    public EnhanceCommand {
        if (playerId <= 0) {
            throw new IllegalArgumentException("玩家ID必须大于0");
        }
        if (itemId <= 0) {
            throw new IllegalArgumentException("物品ID必须大于0");
        }
    }
}
