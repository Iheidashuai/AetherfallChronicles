package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class ConfigureBuildRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public ConfigureBuildRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "build";
    }

    @Override
    public int priority() {
        return 78;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        String recommended = context.recommendedBuildPresetId();
        if (recommended == null || recommended.isBlank()) {
            return false;
        }
        return !recommended.equals(context.activeBuildPresetId());
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("当前构筑已经匹配推荐流派");
        }
        double value = context.buildCount() <= 0 ? 54 : 42;
        value += Math.min(16, context.riftNextTier() * 1.2);
        value += Math.min(14, context.powerGapToProgression() / 260.0);
        if (context.riftUnlocked()) {
            value += 8;
        }
        if (context.isCurrentKind("build")) {
            value -= 12;
        }
        String previous = context.activeBuildName() == null ? "尚未启用构筑" : "当前为【" + context.activeBuildName() + "】";
        return new RobotActionScore(value, previous + "，切换到职业推荐流派以提升深渊和副本表现");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.configureBuild(context, score);
    }
}
