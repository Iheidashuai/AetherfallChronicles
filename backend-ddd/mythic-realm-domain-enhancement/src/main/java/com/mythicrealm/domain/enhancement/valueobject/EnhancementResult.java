package com.mythicrealm.domain.enhancement.valueobject;

import com.mythicrealm.common.domain.ValueObject;

/**
 * 强化结果值对象
 */
public record EnhancementResult(
    boolean success,
    EnhancementLevel previousLevel,
    EnhancementLevel currentLevel,
    EnhancementLuck previousLuck,
    EnhancementLuck currentLuck,
    int cost,
    double successRate
) implements ValueObject {

    public static EnhancementResult success(
        EnhancementLevel previousLevel,
        EnhancementLevel currentLevel,
        EnhancementLuck previousLuck,
        int cost,
        double successRate
    ) {
        return new EnhancementResult(
            true,
            previousLevel,
            currentLevel,
            previousLuck,
            EnhancementLuck.initial(),
            cost,
            successRate
        );
    }

    public static EnhancementResult failure(
        EnhancementLevel previousLevel,
        EnhancementLevel currentLevel,
        EnhancementLuck previousLuck,
        EnhancementLuck currentLuck,
        int cost,
        double successRate
    ) {
        return new EnhancementResult(
            false,
            previousLevel,
            currentLevel,
            previousLuck,
            currentLuck,
            cost,
            successRate
        );
    }

    public boolean isLevelChanged() {
        return !previousLevel.equals(currentLevel);
    }

    public boolean isLevelDecreased() {
        return previousLevel.value() > currentLevel.value();
    }

    public int getLevelDifference() {
        return currentLevel.value() - previousLevel.value();
    }
}
