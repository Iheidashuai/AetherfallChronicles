package com.mythicrealm.domain.player.model;

import com.mythicrealm.common.domain.AggregateRoot;
import com.mythicrealm.domain.player.event.GoldChangedEvent;
import com.mythicrealm.domain.player.event.PlayerCreatedEvent;
import com.mythicrealm.domain.player.event.PlayerLeveledUpEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家聚合根
 */
public class Player implements AggregateRoot {

    private PlayerId id;
    private final Long accountId;
    private final String name;
    private final Profession profession;
    private Level level;
    private Experience experience;
    private Gold gold;
    private PlayerStats stats;

    // 领域事件集合
    private final List<Object> domainEvents = new ArrayList<>();

    // 创建新玩家
    public Player(Long accountId, String name, Profession profession) {
        validateName(name);
        this.accountId = accountId;
        this.name = name.trim();
        this.profession = profession;
        this.level = Level.initial();
        this.experience = Experience.zero();
        this.gold = Gold.zero();
        this.stats = PlayerStats.fromProfession(profession);
    }

    // 从仓储加载
    public Player(PlayerId id, Long accountId, String name, Profession profession,
                  Level level, Experience experience, Gold gold, PlayerStats stats) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.profession = profession;
        this.level = level;
        this.experience = experience;
        this.gold = gold;
        this.stats = stats;
    }

    /**
     * 玩家创建后设置ID
     */
    public void setId(PlayerId id) {
        if (this.id != null) {
            throw new IllegalStateException("玩家ID已设置");
        }
        this.id = id;
        // 发布玩家创建事件
        addDomainEvent(new PlayerCreatedEvent(id.value(), accountId, name, profession.getCode()));
    }

    /**
     * 获得经验和金币奖励
     */
    public void applyRewards(int expGained, int goldGained) {
        if (expGained < 0 || goldGained < 0) {
            throw new IllegalArgumentException("奖励不能为负数");
        }

        // 添加经验
        experience = experience.add(expGained);

        // 处理升级
        int levelsBefore = level.value();
        while (level.canLevelUp() && experience.canLevelUp(level)) {
            int requiredExp = level.experienceRequired();
            experience = experience.subtract(requiredExp);
            level = level.levelUp();
            stats = stats.onLevelUp(profession);

            // 发布升级事件
            addDomainEvent(new PlayerLeveledUpEvent(
                id.value(),
                level.value(),
                levelsBefore,
                stats.strength(),
                stats.agility(),
                stats.constitution(),
                stats.intelligence(),
                stats.spirit()
            ));
        }

        // 满级后清空经验
        if (level.isMaxLevel()) {
            experience = Experience.zero();
        }

        // 添加金币
        if (goldGained > 0) {
            int goldBefore = gold.value();
            gold = gold.add(goldGained);
            addDomainEvent(new GoldChangedEvent(id.value(), goldBefore, gold.value(), goldGained));
        }
    }

    /**
     * 消费金币
     */
    public void spendGold(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("消费金额必须大于0");
        }
        int goldBefore = gold.value();
        gold = gold.subtract(amount);
        addDomainEvent(new GoldChangedEvent(id.value(), goldBefore, gold.value(), -amount));
    }

    /**
     * 添加金币
     */
    public void addGold(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("添加金额必须大于0");
        }
        int goldBefore = gold.value();
        gold = gold.add(amount);
        addDomainEvent(new GoldChangedEvent(id.value(), goldBefore, gold.value(), amount));
    }

    /**
     * 计算战斗属性
     */
    public double maxHp() {
        return stats.calculateMaxHp(level);
    }

    public double maxMp() {
        return stats.calculateMaxMp(level);
    }

    public double attack() {
        return stats.calculateAttack(level);
    }

    public double defense() {
        return stats.calculateDefense(level);
    }

    // Getters
    public PlayerId getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public Profession getProfession() {
        return profession;
    }

    public Level getLevel() {
        return level;
    }

    public Experience getExperience() {
        return experience;
    }

    public Gold getGold() {
        return gold;
    }

    public PlayerStats getStats() {
        return stats;
    }

    // 领域事件管理
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    private void addDomainEvent(Object event) {
        domainEvents.add(event);
    }

    // 私有验证方法
    private void validateName(String name) {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 16) {
            throw new IllegalArgumentException("角色名需要 2-16 个字符");
        }
    }
}
