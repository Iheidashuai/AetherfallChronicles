package com.mythicrealm.domain.player.repository;

import com.mythicrealm.domain.player.model.Player;
import com.mythicrealm.domain.player.model.PlayerId;

import java.util.Optional;

/**
 * 玩家仓储接口
 */
public interface PlayerRepository {

    /**
     * 保存玩家
     */
    Player save(Player player);

    /**
     * 根据ID查找玩家
     */
    Optional<Player> findById(PlayerId playerId);

    /**
     * 根据账号ID查找玩家
     */
    Optional<Player> findByAccountId(Long accountId);

    /**
     * 检查角色名是否存在
     */
    boolean existsByName(String name);
}
