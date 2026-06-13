package com.mythicrealm.domain.enhancement.model;

import com.mythicrealm.common.domain.AggregateRoot;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementLevel;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementLuck;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementResult;

/**
 * 强化聚合根
 *
 * 封装装备强化的核心业务逻辑
 */
public class Enhancement implements AggregateRoot {
    private final long itemId;
    private final long playerId;
    private EnhancementLevel level;
    private EnhancementLuck luck;

    public Enhancement(long itemId, long playerId, EnhancementLevel level, EnhancementLuck luck) {
        this.itemId = itemId;
        this.playerId = playerId;
        this.level = level;
        this.luck = luck;
    }

    public static Enhancement create(long itemId, long playerId) {
        return new Enhancement(
            itemId,
            playerId,
            EnhancementLevel.initial(),
            EnhancementLuck.initial()
        );
    }

    public static Enhancement restore(long itemId, long playerId, int levelValue, int luckValue) {
        return new Enhancement(
            itemId,
            playerId,
            EnhancementLevel.of(levelValue),
            EnhancementLuck.of(luckValue)
        );
    }

    /**
     * 执行强化尝试
     *
     * @param success 是否成功（由策略决定）
     * @param cost 强化消耗
     * @param successRate 成功率
     * @return 强化结果
     */
    public EnhancementResult attemptEnhancement(boolean success, int cost, double successRate) {
        if (level.isMaxLevel()) {
            throw new IllegalStateException("装备已强化到上限");
        }

        EnhancementLevel previousLevel = level;
        EnhancementLuck previousLuck = luck;

        if (success) {
            level = level.increment();
            luck = luck.reset();

            return EnhancementResult.success(
                previousLevel,
                level,
                previousLuck,
                cost,
                successRate
            );
        } else {
            luck = luck.increment();
            level = calculateFailureLevelPenalty(level);

            return EnhancementResult.failure(
                previousLevel,
                level,
                previousLuck,
                luck,
                cost,
                successRate
            );
        }
    }

    /**
     * 计算强化失败后的等级惩罚
     */
    private EnhancementLevel calculateFailureLevelPenalty(EnhancementLevel currentLevel) {
        int targetLevel = currentLevel.value() + 1;

        // 1-6级：不掉级
        if (targetLevel <= 6) {
            return currentLevel;
        }

        // 7-12级：掉1级
        if (targetLevel <= 12) {
            return currentLevel.decrement();
        }

        // 13-15级：掉2级
        return currentLevel.decrementBy(2);
    }

    // Getters
    public long getItemId() {
        return itemId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public EnhancementLevel getLevel() {
        return level;
    }

    public EnhancementLuck getLuck() {
        return luck;
    }

    public int getLevelValue() {
        return level.value();
    }

    public int getLuckValue() {
        return luck.value();
    }
}
