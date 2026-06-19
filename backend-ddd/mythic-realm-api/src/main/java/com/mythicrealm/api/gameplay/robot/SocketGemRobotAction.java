package com.mythicrealm.api.gameplay.robot;

import org.springframework.stereotype.Component;

@Component
public class SocketGemRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;

    public SocketGemRobotAction(RobotActionSupport support) {
        this.support = support;
    }

    @Override
    public String key() {
        return "processing_socket";
    }

    @Override
    public int priority() {
        return 74;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.riftUnlocked()
            && (context.gemCount() > 0 || context.socketCoreCount() > 0)
            && context.gemDustCount() >= 1;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("宝石或开孔材料不足");
        }
        double value = 30 + Math.min(12, context.gemCount() * 3.0) + Math.min(10, context.socketCoreCount() * 2.0);
        value += Math.min(10, context.powerGapToProgression() / 260.0);
        if (context.isCurrentKind("processing_socket")) {
            value -= 10;
        }
        return new RobotActionScore(value, "给核心装备开孔并镶嵌宝石，补足深渊冲层属性");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.processSockets(context, score);
    }
}
