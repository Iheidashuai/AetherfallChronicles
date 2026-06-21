package com.mythicrealm.api.gameplay.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythicrealm.api.gameplay.announcement.AnnouncementService;
import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.stamina.StaminaService;
import com.mythicrealm.api.gameplay.stamina.StaminaService.StaminaSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ItemEffectEngine {
    private static final String[] SYNTHESIS_CHEST_SLOTS =
        {"weapon", "helmet", "armor", "legs", "boots", "gloves", "necklace", "ring"};

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final StaminaService staminaService;
    private final AnnouncementService announcementService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<InventoryService> inventoryServiceProvider;

    public ItemEffectEngine(
        JdbcTemplate jdbcTemplate,
        PlayerService playerService,
        StaminaService staminaService,
        AnnouncementService announcementService,
        ObjectMapper objectMapper,
        ObjectProvider<InventoryService> inventoryServiceProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
        this.staminaService = staminaService;
        this.announcementService = announcementService;
        this.objectMapper = objectMapper;
        this.inventoryServiceProvider = inventoryServiceProvider;
    }

    public ItemEffectMetadata preview(ItemRecord item, PlayerRecord player) {
        return ItemEffectMetadata.fromItemRecord(item);
    }

    public boolean canUse(ItemRecord item, PlayerRecord player) {
        if (!ItemEffectMetadata.fromItemRecord(item).usable()) {
            return false;
        }
        try {
            validateConditions(playerService.requireById(player.id()), configuredEffect(item).path("use").path("conditions"));
            return true;
        } catch (ApiException error) {
            return false;
        }
    }

    public ApplyResult apply(PlayerRecord actor, ItemRecord item) {
        ItemEffectMetadata metadata = ItemEffectMetadata.fromItemRecord(item);
        if (!metadata.usable()) {
            throw ApiException.badRequest("This item cannot be used");
        }
        PlayerRecord player = playerService.requireById(actor.id());
        JsonNode config = configuredEffect(item);
        JsonNode use = config.path("use");
        JsonNode effects = use.path("effects");
        validateConditions(player, use.path("conditions"));

        int consumeSelf = Math.max(0, use.path("consumeSelf").asInt(1));
        for (int i = 0; i < consumeSelf; i++) {
            inventory().consumeOne(player.id(), item.id());
        }

        EffectContext context = new EffectContext(
            player,
            item,
            new Random(System.nanoTime() + item.id()),
            new ArrayList<>(),
            new ArrayList<>(),
            staminaService.snapshot(player.id())
        );
        if (effects.isArray()) {
            for (JsonNode effect : effects) {
                applyEffect(context, effect);
            }
        }
        addConfiguredEvents(context, use.path("events"));
        context.events().add(0, new ItemEffectEvent("itemUsed", item.effectType(), 1));

        String message = useMessage(item, metadata, context);
        return new ApplyResult(
            message,
            item.effectType() == null || item.effectType().isBlank() ? "configured" : item.effectType(),
            List.copyOf(context.rewards()),
            context.stamina(),
            List.copyOf(context.events())
        );
    }

    public int purchaseLimit(String effectType, String effectValueJson) {
        return ItemEffectMetadata.shopPurchaseLimit(effectType, effectValueJson);
    }

    private void validateConditions(PlayerRecord player, JsonNode conditions) {
        if (!conditions.isArray()) {
            return;
        }
        for (JsonNode condition : conditions) {
            String op = condition.path("op").asText("");
            switch (op) {
                case "playerLevelAtLeast" -> {
                    int level = condition.path("level").asInt(1);
                    if (player.level() < level) {
                        throw ApiException.badRequest("角色等级不足，需要 Lv." + level);
                    }
                }
                case "playerLevelAtMost" -> {
                    int level = condition.path("level").asInt(PlayerService.MAX_LEVEL);
                    if (player.level() > level) {
                        throw ApiException.badRequest("该道具只能在 Lv." + level + " 前使用");
                    }
                }
                case "hasItems" -> validateHasItems(player.id(), condition.path("items"));
                default -> throw ApiException.badRequest("Unsupported item condition: " + op);
            }
        }
    }

    private void validateHasItems(long playerId, JsonNode items) {
        if (!items.isArray()) {
            throw ApiException.badRequest("Item condition is missing item list");
        }
        for (JsonNode entry : items) {
            String templateId = entry.path("templateId").asText("");
            int quantity = Math.max(1, entry.path("quantity").asInt(1));
            if (templateId.isBlank() || inventory().stackableQuantity(playerId, templateId) < quantity) {
                throw ApiException.badRequest("材料不足: " + templateId);
            }
        }
    }

    private void applyEffect(EffectContext context, JsonNode effect) {
        String op = effect.path("op").asText("");
        switch (op) {
            case "grantGold" -> grantGold(context.player().id(), amountLong(effect, "amount", 0));
            case "grantRealMoney" -> grantRealMoney(context.player().id(), amountLong(effect, "amount", 0));
            case "restoreStamina" -> context.stamina(staminaService.add(context.player().id(), amountInt(effect, "amount", 0)));
            case "gainExperience" -> playerService.applyRewards(context.player().id(), amountInt(effect, "amount", 0), 0);
            case "setLevel" -> setLevel(context, targetLevel(effect));
            case "addAttributes" -> addAttributes(context.player().id(), effect.path("attributes"));
            case "grantItems" -> grantItems(context, effect);
            case "consumeItems" -> consumeItems(context.player().id(), effect.path("items"));
            case "randomOne" -> applyRandomOne(context, effect.path("options"));
            case "randomMany" -> applyRandomMany(context, effect);
            case "lootTableRef" -> applyLootTableRef(context, effect);
            case "grantEquipmentPackage" -> grantEquipmentPackage(context, effect);
            case "setEquipmentProgress" -> setEquipmentProgress(context, effect);
            case "emitQuestEvent" -> context.events().add(eventFrom(effect));
            default -> throw ApiException.badRequest("Unsupported item effect op: " + op);
        }
    }

    private void setLevel(EffectContext context, int targetLevel) {
        if (targetLevel <= 1 || targetLevel > PlayerService.MAX_LEVEL) {
            throw ApiException.badRequest("Level boost is missing a valid target level");
        }
        PlayerRecord current = playerService.requireById(context.player().id());
        if (current.level() >= targetLevel) {
            throw ApiException.badRequest("角色已达到 Lv." + targetLevel + "，无法使用该道具");
        }
        inventory().boostPlayerToLevel(current, targetLevel);
        PlayerRecord boosted = playerService.requireById(current.id());
        announcementService.publishLevelMilestones(boosted.name(), current.level(), boosted.level());
        context.player(boosted);
    }

    private void addAttributes(long playerId, JsonNode attributes) {
        if (!attributes.isObject()) {
            throw ApiException.badRequest("Attribute effect is missing attributes");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = attributes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            int amount = field.getValue().asInt(0);
            if (amount == 0) {
                continue;
            }
            String column = attributeColumn(field.getKey());
            jdbcTemplate.update("UPDATE player SET " + column + " = " + column + " + ? WHERE id = ?", amount, playerId);
        }
    }

    private void grantItems(EffectContext context, JsonNode effect) {
        JsonNode items = effect.path("items");
        if (items.isMissingNode() || !items.isArray()) {
            ObjectNode single = objectMapper.createObjectNode();
            single.put("templateId", effect.path("templateId").asText(""));
            single.put("quantity", effect.path("quantity").asInt(1));
            ArrayNode array = objectMapper.createArrayNode();
            array.add(single);
            items = array;
        }
        for (JsonNode entry : items) {
            String templateId = entry.path("templateId").asText("");
            int quantity = quantity(entry, context.random());
            context.rewards().addAll(inventory().grantItem(context.player().id(), templateId, quantity, context.random()));
        }
    }

    private void consumeItems(long playerId, JsonNode items) {
        if (!items.isArray()) {
            throw ApiException.badRequest("Consume item effect is missing item list");
        }
        for (JsonNode entry : items) {
            inventory().consumeQuantityByTemplate(
                playerId,
                entry.path("templateId").asText(""),
                Math.max(1, entry.path("quantity").asInt(1))
            );
        }
    }

    private void applyRandomOne(EffectContext context, JsonNode options) {
        JsonNode option = rollOption(options, context.random());
        if (option.hasNonNull("templateId")) {
            ObjectNode grant = objectMapper.createObjectNode();
            grant.put("op", "grantItems");
            ArrayNode items = grant.putArray("items");
            ObjectNode item = items.addObject();
            item.put("templateId", option.path("templateId").asText(""));
            copyQuantity(option, item);
            grantItems(context, grant);
            return;
        }
        JsonNode effects = option.path("effects");
        if (!effects.isArray()) {
            throw ApiException.badRequest("Random reward option has no effects");
        }
        for (JsonNode nested : effects) {
            applyEffect(context, nested);
        }
    }

    private void applyRandomMany(EffectContext context, JsonNode effect) {
        int count = Math.max(1, effect.path("count").asInt(effect.path("times").asInt(1)));
        for (int i = 0; i < count; i++) {
            applyRandomOne(context, effect.path("options"));
        }
    }

    private JsonNode rollOption(JsonNode options, Random random) {
        if (!options.isArray() || options.isEmpty()) {
            throw ApiException.badRequest("Random reward has no options");
        }
        int totalWeight = 0;
        for (JsonNode option : options) {
            totalWeight += Math.max(1, option.path("weight").asInt(1));
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (JsonNode option : options) {
            cursor += Math.max(1, option.path("weight").asInt(1));
            if (roll < cursor) {
                return option;
            }
        }
        return options.get(options.size() - 1);
    }

    private void applyLootTableRef(EffectContext context, JsonNode effect) {
        String chestTemplateId = effect.path("chestTemplateId").asText(context.item().templateId());
        String tieredGear = synthesisChestGear(chestTemplateId, playerService.requireById(context.player().id()).level(), context.random());
        if (tieredGear != null) {
            context.rewards().addAll(inventory().grantItem(context.player().id(), tieredGear, 1, context.random()));
            return;
        }
        ChestLoot loot = rollChestLoot(chestTemplateId, context.random());
        context.rewards().addAll(inventory().grantItem(context.player().id(), loot.rewardTemplateId(), loot.quantity(), context.random()));
    }

    private void grantEquipmentPackage(EffectContext context, JsonNode effect) {
        PlayerRecord current = playerService.requireById(context.player().id());
        context.rewards().addAll(inventory().grantEquipmentPackage(current, equipmentPackageEffect(effect), effect.path("professionNamed").asBoolean(false)));
    }

    private JsonNode equipmentPackageEffect(JsonNode effect) {
        ObjectNode copy = effect.deepCopy();
        if (effect.hasNonNull("level") && !copy.has("equipmentLevel")) {
            copy.put("equipmentLevel", effect.path("level").asInt());
        }
        if (effect.hasNonNull("quality") && !copy.has("equipmentQuality")) {
            copy.put("equipmentQuality", effect.path("quality").asText());
        }
        if (effect.hasNonNull("templatePrefix") && !copy.has("equipmentTemplatePrefix")) {
            copy.put("equipmentTemplatePrefix", effect.path("templatePrefix").asText());
        }
        if (effect.hasNonNull("tier") && !copy.has("equipmentTier")) {
            copy.put("equipmentTier", effect.path("tier").asText());
        }
        return copy;
    }

    private void setEquipmentProgress(EffectContext context, JsonNode effect) {
        String progression = effect.path("progression").asText("");
        int targetLevel = progressTarget(effect, progression);
        String scope = effect.path("scope").asText("playableEquipment");
        List<ItemRecord> targets = equipmentTargets(context.player().id(), scope).stream()
            .filter(candidate -> equipmentProgressLevel(candidate, progression) < targetLevel)
            .sorted(Comparator.comparingInt(ItemRecord::requiredLevel).reversed().thenComparingLong(ItemRecord::id))
            .toList();
        if (targets.isEmpty()) {
            throw ApiException.badRequest("当前没有需要提升的装备");
        }
        String focus = effect.path("focus").asText("balanced");
        for (ItemRecord target : targets) {
            switch (progression) {
                case "enhancement" -> jdbcTemplate.update(
                    "UPDATE item_instance SET enhancement_level = ?, enhancement_luck = 0 WHERE id = ? AND player_id = ?",
                    targetLevel,
                    target.id(),
                    context.player().id()
                );
                case "ascension" -> jdbcTemplate.update(
                    "UPDATE item_instance SET ascension_level = ?, ascension_luck = 0 WHERE id = ? AND player_id = ?",
                    targetLevel,
                    target.id(),
                    context.player().id()
                );
                case "refine" -> jdbcTemplate.update(
                    "UPDATE item_instance SET refine_level = ?, refine_focus = ? WHERE id = ? AND player_id = ?",
                    targetLevel,
                    focus,
                    target.id(),
                    context.player().id()
                );
                default -> throw ApiException.badRequest("Unsupported equipment progression: " + progression);
            }
        }
        context.rewards().addAll(targets.stream().map(target -> inventory().requireItem(target.id())).toList());
    }

    private List<ItemRecord> equipmentTargets(long playerId, String scope) {
        return switch (scope) {
            case "equipped" -> new ArrayList<>(inventory().equippedItems(playerId).values());
            case "backpack" -> inventory().inventoryItems(playerId).stream().filter(ItemRecord::equipment).toList();
            case "playableEquipment", "allPlayableEquipment", "allOwned" -> inventory().ownedPlayableEquipment(playerId);
            default -> throw ApiException.badRequest("Unsupported equipment scope: " + scope);
        };
    }

    private int equipmentProgressLevel(ItemRecord item, String progression) {
        return switch (progression) {
            case "enhancement" -> item.enhancementLevel();
            case "ascension" -> item.ascensionLevel();
            case "refine" -> item.refineLevel();
            default -> throw ApiException.badRequest("Unsupported equipment progression: " + progression);
        };
    }

    private int progressTarget(JsonNode effect, String progression) {
        int raw = Math.max(1, effect.path("target").asInt(effect.path("targetLevel").asInt(1)));
        return switch (progression) {
            case "enhancement" -> Math.min(15, raw);
            case "refine", "ascension" -> Math.min(5, raw);
            default -> throw ApiException.badRequest("Unsupported equipment progression: " + progression);
        };
    }

    private void addConfiguredEvents(EffectContext context, JsonNode events) {
        if (!events.isArray()) {
            return;
        }
        for (JsonNode event : events) {
            context.events().add(eventFrom(event));
        }
    }

    private ItemEffectEvent eventFrom(JsonNode node) {
        String type = node.path("type").asText(node.path("event").asText(""));
        if (type.isBlank()) {
            throw ApiException.badRequest("Item event is missing type");
        }
        String targetId = node.path("targetId").asText(null);
        int amount = Math.max(1, node.path("amount").asInt(1));
        return new ItemEffectEvent(type, targetId, amount);
    }

    private JsonNode configuredEffect(ItemRecord item) {
        JsonNode root = readEffect(item.effectValueJson());
        if ("configured".equals(item.effectType()) || root.has("version") || root.has("use")) {
            return root;
        }
        return legacyConfig(item, root);
    }

    private JsonNode legacyConfig(ItemRecord item, JsonNode root) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("version", 1);
        config.set("tags", legacyTags(item, root));
        ObjectNode ui = config.putObject("ui");
        ItemEffectMetadata metadata = ItemEffectMetadata.fromItemRecord(item);
        ui.put("typeLabel", metadata.typeLabel());
        ui.put("actionLabel", metadata.actionLabel());
        ui.put("summary", metadata.effectSummary());
        ui.put("usageHint", metadata.usageHint());
        ObjectNode use = config.putObject("use");
        use.put("consumeSelf", 1);
        ArrayNode conditions = use.putArray("conditions");
        ArrayNode effects = use.putArray("effects");
        ArrayNode events = use.putArray("events");
        if (ItemEffectMetadata.shopPurchaseLimit(item.effectType(), item.effectValueJson()) > 0) {
            config.putObject("shop").put("purchaseLimit", ItemEffectMetadata.shopPurchaseLimit(item.effectType(), item.effectValueJson()));
        }
        config.putObject("robot").put("policy", metadata.robotPolicy());
        config.putObject("market").put("tradeable", metadata.marketable());

        switch (item.effectType() == null ? "" : item.effectType()) {
            case "staminaPotion" -> {
                effects.addObject().put("op", "restoreStamina").put("amount", root.path("amount").asInt(0));
                events.addObject().put("type", "staminaPotionUsed");
            }
            case "attributePotion" -> {
                int maxLevel = root.path("maxLevel").asInt(PlayerService.MAX_LEVEL);
                conditions.addObject().put("op", "playerLevelAtMost").put("level", maxLevel);
                ObjectNode attributes = effects.addObject().put("op", "addAttributes").putObject("attributes");
                attributes.put(root.path("attribute").asText(""), root.path("amount").asInt(0));
            }
            case "levelBoost" -> {
                effects.addObject().put("op", "setLevel").put("targetLevel", root.path("targetLevel").asInt(0));
                ObjectNode packageEffect = effects.addObject();
                packageEffect.put("op", "grantEquipmentPackage");
                copyPackageFields(root, packageEffect);
                packageEffect.put("professionNamed", true);
            }
            case "equipmentSetChest" -> {
                ObjectNode packageEffect = effects.addObject();
                packageEffect.put("op", "grantEquipmentPackage");
                copyPackageFields(root, packageEffect);
                events.addObject().put("type", "chestOpened");
            }
            case "equipmentProgressBoost" -> {
                effects.addObject()
                    .put("op", "setEquipmentProgress")
                    .put("progression", root.path("progression").asText(""))
                    .put("targetLevel", root.path("targetLevel").asInt(1))
                    .put("scope", root.path("scope").asText("playableEquipment"));
            }
            case "chest" -> {
                effects.addObject().put("op", "lootTableRef").put("chestTemplateId", item.templateId());
                events.addObject().put("type", "chestOpened");
            }
            default -> {
                if ("chest".equals(item.itemCategory())) {
                    effects.addObject().put("op", "lootTableRef").put("chestTemplateId", item.templateId());
                    events.addObject().put("type", "chestOpened");
                }
            }
        }
        return config;
    }

    private void copyPackageFields(JsonNode source, ObjectNode target) {
        for (String field : List.of("equipmentLevel", "equipmentQuality", "equipmentTier", "equipmentTemplatePrefix")) {
            if (source.has(field)) {
                target.set(field, source.get(field));
            }
        }
        if (source.has("equipmentTemplates")) {
            target.set("equipmentTemplates", source.get("equipmentTemplates"));
        }
    }

    private ArrayNode legacyTags(ItemRecord item, JsonNode root) {
        ArrayNode tags = objectMapper.createArrayNode();
        if (item.effectType() != null && !item.effectType().isBlank()) {
            tags.add(item.effectType());
        }
        if (item.itemCategory() != null && !item.itemCategory().isBlank()) {
            tags.add(item.itemCategory());
        }
        if ("shopOnly".equals(root.path("dropPolicy").asText(""))) {
            tags.add("shopOnly");
        }
        return tags;
    }

    private ChestLoot rollChestLoot(String chestTemplateId, Random random) {
        List<ChestLootOption> options = jdbcTemplate.query(
            """
            SELECT reward_template_id, min_quantity, max_quantity, weight
            FROM chest_loot
            WHERE chest_template_id = ?
            """,
            (rs, rowNum) -> new ChestLootOption(
                rs.getString("reward_template_id"),
                rs.getInt("min_quantity"),
                rs.getInt("max_quantity"),
                Math.max(1, rs.getInt("weight"))
            ),
            chestTemplateId
        );
        if (options.isEmpty()) {
            throw ApiException.badRequest("Chest has no loot table: " + chestTemplateId);
        }
        int totalWeight = options.stream().mapToInt(ChestLootOption::weight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (ChestLootOption option : options) {
            cursor += option.weight();
            if (roll < cursor) {
                int min = Math.max(1, option.minQuantity());
                int max = Math.max(min, option.maxQuantity());
                int quantity = min + random.nextInt(max - min + 1);
                return new ChestLoot(option.rewardTemplateId(), quantity);
            }
        }
        ChestLootOption fallback = options.get(options.size() - 1);
        return new ChestLoot(fallback.rewardTemplateId(), Math.max(1, fallback.minQuantity()));
    }

    private String synthesisChestGear(String chestTemplateId, int playerLevel, Random random) {
        String quality;
        if ("chest_legendary_cache".equals(chestTemplateId)) {
            quality = "legendary";
        } else if ("chest_immortal_cache".equals(chestTemplateId)) {
            quality = "immortal";
        } else {
            return null;
        }
        int tier = Math.max(60, Math.min(90, (playerLevel / 10) * 10));
        String slot = SYNTHESIS_CHEST_SLOTS[random.nextInt(SYNTHESIS_CHEST_SLOTS.length)];
        return "eq_bloodmoon_l" + tier + "_" + slot + "_" + quality;
    }

    private String useMessage(ItemRecord item, ItemEffectMetadata metadata, EffectContext context) {
        String rewards = context.rewards().isEmpty() ? "" : "，获得 " + context.rewards().size() + " 件奖励";
        String summary = metadata.effectSummary() == null || metadata.effectSummary().isBlank()
            ? ""
            : "：" + metadata.effectSummary();
        return "已" + metadata.actionLabel() + " " + item.name() + summary + rewards;
    }

    private long amountLong(JsonNode node, String field, long fallback) {
        return Math.max(0, node.path(field).asLong(fallback));
    }

    private int amountInt(JsonNode node, String field, int fallback) {
        return Math.max(0, node.path(field).asInt(fallback));
    }

    private int targetLevel(JsonNode node) {
        return node.path("targetLevel").asInt(node.path("level").asInt(0));
    }

    private int quantity(JsonNode node, Random random) {
        if (node.hasNonNull("quantity")) {
            return Math.max(1, node.path("quantity").asInt(1));
        }
        int min = Math.max(1, node.path("minQuantity").asInt(1));
        int max = Math.max(min, node.path("maxQuantity").asInt(min));
        return min + random.nextInt(max - min + 1);
    }

    private void copyQuantity(JsonNode source, ObjectNode target) {
        for (String field : List.of("quantity", "minQuantity", "maxQuantity")) {
            if (source.has(field)) {
                target.set(field, source.get(field));
            }
        }
    }

    private void grantGold(long playerId, long amount) {
        if (amount > 0) {
            jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", amount, playerId);
        }
    }

    private void grantRealMoney(long playerId, long amount) {
        if (amount > 0) {
            jdbcTemplate.update("UPDATE player SET real_money = real_money + ? WHERE id = ?", amount, playerId);
        }
    }

    private JsonNode readEffect(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception error) {
            throw ApiException.badRequest("Invalid item effect config");
        }
    }

    private String attributeColumn(String attribute) {
        return switch (attribute) {
            case "strength", "agility", "constitution", "intelligence", "spirit" -> attribute;
            default -> throw ApiException.badRequest("Unsupported attribute: " + attribute);
        };
    }

    private InventoryService inventory() {
        return inventoryServiceProvider.getObject();
    }

    public record ApplyResult(
        String message,
        String effectType,
        List<ItemRecord> rewards,
        StaminaSnapshot stamina,
        List<ItemEffectEvent> events
    ) {
    }

    private static final class EffectContext {
        private PlayerRecord player;
        private final ItemRecord item;
        private final Random random;
        private final List<ItemRecord> rewards;
        private final List<ItemEffectEvent> events;
        private StaminaSnapshot stamina;

        private EffectContext(
            PlayerRecord player,
            ItemRecord item,
            Random random,
            List<ItemRecord> rewards,
            List<ItemEffectEvent> events,
            StaminaSnapshot stamina
        ) {
            this.player = player;
            this.item = item;
            this.random = random;
            this.rewards = rewards;
            this.events = events;
            this.stamina = stamina;
        }

        private PlayerRecord player() {
            return player;
        }

        private void player(PlayerRecord player) {
            this.player = player;
        }

        private ItemRecord item() {
            return item;
        }

        private Random random() {
            return random;
        }

        private List<ItemRecord> rewards() {
            return rewards;
        }

        private List<ItemEffectEvent> events() {
            return events;
        }

        private StaminaSnapshot stamina() {
            return stamina;
        }

        private void stamina(StaminaSnapshot stamina) {
            this.stamina = stamina;
        }
    }

    private record ChestLootOption(String rewardTemplateId, int minQuantity, int maxQuantity, int weight) {
    }

    private record ChestLoot(String rewardTemplateId, int quantity) {
    }
}
