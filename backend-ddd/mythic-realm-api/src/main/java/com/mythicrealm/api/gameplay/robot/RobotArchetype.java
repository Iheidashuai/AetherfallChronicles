package com.mythicrealm.api.gameplay.robot;

/**
 * Structured personality for robots.
 *
 * <p>Each archetype carries a small set of behaviour weights that multiply the
 * personality bonus inside {@code *RobotAction.score()}, plus a softmax
 * {@code temperature} that controls how "satisficing" (human, varied) vs.
 * "optimising" (expert, predictable) that robot is when picking an action.
 *
 * <p>This replaces the old "keyword substring match -> fixed +bonus" path so the
 * 200 robots actually diverge instead of all chasing the single highest-utility
 * action. The free-text {@code personality} column is kept for chat flavour; this
 * enum is resolved from the {@code personality_archetype} column with a keyword
 * fallback so older seeds still behave reasonably.
 */
public enum RobotArchetype {
    // pveBias, marketBias, socialBias, growthBias, temperature
    HARDCORE(1.6, 0.6, 0.6, 1.1, 7.0),
    SHOWOFF(1.3, 0.9, 1.2, 1.4, 8.0),
    MERCHANT(0.6, 1.8, 0.9, 1.0, 9.0),
    SOCIAL(0.8, 0.9, 1.9, 0.8, 11.0),
    CASUAL(1.0, 1.0, 1.1, 0.9, 11.0),
    NEWBIE(1.0, 0.8, 1.0, 0.7, 12.0);

    private final double pveBias;
    private final double marketBias;
    private final double socialBias;
    private final double growthBias;
    private final double temperature;

    RobotArchetype(double pveBias, double marketBias, double socialBias, double growthBias, double temperature) {
        this.pveBias = pveBias;
        this.marketBias = marketBias;
        this.socialBias = socialBias;
        this.growthBias = growthBias;
        this.temperature = temperature;
    }

    public double pveBias() {
        return pveBias;
    }

    public double marketBias() {
        return marketBias;
    }

    public double socialBias() {
        return socialBias;
    }

    public double growthBias() {
        return growthBias;
    }

    public double temperature() {
        return temperature;
    }

    /**
     * Resolve an archetype from the explicit seed code, falling back to keyword
     * inference over the free-text personality, then to {@link #CASUAL}.
     */
    public static RobotArchetype resolve(String code, String personality) {
        if (code != null && !code.isBlank()) {
            for (RobotArchetype archetype : values()) {
                if (archetype.name().equalsIgnoreCase(code.trim())) {
                    return archetype;
                }
            }
        }
        return infer(personality);
    }

    private static RobotArchetype infer(String personality) {
        if (personality == null || personality.isBlank()) {
            return CASUAL;
        }
        if (contains(personality, "商会", "低价", "倒卖", "捡漏", "财迷")) {
            return MERCHANT;
        }
        if (contains(personality, "频道", "聊天", "冒泡", "社交")) {
            return SOCIAL;
        }
        if (contains(personality, "硬核", "冲榜", "推进", "挑战", "刷本")) {
            return HARDCORE;
        }
        if (contains(personality, "比较", "词条", "炫", "评分")) {
            return SHOWOFF;
        }
        if (contains(personality, "新人", "练级", "新晋", "学徒")) {
            return NEWBIE;
        }
        return CASUAL;
    }

    private static boolean contains(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
