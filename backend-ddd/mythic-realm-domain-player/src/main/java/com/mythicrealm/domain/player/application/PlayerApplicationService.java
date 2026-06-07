package com.mythicrealm.domain.player.application;

import com.mythicrealm.common.event.EventPublisher;
import com.mythicrealm.domain.player.application.command.CreatePlayerCommand;
import com.mythicrealm.domain.player.application.command.GainExpCommand;
import com.mythicrealm.domain.player.model.Player;
import com.mythicrealm.domain.player.model.PlayerId;
import com.mythicrealm.domain.player.model.Profession;
import com.mythicrealm.domain.player.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 玩家应用服务
 */
@Service
public class PlayerApplicationService {

    private final PlayerRepository playerRepository;
    private final EventPublisher eventPublisher;

    public PlayerApplicationService(PlayerRepository playerRepository, EventPublisher eventPublisher) {
        this.playerRepository = playerRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建玩家
     */
    @Transactional
    public Player createPlayer(CreatePlayerCommand command) {
        // 检查账号是否已有角色
        if (playerRepository.findByAccountId(command.accountId()).isPresent()) {
            throw new IllegalStateException("该账号已经创建角色");
        }

        // 检查角色名是否被占用
        if (playerRepository.existsByName(command.name())) {
            throw new IllegalStateException("角色名已被占用");
        }

        // 创建玩家
        Profession profession = Profession.fromCode(command.profession());
        Player player = new Player(command.accountId(), command.name(), profession);

        // 保存玩家
        Player savedPlayer = playerRepository.save(player);

        // 发布领域事件
        publishDomainEvents(savedPlayer);

        return savedPlayer;
    }

    /**
     * 获得经验和金币
     */
    @Transactional
    public Player gainExperience(GainExpCommand command) {
        Player player = playerRepository.findById(PlayerId.of(command.playerId()))
            .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        // 应用奖励
        player.applyRewards(command.expGained(), command.goldGained());

        // 保存玩家
        Player savedPlayer = playerRepository.save(player);

        // 发布领域事件
        publishDomainEvents(savedPlayer);

        return savedPlayer;
    }

    /**
     * 根据账号ID获取玩家
     */
    @Transactional(readOnly = true)
    public Player getPlayerByAccountId(Long accountId) {
        return playerRepository.findByAccountId(accountId)
            .orElseThrow(() -> new IllegalArgumentException("请先创建角色"));
    }

    /**
     * 根据玩家ID获取玩家
     */
    @Transactional(readOnly = true)
    public Player getPlayerById(Long playerId) {
        return playerRepository.findById(PlayerId.of(playerId))
            .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
    }

    /**
     * 发布领域事件
     */
    private void publishDomainEvents(Player player) {
        player.getDomainEvents().forEach(event -> {
            if (event instanceof com.mythicrealm.common.domain.DomainEvent domainEvent) {
                eventPublisher.publish(domainEvent);
            }
        });
        player.clearDomainEvents();
    }
}
