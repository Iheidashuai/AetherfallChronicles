package com.mythicrealm.api.gameplay.inventory;

import java.math.BigDecimal;
import java.util.List;

public record ItemRecord(
    long id,
    long playerId,
    String templateId,
    String name,
    String itemType,
    String itemCategory,
    String quality,
    int requiredLevel,
    int attackBonus,
    int defenseBonus,
    int resistanceBonus,
    int hpBonus,
    int mpBonus,
    BigDecimal critBonus,
    int sellPrice,
    int quantity,
    boolean stackable,
    String effectType,
    String effectValueJson,
    double enhanceBonusRate,
    int minEnhanceLevel,
    int maxEnhanceLevel,
    int enhancementLevel,
    int enhancementLuck,
    int refineLevel,
    String refineFocus,
    int ascensionLevel,
    int ascensionLuck,
    int socketAttackBonus,
    int socketDefenseBonus,
    int socketResistanceBonus,
    int socketHpBonus,
    int socketMpBonus,
    double socketCritBonus,
    int affixAttackBonus,
    int affixDefenseBonus,
    int affixResistanceBonus,
    int affixHpBonus,
    int affixMpBonus,
    double affixCritBonus,
    List<EquipmentSocketView> sockets,
    List<EquipmentAffixView> affixes
) {
    public ItemRecord(
        long id,
        long playerId,
        String templateId,
        String name,
        String itemType,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int resistanceBonus,
        int hpBonus,
        int mpBonus,
        BigDecimal critBonus,
        int sellPrice,
        int enhancementLevel,
        int enhancementLuck
    ) {
        this(
            id,
            playerId,
            templateId,
            name,
            itemType,
            "equipment",
            quality,
            requiredLevel,
            attackBonus,
            defenseBonus,
            resistanceBonus,
            hpBonus,
            mpBonus,
            critBonus,
            sellPrice,
            1,
            false,
            null,
            null,
            0,
            1,
            15,
            enhancementLevel,
            enhancementLuck,
            0,
            "balanced",
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
            0,
            List.of(),
            List.of()
        );
    }

    public String displayName() {
        String enhanced = enhancementLevel > 0 ? name + " +" + enhancementLevel : name;
        String refined = refineLevel > 0 ? enhanced + " · 淬" + refineLevel : enhanced;
        return ascensionLevel > 0 ? refined + " · 阶" + ascensionLevel : refined;
    }

    public boolean equipment() {
        return "equipment".equals(itemCategory);
    }

    public boolean consumable() {
        return "consumable".equals(itemCategory);
    }

    public boolean material() {
        return "material".equals(itemCategory);
    }

    public boolean chest() {
        return "chest".equals(itemCategory) || "chest".equals(effectType);
    }

    public int enhancedAttackBonus() {
        return postProcessedValue(attackBonus, "attack", socketAttackBonus + affixAttackBonus);
    }

    public int enhancedDefenseBonus() {
        return postProcessedValue(defenseBonus, "defense", socketDefenseBonus + affixDefenseBonus);
    }

    public int enhancedResistanceBonus() {
        return postProcessedValue(resistanceBonus, "resistance", socketResistanceBonus + affixResistanceBonus);
    }

    public int enhancedHpBonus() {
        return postProcessedValue(hpBonus, "hp", socketHpBonus + affixHpBonus);
    }

    public int enhancedMpBonus() {
        return postProcessedValue(mpBonus, "mp", socketMpBonus + affixMpBonus);
    }

    public double enhancedCritBonus() {
        double milestone = 0.0;
        if (enhancementLevel >= 10) {
            milestone += 0.01;
        }
        if (enhancementLevel >= 15) {
            milestone += 0.02;
        }
        return (critBonus.doubleValue() * multiplierFor("crit") + milestone + socketCritBonus + affixCritBonus) * ascensionMultiplier();
    }

    private int postProcessedValue(int value, String attribute, int postBonus) {
        if (value <= 0 && postBonus <= 0) {
            return 0;
        }
        int result = value <= 0 ? 0 : (int) Math.round(value * multiplierFor(attribute));
        if (enhancementLevel >= 5) {
            result += Math.max(1, value / 10);
        }
        if (enhancementLevel >= 10) {
            result += Math.max(1, value / 8);
        }
        if (enhancementLevel >= 15) {
            result += Math.max(1, value / 5);
        }
        return (int) Math.round((result + Math.max(0, postBonus)) * ascensionMultiplier());
    }

    private double multiplierFor(String attribute) {
        double refineMultiplier = "balanced".equals(refineFocus)
            ? refineLevel * 0.018
            : attribute.equals(refineFocus) ? refineLevel * 0.04 : refineLevel * 0.01;
        return 1 + enhancementLevel * 0.03 + refineMultiplier;
    }

    private double ascensionMultiplier() {
        return 1 + Math.max(0, ascensionLevel) * 0.02;
    }

    public record EquipmentSocketView(
        int socketIndex,
        boolean unlocked,
        Long gemItemId,
        String gemTemplateId,
        String gemName,
        String gemQuality,
        String statKey,
        double statValue,
        int rank
    ) {
    }

    public record EquipmentAffixView(
        int affixIndex,
        String statKey,
        double statValue,
        int tier,
        boolean locked
    ) {
    }
}
