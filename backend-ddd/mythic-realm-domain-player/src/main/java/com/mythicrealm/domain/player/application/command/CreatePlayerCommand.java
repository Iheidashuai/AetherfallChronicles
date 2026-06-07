package com.mythicrealm.domain.player.application.command;

/**
 * 创建玩家命令
 */
public record CreatePlayerCommand(
    Long accountId,
    String name,
    String profession
) {
}
