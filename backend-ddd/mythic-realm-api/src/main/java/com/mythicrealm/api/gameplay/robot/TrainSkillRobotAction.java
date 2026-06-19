package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.skill.SkillService;
import org.springframework.stereotype.Component;

@Component
public class TrainSkillRobotAction implements RobotDecisionAction {
    private final RobotActionSupport support;
    private final SkillService skillService;

    public TrainSkillRobotAction(RobotActionSupport support, SkillService skillService) {
        this.support = support;
        this.skillService = skillService;
    }

    @Override
    public String key() {
        return "skill";
    }

    @Override
    public int priority() {
        return 72;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        SkillService.TrainingOption option = skillService.bestTrainingOption(context.player());
        return option != null && context.goldAfterPossibleRecharge() >= option.cost();
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        SkillService.TrainingOption option = skillService.bestTrainingOption(context.player());
        if (option == null) {
            return RobotActionScore.zero("没有可学习或升级的技能");
        }
        if (context.goldAfterPossibleRecharge() < option.cost()) {
            return RobotActionScore.zero("余额不足以训练下一阶技能");
        }
        double value = 32 + option.score() * 0.55;
        value += Math.min(18, context.powerGapToProgression() / 220.0);
        value += option.affordable() ? 7 : 3;
        if (option.learn()) {
            value += 8;
        }
        if (context.personalityContains("副本") || context.personalityContains("挑战")) {
            value += 5;
        }
        if (context.isCurrentKind("skill")) {
            value -= 14;
        }
        return new RobotActionScore(value, option.reason() + "，成本 " + option.cost() + " 金");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        return support.trainSkill(context, score);
    }
}
