package com.mythicrealm.domain.player.application.command;

/**
 * 获得经验命令
 */
public record GainExpCommand(
    Long playerId,
    int expGained,
    int goldGained
) {
}
