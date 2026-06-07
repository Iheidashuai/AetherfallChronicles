package com.mythicrealm.domain.enhancement.interfaces.dto;

/**
 * 强化结果 DTO
 */
public record EnhanceResultDTO(
    boolean success,
    int previousLevel,
    int currentLevel,
    int previousLuck,
    int currentLuck,
    int cost,
    double successRate,
    String message
) {
    public static EnhanceResultDTO from(
        boolean success,
        int previousLevel,
        int currentLevel,
        int previousLuck,
        int currentLuck,
        int cost,
        double successRate
    ) {
        String message = buildMessage(success, previousLevel, currentLevel);
        return new EnhanceResultDTO(
            success,
            previousLevel,
            currentLevel,
            previousLuck,
            currentLuck,
            cost,
            successRate,
            message
        );
    }

    private static String buildMessage(boolean success, int previousLevel, int currentLevel) {
        if (success) {
            return String.format("强化成功！装备从 +%d 升级到 +%d", previousLevel, currentLevel);
        } else {
            if (currentLevel < previousLevel) {
                return String.format("强化失败！装备从 +%d 降级到 +%d，幸运值增加", previousLevel, currentLevel);
            } else {
                return String.format("强化失败！装备保持在 +%d，幸运值增加", currentLevel);
            }
        }
    }
}
