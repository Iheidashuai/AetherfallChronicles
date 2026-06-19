package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.common.exception.BusinessException;
import com.mythicrealm.domain.arena.ArenaService;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaMatchDetail;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaOpponentView;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaOverview;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaShopOffer;
import org.springframework.stereotype.Component;

@Component
public class ArenaChallengeRobotAction implements RobotDecisionAction {
    private final ArenaService arenaService;

    public ArenaChallengeRobotAction(ArenaService arenaService) {
        this.arenaService = arenaService;
    }

    @Override
    public String key() {
        return "arena";
    }

    @Override
    public int priority() {
        return 58;
    }

    @Override
    public boolean canRun(RobotDecisionContext context) {
        return context.player().level() >= 6 && context.actor().power() >= 1200;
    }

    @Override
    public RobotActionScore score(RobotDecisionContext context) {
        if (!canRun(context)) {
            return RobotActionScore.zero("等级或战力还不足以进入竞技场");
        }
        double value = 24 + Math.min(18, context.actor().power() / 6000.0);
        if (context.personalityContains("战斗") || context.personalityContains("排行") || context.personalityContains("竞技")) {
            value += 10;
        }
        if (context.isCurrentKind("arena")) {
            value -= 18;
        }
        return new RobotActionScore(value, "进入竞技场异步挑战，争取竞技币和排名");
    }

    @Override
    public RobotActionResult execute(RobotDecisionContext context, RobotActionScore score) {
        try {
            ArenaOverview overview = arenaService.overview(context.actor().id());
            ArenaShopOffer offer = overview.shop().stream()
                .filter(ArenaShopOffer::affordable)
                .filter(ArenaShopOffer::unlocked)
                .findFirst()
                .orElse(null);
            if (offer != null && context.random().nextInt(4) == 0) {
                arenaService.buyShopOffer(context.actor().id(), offer.id());
                return RobotActionResult.success("arena_shop", "竞技场商店兑换【" + offer.name() + "】，把排名收益转成成长资源。");
            }

            ArenaOpponentView opponent = overview.opponents().stream()
                .filter(ArenaOpponentView::challengeable)
                .filter(candidate -> candidate.combatPower() <= Math.max(context.actor().power() + 6000, (int) (context.actor().power() * 1.35)))
                .findFirst()
                .orElse(null);
            if (opponent == null) {
                return RobotActionResult.failure("arena", "竞技场暂无合适对手，暂缓挑战。");
            }
            ArenaMatchDetail match = arenaService.challenge(context.actor().id(), opponent.playerId());
            String text = match.summary().attackerWon()
                ? "竞技场击败【" + opponent.name() + "】，获得 " + match.summary().arenaCoins() + " 枚竞技币。"
                : "竞技场挑战【" + opponent.name() + "】失利，带回 " + match.summary().arenaCoins() + " 枚竞技币和战报。";
            return RobotActionResult.success("arena", text);
        } catch (BusinessException error) {
            return RobotActionResult.failure("arena", error.getMessage());
        }
    }
}
