package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import java.util.Random;

public record RobotDecisionContext(
    RobotAgent actor,
    RobotAgent target,
    DungeonConfig runnableDungeon,
    DungeonConfig progressionDungeon,
    RobotEquipmentService.EnhancementOpportunity enhancementOpportunity,
    int inventoryCount,
    int activeListings,
    int marketOpportunities,
    Random random
) {
    public PlayerRecord player() {
        return actor.player();
    }

    public int powerGapToProgression() {
        if (progressionDungeon == null) {
            return 0;
        }
        return Math.max(0, progressionDungeon.recommendedPower() - actor.power());
    }

    public long goldAfterPossibleRecharge() {
        return player().gold() + safeGoldValue(player().realMoney());
    }

    public boolean isCurrentKind(String kind) {
        return kind != null && kind.equals(actor.currentActivityKind());
    }

    public boolean personalityContains(String token) {
        return actor.personality() != null && actor.personality().contains(token);
    }

    private long safeGoldValue(long realMoney) {
        try {
            return Math.addExact(0, Math.multiplyExact(realMoney, RechargeService.GOLD_PER_RMB));
        } catch (ArithmeticException error) {
            return Long.MAX_VALUE / 2;
        }
    }
}
