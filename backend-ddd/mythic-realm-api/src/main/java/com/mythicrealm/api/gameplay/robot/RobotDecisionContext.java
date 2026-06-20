package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.util.Map;
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
    StaminaSnapshot stamina,
    int claimableQuestCount,
    String firstClaimableQuestId,
    int staminaPotionCount,
    int attributePotionCount,
    int chestCount,
    int legendaryFragmentCount,
    int immortalFragmentCount,
    int usableEnhancementStoneCount,
    boolean riftUnlocked,
    int riftBestTier,
    int riftNextTier,
    int riftMinimumPower,
    int riftEssence,
    int riftShards,
    int riftOrbs,
    int socketCoreCount,
    int gemDustCount,
    int affixLockCount,
    int ascensionCoreCount,
    int ascensionGuardCount,
    int gemCount,
    int buildCount,
    String activeBuildName,
    String activeBuildPresetId,
    String recommendedBuildPresetId,
    Map<String, Integer> recentKindCounts,
    boolean inGuild,
    Random random
) {
    public RobotDecisionContext(
        RobotAgent actor,
        RobotAgent target,
        DungeonConfig runnableDungeon,
        DungeonConfig progressionDungeon,
        RobotEquipmentService.EnhancementOpportunity enhancementOpportunity,
        int inventoryCount,
        int activeListings,
        int marketOpportunities,
        StaminaSnapshot stamina,
        int claimableQuestCount,
        String firstClaimableQuestId,
        int staminaPotionCount,
        int attributePotionCount,
        int chestCount,
        int legendaryFragmentCount,
        int immortalFragmentCount,
        int usableEnhancementStoneCount,
        Random random
    ) {
        this(
            actor,
            target,
            runnableDungeon,
            progressionDungeon,
            enhancementOpportunity,
            inventoryCount,
            activeListings,
            marketOpportunities,
            stamina,
            claimableQuestCount,
            firstClaimableQuestId,
            staminaPotionCount,
            attributePotionCount,
            chestCount,
            legendaryFragmentCount,
            immortalFragmentCount,
            usableEnhancementStoneCount,
            false,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            Map.of(),
            false,
            random
        );
    }

    public PlayerRecord player() {
        return actor.player();
    }

    public int powerGapToProgression() {
        if (progressionDungeon == null) {
            return 0;
        }
        return Math.max(0, progressionDungeon.minimumPower() - actor.power());
    }

    public long goldAfterPossibleRecharge() {
        return player().gold() + safeGoldValue(player().realMoney());
    }

    public long tacticalGoldReserveTarget() {
        long levelReserve = Math.max(12_000L, (long) Math.max(1, player().level()) * 2_400L);
        long wealthReserve = (long) Math.max(0, actor().wealthTierLevel()) * 6_000L;
        long target = levelReserve + wealthReserve;
        if (enhancementOpportunity != null) {
            target = Math.max(target, enhancementOpportunity.cost() * 2L);
        }
        if (marketOpportunities > 0) {
            target += Math.min(30_000L, (long) marketOpportunities * 4_000L);
        }
        return Math.min(goldAfterPossibleRecharge(), target);
    }

    public long tacticalGoldReserveDeficit() {
        return Math.max(0, tacticalGoldReserveTarget() - player().gold());
    }

    public boolean isCurrentKind(String kind) {
        return kind != null && kind.equals(actor.currentActivityKind());
    }

    public RobotArchetype archetype() {
        return actor.archetype() == null ? RobotArchetype.CASUAL : actor.archetype();
    }

    /** How many of the robot's last few actions were {@code kind} (0 if unknown). */
    public int recentKindCount(String kind) {
        if (kind == null || recentKindCounts == null) {
            return 0;
        }
        return recentKindCounts.getOrDefault(kind, 0);
    }

    /**
     * Decaying anti-repeat penalty: the more of the recent history was already this
     * kind, the stronger the nudge to do something else. Replaces the old one-step
     * {@code isCurrentKind} check so robots stop ping-ponging between two actions.
     */
    public double repeatPenalty(String kind, double perOccurrence) {
        return recentKindCount(kind) * perOccurrence;
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
