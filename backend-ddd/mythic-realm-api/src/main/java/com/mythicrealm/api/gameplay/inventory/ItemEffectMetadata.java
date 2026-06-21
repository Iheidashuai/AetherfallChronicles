package com.mythicrealm.api.gameplay.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ItemEffectMetadata(
    boolean usable,
    String actionLabel,
    String typeLabel,
    String effectSummary,
    String usageHint,
    boolean marketable,
    String robotPolicy,
    int shopPurchaseLimit,
    List<String> tags
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> ATTRIBUTES = Set.of("strength", "agility", "constitution", "intelligence", "spirit");
    private static final Set<String> PROGRESSIONS = Set.of("enhancement", "refine", "ascension");
    private static final Set<String> EQUIPMENT_SCOPES = Set.of(
        "playableEquipment",
        "allPlayableEquipment",
        "backpack",
        "equipped",
        "allOwned"
    );
    private static final Set<String> OPS = Set.of(
        "grantGold",
        "grantRealMoney",
        "restoreStamina",
        "gainExperience",
        "setLevel",
        "addAttributes",
        "grantItems",
        "consumeItems",
        "randomOne",
        "randomMany",
        "lootTableRef",
        "grantEquipmentPackage",
        "setEquipmentProgress",
        "emitQuestEvent"
    );

    public static ItemEffectMetadata fromItemRecord(ItemRecord item) {
        return from(
            item.effectType(),
            item.effectValueJson(),
            item.itemCategory(),
            item.itemType(),
            item.description(),
            true
        );
    }

    public static ItemEffectMetadata fromTemplate(ItemTemplate item) {
        return from(
            item.effectType(),
            item.effectValueJson(),
            item.category(),
            item.type(),
            item.description(),
            item.tradeable()
        );
    }

    public static ItemEffectMetadata from(
        String effectType,
        String effectValueJson,
        String itemCategory,
        String itemType,
        String description,
        boolean tradeableDefault
    ) {
        JsonNode root = readJson(effectValueJson);
        boolean configured = isConfigured(effectType, root);
        if (configured) {
            JsonNode ui = root.path("ui");
            List<String> tags = stringList(root.path("tags"));
            String actionLabel = text(ui, "actionLabel", "chest".equals(itemCategory) ? "开启" : "使用");
            String typeLabel = text(ui, "typeLabel", legacyTypeLabel(effectType, itemCategory, itemType));
            String summary = text(ui, "summary", configuredSummary(root, description));
            String hint = text(ui, "usageHint", legacyUsageHint(effectType, itemCategory, itemType, root));
            boolean marketable = root.path("market").has("tradeable")
                ? root.path("market").path("tradeable").asBoolean(tradeableDefault)
                : tradeableDefault && !tags.contains("shopOnly");
            return new ItemEffectMetadata(
                hasEffects(root) || "chest".equals(itemCategory),
                actionLabel,
                typeLabel,
                summary,
                hint,
                marketable,
                text(root.path("robot"), "policy", "default"),
                shopPurchaseLimit(root),
                tags
            );
        }

        boolean usable = legacyUsable(effectType, itemCategory);
        return new ItemEffectMetadata(
            usable,
            "chest".equals(effectType) || "chest".equals(itemCategory) || "equipmentSetChest".equals(effectType) ? "开启" : "使用",
            legacyTypeLabel(effectType, itemCategory, itemType),
            legacySummary(effectType, itemCategory, itemType, description, root),
            legacyUsageHint(effectType, itemCategory, itemType, root),
            legacyMarketable(effectType, itemCategory, itemType, root, tradeableDefault),
            legacyRobotPolicy(effectType, root),
            shopPurchaseLimit(root),
            legacyTags(effectType, itemCategory, root)
        );
    }

    public static int shopPurchaseLimit(String effectType, String effectValueJson) {
        JsonNode root = readJson(effectValueJson);
        return shopPurchaseLimit(root);
    }

    public static void validateTemplate(ItemTemplate item, Set<String> itemIds) {
        JsonNode root = readJson(item.effectValueJson());
        if (!isConfigured(item.effectType(), root)) {
            return;
        }
        if (root.path("version").asInt(0) != 1) {
            throw new IllegalStateException("Item " + item.id() + " has unsupported effect version");
        }
        JsonNode effects = root.path("use").path("effects");
        if (!effects.isArray() && !"chest".equals(item.category())) {
            throw new IllegalStateException("Item " + item.id() + " configured effect needs use.effects");
        }
        validateEffects(item.id(), effects, itemIds);
        JsonNode conditions = root.path("use").path("conditions");
        if (conditions.isArray()) {
            for (JsonNode condition : conditions) {
                String op = condition.path("op").asText("");
                if (!Set.of("playerLevelAtLeast", "playerLevelAtMost", "hasItems").contains(op)) {
                    throw new IllegalStateException("Item " + item.id() + " has unknown condition " + op);
                }
                if ("hasItems".equals(op)) {
                    validateItemList(item.id(), condition.path("items"), itemIds, true);
                }
            }
        }
    }

    private static void validateEffects(String itemId, JsonNode effects, Set<String> itemIds) {
        if (!effects.isArray()) {
            return;
        }
        for (JsonNode effect : effects) {
            String op = effect.path("op").asText("");
            if (!OPS.contains(op)) {
                throw new IllegalStateException("Item " + itemId + " has unknown effect op " + op);
            }
            switch (op) {
                case "addAttributes" -> validateAttributes(itemId, effect.path("attributes"));
                case "grantItems", "consumeItems" -> validateItemList(itemId, effect.path("items"), itemIds, "consumeItems".equals(op));
                case "randomOne" -> validateRandomOptions(itemId, effect.path("options"), itemIds);
                case "randomMany" -> validateRandomOptions(itemId, effect.path("options"), itemIds);
                case "grantEquipmentPackage" -> validateEquipmentPackage(itemId, effect, itemIds);
                case "setEquipmentProgress" -> {
                    String progression = effect.path("progression").asText("");
                    String scope = effect.path("scope").asText("playableEquipment");
                    if (!PROGRESSIONS.contains(progression)) {
                        throw new IllegalStateException("Item " + itemId + " has illegal equipment progression " + progression);
                    }
                    if (!EQUIPMENT_SCOPES.contains(scope)) {
                        throw new IllegalStateException("Item " + itemId + " has illegal equipment scope " + scope);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void validateRandomOptions(String itemId, JsonNode options, Set<String> itemIds) {
        if (!options.isArray() || options.isEmpty()) {
            throw new IllegalStateException("Item " + itemId + " random effect needs options");
        }
        for (JsonNode option : options) {
            validateEffects(itemId, option.path("effects"), itemIds);
            if (option.hasNonNull("templateId") && !itemIds.contains(option.path("templateId").asText())) {
                throw new IllegalStateException("Item " + itemId + " references missing item " + option.path("templateId").asText());
            }
        }
    }

    private static void validateEquipmentPackage(String itemId, JsonNode effect, Set<String> itemIds) {
        JsonNode templates = effect.path("equipmentTemplates");
        if (!templates.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = templates.fields();
        while (fields.hasNext()) {
            String templateId = fields.next().getValue().asText("");
            if (!templateId.isBlank() && !itemIds.contains(templateId)) {
                throw new IllegalStateException("Item " + itemId + " references missing equipment template " + templateId);
            }
        }
    }

    private static void validateAttributes(String itemId, JsonNode attributes) {
        if (!attributes.isObject()) {
            throw new IllegalStateException("Item " + itemId + " addAttributes needs attributes");
        }
        Iterator<String> names = attributes.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ATTRIBUTES.contains(name)) {
                throw new IllegalStateException("Item " + itemId + " has illegal attribute " + name);
            }
        }
    }

    private static void validateItemList(String itemId, JsonNode items, Set<String> itemIds, boolean required) {
        if (!items.isArray() || items.isEmpty()) {
            if (required) {
                throw new IllegalStateException("Item " + itemId + " needs item references");
            }
            return;
        }
        for (JsonNode entry : items) {
            String templateId = entry.path("templateId").asText("");
            if (templateId.isBlank() || !itemIds.contains(templateId)) {
                throw new IllegalStateException("Item " + itemId + " references missing item " + templateId);
            }
        }
    }

    private static boolean isConfigured(String effectType, JsonNode root) {
        return "configured".equals(effectType) || root.has("version") || root.has("use");
    }

    private static boolean hasEffects(JsonNode root) {
        JsonNode effects = root.path("use").path("effects");
        return effects.isArray() && !effects.isEmpty();
    }

    private static int shopPurchaseLimit(JsonNode root) {
        return Math.max(0, root.path("shop").path("purchaseLimit").asInt(root.path("shopPurchaseLimit").asInt(0)));
    }

    private static JsonNode readJson(String value) {
        try {
            return OBJECT_MAPPER.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception error) {
            throw new IllegalStateException("Invalid item effect JSON", error);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            String text = value.asText("");
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static boolean legacyUsable(String effectType, String itemCategory) {
        return effectType != null
            && Set.of("staminaPotion", "attributePotion", "levelBoost", "equipmentSetChest", "equipmentProgressBoost", "chest")
            .contains(effectType)
            || "chest".equals(itemCategory);
    }

    private static String legacyTypeLabel(String effectType, String itemCategory, String itemType) {
        return switch (effectType == null ? "" : effectType) {
            case "staminaPotion" -> "疲劳药水";
            case "attributePotion" -> "属性药水";
            case "levelBoost" -> "成长药水";
            case "equipmentSetChest" -> "套装宝箱";
            case "equipmentProgressBoost" -> "成长券";
            case "enhancementStone" -> "强化石";
            case "sweepTicket" -> "扫荡符";
            case "fragment" -> "碎片";
            case "gem" -> "宝石";
            case "chest" -> "宝箱";
            default -> {
                if ("chest".equals(itemCategory)) {
                    yield "宝箱";
                }
                if ("material".equals(itemCategory)) {
                    yield "材料";
                }
                if ("consumable".equals(itemCategory)) {
                    yield "消耗品";
                }
                yield itemType == null || itemType.isBlank() ? "物品" : itemType;
            }
        };
    }

    private static String legacySummary(
        String effectType,
        String itemCategory,
        String itemType,
        String description,
        JsonNode root
    ) {
        return switch (effectType == null ? "" : effectType) {
            case "staminaPotion" -> "恢复疲劳 +" + root.path("amount").asInt(0);
            case "attributePotion" -> attributeName(root.path("attribute").asText("")) + " +" + root.path("amount").asInt(0);
            case "levelBoost" -> "直升 Lv." + root.path("targetLevel").asInt(60) + " · 获得 9 件史诗装备";
            case "equipmentSetChest" -> "开启获得 Lv." + root.path("equipmentLevel").asInt(1) + " 九件套装备";
            case "equipmentProgressBoost" -> "ascension".equals(root.path("progression").asText(""))
                ? "全部装备升阶至 " + root.path("targetLevel").asInt(5) + " 阶"
                : "全部装备强化至 +" + root.path("targetLevel").asInt(15);
            case "enhancementStone" -> description == null || description.isBlank() ? "强化时提升成功率" : description;
            case "sweepTicket" -> description == null || description.isBlank() ? "副本扫荡时消耗" : description;
            case "chest" -> "开启后随机获得奖励";
            default -> description == null ? "" : description;
        };
    }

    private static String configuredSummary(JsonNode root, String description) {
        JsonNode effects = root.path("use").path("effects");
        if (effects.isArray() && !effects.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode effect : effects) {
                String op = effect.path("op").asText("");
                switch (op) {
                    case "setLevel" -> parts.add("直升 Lv." + effect.path("targetLevel").asInt(effect.path("level").asInt(1)));
                    case "grantEquipmentPackage" -> parts.add("获得 9 件" + qualityName(effect.path("equipmentQuality").asText(effect.path("quality").asText(""))) + "装备");
                    case "setEquipmentProgress" -> parts.add("提升装备" + progressionName(effect.path("progression").asText("")));
                    case "restoreStamina" -> parts.add("恢复疲劳 +" + effect.path("amount").asInt(0));
                    case "grantItems" -> parts.add("获得物品");
                    case "lootTableRef", "randomOne", "randomMany" -> parts.add("随机奖励");
                    default -> {
                    }
                }
            }
            if (!parts.isEmpty()) {
                return String.join(" · ", parts);
            }
        }
        return description == null ? "" : description;
    }

    private static String legacyUsageHint(String effectType, String itemCategory, String itemType, JsonNode root) {
        return switch (effectType == null ? "" : effectType) {
            case "staminaPotion" -> "疲劳不足时使用。";
            case "attributePotion" -> "使用后永久增加角色属性。";
            case "levelBoost" -> "使用后立即升级，并获得对应职业九件装备。";
            case "equipmentSetChest" -> "开启后一次性获得对应等级九件装备。";
            case "equipmentProgressBoost" -> "使用后直接提升背包和已穿戴装备加工进度。";
            case "enhancementStone" -> "装备强化时作为材料使用。";
            case "sweepTicket" -> "在副本大厅扫荡时自动消耗，不能直接使用。";
            case "chest" -> "开启后按权重产出奖励。";
            default -> "chest".equals(itemCategory) ? "开启后获得奖励。" : "";
        };
    }

    private static boolean legacyMarketable(
        String effectType,
        String itemCategory,
        String itemType,
        JsonNode root,
        boolean tradeableDefault
    ) {
        if (root.path("dropPolicy").asText("").equals("shopOnly")
            || (effectType != null && Set.of("levelBoost", "equipmentSetChest", "equipmentProgressBoost").contains(effectType))) {
            return false;
        }
        return tradeableDefault
            && ("equipment".equals(itemCategory)
                || "material".equals(itemCategory)
                || "consumable".equals(itemCategory)
                || "chest".equals(itemCategory)
                || "gem".equals(itemType)
                || "gem".equals(effectType));
    }

    private static String legacyRobotPolicy(String effectType, JsonNode root) {
        if (root.path("dropPolicy").asText("").equals("shopOnly")
            || (effectType != null && Set.of("levelBoost", "equipmentSetChest", "equipmentProgressBoost").contains(effectType))) {
            return "never";
        }
        if (effectType != null && Set.of("staminaPotion", "attributePotion", "chest").contains(effectType)) {
            return "situational";
        }
        return "default";
    }

    private static List<String> legacyTags(String effectType, String itemCategory, JsonNode root) {
        List<String> tags = new ArrayList<>();
        if (effectType != null && !effectType.isBlank()) {
            tags.add(effectType);
        }
        if (itemCategory != null && !itemCategory.isBlank()) {
            tags.add(itemCategory);
        }
        if (root.path("dropPolicy").asText("").equals("shopOnly")) {
            tags.add("shopOnly");
        }
        return List.copyOf(tags);
    }

    private static String attributeName(String key) {
        return switch (key) {
            case "strength" -> "力量";
            case "agility" -> "敏捷";
            case "constitution" -> "体质";
            case "intelligence" -> "智力";
            case "spirit" -> "精神";
            default -> "属性";
        };
    }

    private static String qualityName(String quality) {
        return switch (quality) {
            case "immortal" -> "不朽";
            case "legendary" -> "传说";
            case "epic" -> "史诗";
            case "rare" -> "稀有";
            case "uncommon" -> "优秀";
            default -> "";
        };
    }

    private static String progressionName(String progression) {
        return switch (progression) {
            case "enhancement" -> "强化";
            case "refine" -> "淬炼";
            case "ascension" -> "升阶";
            default -> "进度";
        };
    }
}
