package com.mythicrealm.domain.arena.model;

import java.time.Instant;
import java.util.List;

public final class ArenaModels {
    private ArenaModels() {
    }

    public record ArenaPlayer(
        long id,
        String name,
        String profession,
        int level,
        String controllerType
    ) {
        public boolean robot() {
            return "robot".equals(controllerType);
        }
    }

    public record ArenaStats(
        int level,
        int maxHp,
        int maxMp,
        int attackPower,
        int armor,
        int resistance,
        int speed,
        double accuracy,
        double evasion,
        double critChance,
        double critDamage,
        double critResist,
        String damageType
    ) {
    }

    public record ArenaSkill(
        String id,
        String name,
        String triggerKind,
        String damageType,
        double multiplier,
        int cooldown,
        int mpCost,
        String effectType,
        double effectPower,
        int durationRounds,
        int priority
    ) {
    }

    public record ArenaFighter(
        long playerId,
        String name,
        String profession,
        int level,
        int combatPower,
        ArenaStats stats,
        List<ArenaSkill> skills,
        List<String> equipmentSummary,
        String buildName,
        String strategy
    ) {
    }

    public record ArenaBattleEvent(
        int sequenceNo,
        String actor,
        String eventType,
        String tone,
        String text,
        int attackerHp,
        int defenderHp,
        int damage,
        boolean critical,
        boolean missed,
        String skillName
    ) {
    }

    public record ArenaBattleResult(
        boolean attackerWon,
        boolean roundLimitReached,
        int attackerHp,
        int defenderHp,
        List<ArenaBattleEvent> events
    ) {
    }

    public record ArenaProfileView(
        long playerId,
        String name,
        String profession,
        int level,
        String controllerType,
        int combatPower,
        int rating,
        String tier,
        int rank,
        int arenaCoins,
        int todayAttemptsUsed,
        int dailyAttempts,
        int wins,
        int losses,
        int winStreak
    ) {
    }

    public record ArenaOpponentView(
        long playerId,
        String name,
        String profession,
        int level,
        String controllerType,
        int combatPower,
        int rating,
        String tier,
        int rank,
        int wins,
        int losses,
        String challengeHint,
        boolean challengeable,
        String disabledReason
    ) {
    }

    public record ArenaMatchSummary(
        long matchId,
        long attackerId,
        String attackerName,
        long defenderId,
        String defenderName,
        boolean attackerWon,
        int attackerRatingChange,
        int defenderRatingChange,
        int arenaCoins,
        String resultText,
        Instant createdAt
    ) {
    }

    public record ArenaMatchDetail(
        ArenaMatchSummary summary,
        ArenaProfileView profile,
        ArenaFighterSnapshot attacker,
        ArenaFighterSnapshot defender,
        List<ArenaBattleEvent> events
    ) {
    }

    public record ArenaFighterSnapshot(
        long playerId,
        String name,
        String profession,
        int level,
        int combatPower,
        int maxHp,
        int attackPower,
        int armor,
        int resistance,
        String buildName,
        String strategy,
        List<String> equipmentSummary,
        List<String> skills
    ) {
    }

    public record ArenaShopOffer(
        String id,
        String name,
        String description,
        String itemTemplateId,
        int itemQuantity,
        int priceCoins,
        int requiredRating,
        int sortOrder,
        boolean affordable,
        boolean unlocked,
        String disabledReason
    ) {
    }

    public record ArenaShopPurchaseResult(
        ArenaShopOffer offer,
        ArenaProfileView profile,
        List<String> rewards
    ) {
    }

    public record ArenaOverview(
        ArenaProfileView profile,
        List<ArenaOpponentView> opponents,
        List<ArenaMatchSummary> recentMatches,
        List<ArenaShopOffer> shop,
        List<ArenaProfileView> rankings
    ) {
    }
}
