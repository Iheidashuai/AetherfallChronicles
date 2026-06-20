package com.mythicrealm.api.gameplay.market;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.dungeon.DungeonService;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import com.mythicrealm.api.gameplay.robot.RobotActivityLogService;
import com.mythicrealm.api.gameplay.robot.RobotEquipmentService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketService {
    private static final int ROBOT_LISTING_TARGET = 1200;
    private static final int LISTING_PAGE_LIMIT = 1200;
    private static final int PRICE_CAP_MULTIPLIER = 3;
    private static final int MARKET_TAX_RATE = 8;
    static final String MARK_LISTING_SOLD_SQL =
        "UPDATE market_listing SET status = 'sold', item_id = NULL, buyer_player_id = ?, sold_at = CURRENT_TIMESTAMP WHERE id = ?";
    static final String MARK_LISTING_CANCELED_SQL =
        "UPDATE market_listing SET status = 'canceled', item_id = NULL WHERE id = ?";

    /** Gold the seller receives after the market tax, floored at 1. Pure for unit testing. */
    static int netSellerProceeds(int price) {
        return Math.max(1, price * (100 - MARKET_TAX_RATE) / 100);
    }

    /** Maximum allowed listing price given the appraised/recommended price. Pure for unit testing. */
    static int maxListingPrice(int recommendedPrice) {
        return recommendedPrice * PRICE_CAP_MULTIPLIER;
    }

    private final JdbcTemplate jdbcTemplate;
    private final GameConfigService gameConfigService;
    private final InventoryService inventoryService;
    private final QuestService questService;
    private final RobotActivityLogService robotActivityLogService;
    private final RobotEquipmentService robotEquipmentService;
    private final DungeonService dungeonService;
    private final RechargeService rechargeService;
    private final Map<String, String> originCache = new ConcurrentHashMap<>();
    private final Object robotListingMonitor = new Object();
    private Instant nextMarketPulseAt = Instant.now().plusSeconds(5);

    public MarketService(
        JdbcTemplate jdbcTemplate,
        GameConfigService gameConfigService,
        InventoryService inventoryService,
        QuestService questService,
        RobotActivityLogService robotActivityLogService,
        RobotEquipmentService robotEquipmentService,
        DungeonService dungeonService,
        RechargeService rechargeService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.gameConfigService = gameConfigService;
        this.inventoryService = inventoryService;
        this.questService = questService;
        this.robotActivityLogService = robotActivityLogService;
        this.robotEquipmentService = robotEquipmentService;
        this.dungeonService = dungeonService;
        this.rechargeService = rechargeService;
    }

    @Transactional
    public MarketSnapshot listings(PlayerRecord player) {
        ensureRobotListings(Math.max(12, player.level() + 14));
        List<MarketListingView> listings = activeListings();
        int playerListings = (int) listings.stream().filter(MarketListingView::playerListing).count();
        int robotListings = listings.size() - playerListings;
        Integer soldRecently = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM market_listing WHERE status = 'sold' AND sold_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 HOUR)",
            Integer.class
        );
        int averagePrice = listings.stream().mapToInt(MarketListingView::price).sum() / Math.max(1, listings.size());
        return new MarketSnapshot(
            listings,
            marketActivities(listings),
            playerSales(player.id()),
            new MarketRules(MARKET_TAX_RATE, PRICE_CAP_MULTIPLIER, 1, "寄售价最高为商会估值 3 倍，机器人会持续上架、捡漏，成交收益扣除 8% 商会税。"),
            900 + Math.abs(Objects.hash(player.id(), System.currentTimeMillis() / 120_000)) % 520,
            robotListings,
            playerListings,
            soldRecently == null ? 0 : soldRecently,
            averagePrice
        );
    }

    @Transactional
    public MarketListingView listItem(PlayerRecord player, long itemId, int quantity, int unitPrice) {
        if (unitPrice <= 0) {
            throw ApiException.badRequest("寄售单价必须大于 0");
        }
        ItemRecord item = inventoryService.requireOwnedItem(player.id(), itemId);
        ItemTemplate template = gameConfigService.requireItem(item.templateId());
        if (!template.tradeable()) {
            throw ApiException.badRequest("该物品不可寄售");
        }
        inventoryService.inventorySlot(player.id(), itemId).orElseThrow(() -> ApiException.badRequest("只能寄售背包中的物品"));
        int listingQuantity = item.stackable() ? Math.max(1, quantity) : 1;
        if (listingQuantity > Math.max(1, item.quantity())) {
            throw ApiException.badRequest("寄售数量不足，当前只有 " + Math.max(1, item.quantity()) + " 件");
        }
        ItemSnapshot snapshot = ItemSnapshot.from(item, template, originFor(item.templateId()));
        int recommendedPrice = recommendedPrice(snapshot);
        int maxPrice = maxListingPrice(recommendedPrice);
        if (unitPrice > maxPrice) {
            throw ApiException.badRequest("寄售单价超过商会估值上限，最高 " + maxPrice + " 金");
        }
        if (unitPrice < Math.max(1, template.marketMinUnitPrice())) {
            throw ApiException.badRequest("寄售单价低于商会最低价，最低 " + Math.max(1, template.marketMinUnitPrice()) + " 金");
        }

        ItemRecord listedItem = inventoryService.splitItemForMarket(player.id(), itemId, listingQuantity);
        snapshot = ItemSnapshot.from(listedItem, template, originFor(listedItem.templateId()));
        int totalPrice = Math.toIntExact((long) unitPrice * listingQuantity);
        jdbcTemplate.update(
            """
            INSERT INTO market_listing
            (seller_player_id, seller_name, item_id, item_template_id, quantity, unit_price, item_category, stackable,
             item_enhancement_level, item_enhancement_luck, item_refine_level, item_ascension_level,
             snapshot_name, snapshot_item_type, snapshot_item_category, snapshot_quality, snapshot_required_level, snapshot_attack_bonus,
             snapshot_defense_bonus, snapshot_resistance_bonus, snapshot_hp_bonus, snapshot_mp_bonus,
             snapshot_crit_bonus, snapshot_sell_price, snapshot_description, snapshot_effect_type, snapshot_effect_value,
             snapshot_processing_summary, price, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'listed')
            """,
            player.id(),
            player.name(),
            listedItem.id(),
            listedItem.templateId(),
            listingQuantity,
            unitPrice,
            snapshot.marketCategory(),
            snapshot.stackable(),
            listedItem.enhancementLevel(),
            listedItem.enhancementLuck(),
            listedItem.refineLevel(),
            listedItem.ascensionLevel(),
            snapshot.name(),
            snapshot.itemType(),
            snapshot.itemCategory(),
            snapshot.quality(),
            snapshot.requiredLevel(),
            snapshot.attackBonus(),
            snapshot.defenseBonus(),
            snapshot.resistanceBonus(),
            snapshot.hpBonus(),
            snapshot.mpBonus(),
            snapshot.critBonus(),
            snapshot.sellPrice(),
            snapshot.description(),
            snapshot.effectType(),
            snapshot.effectValueJson(),
            snapshot.processingSummary(),
            totalPrice
        );
        long listingId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return view(listingId, true, player.name(), listedItem.id(), snapshot, listingQuantity, unitPrice, totalPrice, "listed");
    }

    @Transactional
    public MarketListingView buy(PlayerRecord buyer, long listingId) {
        ListingRow row = requireListing(listingId);
        if (!"listed".equals(row.status())) {
            throw ApiException.badRequest("这件商品已经无法购买");
        }
        if (row.sellerPlayerId() != null && row.sellerPlayerId() == buyer.id()) {
            throw ApiException.badRequest("不能购买自己的寄售");
        }
        if (buyer.gold() < row.price()) {
            throw ApiException.badRequest("金币不足");
        }
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", row.price(), buyer.id());
        if (row.sellerPlayerId() != null) {
            int sellerGold = netSellerProceeds(row.price());
            jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", sellerGold, row.sellerPlayerId());
            if (row.itemId() > 0 && itemExists(row.itemId())) {
                inventoryService.transferItemOwner(row.itemId(), buyer.id());
                inventoryService.addExistingItemToInventory(buyer.id(), row.itemId());
            } else {
                createItemFromSnapshot(buyer.id(), row.item());
            }
            if ("robot".equals(row.sellerKind())) {
                robotActivityLogService.record(row.sellerPlayerId(), "market_sell", "寄售的【" + row.item().name() + "】被 " + buyer.name() + " 买走，入账 " + sellerGold + " 金。");
            }
        } else {
            createItemFromSnapshot(buyer.id(), row.item());
        }
        jdbcTemplate.update(
            MARK_LISTING_SOLD_SQL,
            buyer.id(),
            listingId
        );
        if (row.sellerPlayerId() != null && !"robot".equals(row.sellerKind())) {
            questService.recordEvent(row.sellerPlayerId(), QuestEvent.of("marketSold"));
        }
        return view(listingId, !"robot".equals(row.sellerKind()), row.sellerName(), row.itemId(), row.item(), row.quantity(), row.unitPrice(), row.price(), "sold");
    }

    @Transactional
    public void cancel(PlayerRecord player, long listingId) {
        ListingRow row = requireListing(listingId);
        if (row.sellerPlayerId() == null || row.sellerPlayerId() != player.id()) {
            throw ApiException.badRequest("只能取消自己的寄售");
        }
        if (!"listed".equals(row.status())) {
            throw ApiException.badRequest("该寄售无法取消");
        }
        if (row.itemId() > 0 && itemExists(row.itemId())) {
            inventoryService.addExistingItemToInventory(player.id(), row.itemId());
        } else {
            createItemFromSnapshot(player.id(), row.item());
        }
        jdbcTemplate.update(MARK_LISTING_CANCELED_SQL, listingId);
    }

    private List<MarketListingView> activeListings() {
        return jdbcTemplate.query(
            """
            SELECT ml.id, ml.seller_player_id, ml.seller_name, ml.item_id,
                   COALESCE(ii.template_id, ml.item_template_id) AS snapshot_template_id,
                   COALESCE(ii.name, ml.snapshot_name, it.name) AS snapshot_name,
                   COALESCE(ii.item_type, ml.snapshot_item_type, it.item_type) AS snapshot_item_type,
                   ml.snapshot_item_category AS snapshot_item_category,
                   ml.item_category AS market_category,
                   COALESCE(ml.stackable, it.stackable) AS stackable,
                   GREATEST(1, COALESCE(ml.quantity, ii.quantity, 1)) AS quantity,
                   COALESCE(ii.quality, ml.snapshot_quality, it.quality) AS snapshot_quality,
                   COALESCE(ii.required_level, ml.snapshot_required_level, it.required_level) AS snapshot_required_level,
                   COALESCE(ii.attack_bonus, ml.snapshot_attack_bonus, it.attack_bonus) AS snapshot_attack_bonus,
                   COALESCE(ii.defense_bonus, ml.snapshot_defense_bonus, it.defense_bonus) AS snapshot_defense_bonus,
                   COALESCE(ii.resistance_bonus, ml.snapshot_resistance_bonus, it.resistance_bonus) AS snapshot_resistance_bonus,
                   COALESCE(ii.hp_bonus, ml.snapshot_hp_bonus, it.hp_bonus) AS snapshot_hp_bonus,
                   COALESCE(ii.mp_bonus, ml.snapshot_mp_bonus, it.mp_bonus) AS snapshot_mp_bonus,
                   COALESCE(ii.crit_bonus, ml.snapshot_crit_bonus, it.crit_bonus) AS snapshot_crit_bonus,
                   COALESCE(ii.sell_price, ml.snapshot_sell_price, it.sell_price) AS snapshot_sell_price,
                   COALESCE(ml.snapshot_description, it.description) AS snapshot_description,
                   COALESCE(ml.snapshot_effect_type, it.effect_type) AS snapshot_effect_type,
                   COALESCE(ml.snapshot_effect_value, it.effect_value_json) AS snapshot_effect_value,
                   COALESCE(ii.enhancement_level, ml.item_enhancement_level) AS snapshot_enhancement_level,
                   COALESCE(ii.enhancement_luck, ml.item_enhancement_luck) AS snapshot_enhancement_luck,
                   COALESCE(ii.refine_level, ml.item_refine_level) AS snapshot_refine_level,
                   COALESCE(ii.ascension_level, ml.item_ascension_level) AS snapshot_ascension_level,
                   COALESCE(ml.snapshot_processing_summary, '') AS snapshot_processing_summary,
                   CASE WHEN ml.unit_price > 0 THEN ml.unit_price ELSE ml.price END AS unit_price,
                   ml.price, ml.status, ml.created_at,
                   COALESCE(seller.controller_type, 'player') AS seller_kind
            FROM market_listing ml
            LEFT JOIN player seller ON seller.id = ml.seller_player_id
            LEFT JOIN item_instance ii ON ii.id = ml.item_id
            JOIN item_template it ON it.id = COALESCE(ii.template_id, ml.item_template_id)
            WHERE ml.status = 'listed'
            ORDER BY seller_kind = 'robot', ml.created_at DESC, ml.id DESC
            LIMIT ?
            """,
            (rs, rowNum) -> view(
                rs.getLong("id"),
                !"robot".equals(rs.getString("seller_kind")),
                rs.getString("seller_name"),
                rs.getObject("item_id") == null ? 0 : rs.getLong("item_id"),
                snapshotFromRow(rs),
                rs.getInt("quantity"),
                rs.getInt("unit_price"),
                rs.getInt("price"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            ),
            LISTING_PAGE_LIMIT
        );
    }

    private MarketListingView view(long id, boolean playerListing, String sellerName, long itemId, ItemSnapshot item, int quantity, int unitPrice, int price, String status) {
        return view(id, playerListing, sellerName, itemId, item, quantity, unitPrice, price, status, Instant.now());
    }

    private MarketListingView view(long id, boolean playerListing, String sellerName, long itemId, ItemSnapshot item, int quantity, int unitPrice, int price, String status, Instant listedAt) {
        int recommendedPrice = recommendedPrice(item);
        int priceRatio = Math.round(unitPrice * 100f / Math.max(1, recommendedPrice));
        int dealChance = dealChance(unitPrice, recommendedPrice, item.quality(), playerListing);
        return new MarketListingView(
            id,
            playerListing,
            sellerName,
            itemId,
            item,
            Math.max(1, quantity),
            Math.max(1, unitPrice),
            price,
            recommendedPrice,
            priceRatio,
            dealChance,
            status,
            playerListing ? "玩家寄售" : "冒险者寄售",
            marketTag(priceRatio, dealChance),
            listedAt
        );
    }

    private void ensureRobotListings(int maxRequiredLevel) {
        synchronized (robotListingMonitor) {
            ensureRobotListingsLocked(maxRequiredLevel);
        }
    }

    private void ensureRobotListingsLocked(int maxRequiredLevel) {
        Integer activeRobotListings = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM market_listing ml
            JOIN player seller ON seller.id = ml.seller_player_id
            WHERE ml.status = 'listed'
              AND seller.controller_type = 'robot'
            """,
            Integer.class
        );
        int activeCount = activeRobotListings == null ? 0 : activeRobotListings;
        if (activeCount >= ROBOT_LISTING_TARGET) {
            return;
        }
        List<RobotSeller> robots = robotSellers(80, new Random());
        Random random = new Random(Objects.hash(activeCount, System.currentTimeMillis() / 600_000));
        int needed = ROBOT_LISTING_TARGET - activeCount;
        int listed = 0;
        int attempts = Math.max(40, needed * 6);
        for (int i = 0; listed < needed && i < attempts && !robots.isEmpty(); i++) {
            RobotSeller robot = robots.get(random.nextInt(robots.size()));
            listed += resolveRobotDungeonLoot(robot, maxRequiredLevel, random, false).listedCount();
        }
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void simulateMarketPulse() {
        if (!hasHumanPlayer()) {
            return;
        }
        Instant now = Instant.now();
        if (now.isBefore(nextMarketPulseAt)) {
            return;
        }
        nextMarketPulseAt = now.plusSeconds(5 + new Random().nextInt(16));
        ensureRobotListings(90);
        if (new Random().nextInt(100) >= 58 || !listRobotDrop()) {
            robotBuyListing(null, null);
        }
    }

    private boolean hasHumanPlayer() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player WHERE account_id IS NOT NULL",
            Integer.class
        );
        return count != null && count > 0;
    }

    @Transactional
    public RobotLootOutcome resolveRobotDungeonLoot(
        long robotId,
        String name,
        String title,
        String profession,
        int level,
        int power,
        long gold,
        Random random
    ) {
        return resolveRobotDungeonLoot(
            new RobotSeller(robotId, name, title, profession, level, power, gold),
            Math.max(12, level + 6),
            random,
            true
        );
    }

    private boolean listRobotDrop() {
        Random random = new Random();
        List<RobotSeller> robots = robotSellers(1, random);
        if (robots.isEmpty()) {
            return false;
        }
        RobotSeller robot = robots.get(0);
        RobotLootOutcome outcome = resolveRobotDungeonLoot(robot, Math.max(12, robot.level() + 6), random, true);
        return outcome.listedCount() > 0 || outcome.equippedCount() > 0;
    }

    private RobotLootOutcome resolveRobotDungeonLoot(RobotSeller robot, int maxRequiredLevel, Random random, boolean recordActivity) {
        PlayerRecord robotPlayer = requireRobotPlayer(robot.id());
        DungeonConfig dungeon = chooseRobotDungeon(robot, maxRequiredLevel, random);
        if (dungeon == null) {
            if (recordActivity) {
                robotActivityLogService.record(robot.id(), "dungeon", "正在寻找合适的副本，暂时没有开刷。");
            }
            return new RobotLootOutcome(0, 0);
        }

        DungeonService.DungeonRunResult runResult;
        try {
            runResult = dungeonService.runDungeonForPlayer(robotPlayer, dungeon.id(), null);
        } catch (ApiException error) {
            if (recordActivity) {
                robotActivityLogService.record(robot.id(), "rest", "准备刷本前先整理背包和金币。");
            }
            return new RobotLootOutcome(0, 0);
        }
        jdbcTemplate.update(
            """
            UPDATE player
            SET dungeon_clears = dungeon_clears + 1, last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            robot.id()
        );
        if (runResult.loot().isEmpty()) {
            if (recordActivity) {
                robotActivityLogService.record(robot.id(), "dungeon", "刚刷完【" + runResult.dungeonName() + "】，这轮没有出可用装备。");
            }
            return new RobotLootOutcome(0, 0);
        }

        int listed = 0;
        int equipped = 0;
        PlayerRecord updatedRobot = runResult.player();
        for (ItemRecord item : runResult.loot()) {
            if (!item.equipment()) {
                boolean legendary = isLegendaryOrBetter(item.quality());
                if (legendary) {
                    jdbcTemplate.update(
                        "UPDATE player SET legendary_loot_count = legendary_loot_count + 1 WHERE id = ?",
                        robot.id()
                    );
                }
                if (shouldRobotListItem(item, random)) {
                    MarketListingView listing = listRobotOwnedItem(updatedRobot, robot.title(), item, "机器人副本掉落 · " + runResult.dungeonName(), random, false);
                    listed++;
                    if (recordActivity) {
                        robotActivityLogService.record(
                            robot.id(),
                            "market_list",
                            "在【" + runResult.dungeonName() + "】获得【" + listing.item().name() + quantitySuffix(listing.quantity()) + "】，留下自用储备后挂到商会 " + listing.price() + " 金。"
                        );
                    }
                }
                continue;
            }
            RobotEquipmentService.DropResolution drop = robotEquipmentService.resolveDropIfUpgrade(updatedRobot, item, random);
            boolean legendary = isLegendaryOrBetter(item.quality());
            if (legendary) {
                jdbcTemplate.update(
                    "UPDATE player SET legendary_loot_count = legendary_loot_count + 1 WHERE id = ?",
                    robot.id()
                );
            }
            if (drop.equipped()) {
                equipped++;
                if (recordActivity) {
                    RobotEquipmentService.EquipmentChange change = drop.change();
                    String text = "在【" + runResult.dungeonName() + "】打到【" + change.itemName() + "】，换到" + change.slotName() + "上，战力提升 " + change.powerGain() + "。";
                    robotActivityLogService.record(robot.id(), "equip", text);
                }
                continue;
            }

            MarketListingView listing = listRobotOwnedItem(updatedRobot, robot.title(), item, "机器人副本掉落 · " + runResult.dungeonName(), random, false);
            listed++;
            if (recordActivity) {
                robotActivityLogService.record(
                    robot.id(),
                    "market_list",
                    "在【" + runResult.dungeonName() + "】打到【" + listing.item().name() + "】，但身上已有同槽装备，挂到商会 " + listing.price() + " 金。"
                );
            }
        }
        return new RobotLootOutcome(listed, equipped);
    }

    private DungeonConfig chooseRobotDungeon(RobotSeller robot, int maxRequiredLevel, Random random) {
        int levelLimit = Math.max(1, Math.min(maxRequiredLevel, robot.level() + 6));
        List<DungeonConfig> candidates = gameConfigService.dungeons().stream()
            .filter(dungeon -> dungeon.minimumLevel() <= Math.min(levelLimit, robot.level()))
            .filter(dungeon -> dungeon.minimumPower() <= Math.max(1, robot.power()))
            .sorted(Comparator
                .comparing((DungeonConfig dungeon) -> !DungeonService.isSpecialDungeon(dungeon.id()))
                .thenComparingInt((DungeonConfig dungeon) -> Math.abs(dungeon.minimumLevel() - Math.max(1, robot.level())))
                .thenComparingInt(dungeon -> Math.abs(dungeon.minimumPower() - robot.power()))
                .thenComparing(DungeonConfig::id))
            .limit(8)
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    public boolean tryRobotBuyListing(long preferredBuyerId, String decisionReason) {
        return robotBuyListing(preferredBuyerId, decisionReason);
    }

    private boolean robotBuyListing(Long preferredBuyerId, String decisionReason) {
        Random random = new Random();
        List<ListingRow> rows = jdbcTemplate.query(
            """
            SELECT ml.id, ml.seller_player_id, ml.seller_name, ml.item_id,
                   COALESCE(ii.template_id, ml.item_template_id) AS snapshot_template_id,
                   COALESCE(ii.name, ml.snapshot_name, it.name) AS snapshot_name,
                   COALESCE(ii.item_type, ml.snapshot_item_type, it.item_type) AS snapshot_item_type,
                   ml.snapshot_item_category AS snapshot_item_category,
                   ml.item_category AS market_category,
                   COALESCE(ml.stackable, it.stackable) AS stackable,
                   GREATEST(1, COALESCE(ml.quantity, ii.quantity, 1)) AS quantity,
                   COALESCE(ii.quality, ml.snapshot_quality, it.quality) AS snapshot_quality,
                   COALESCE(ii.required_level, ml.snapshot_required_level, it.required_level) AS snapshot_required_level,
                   COALESCE(ii.attack_bonus, ml.snapshot_attack_bonus, it.attack_bonus) AS snapshot_attack_bonus,
                   COALESCE(ii.defense_bonus, ml.snapshot_defense_bonus, it.defense_bonus) AS snapshot_defense_bonus,
                   COALESCE(ii.resistance_bonus, ml.snapshot_resistance_bonus, it.resistance_bonus) AS snapshot_resistance_bonus,
                   COALESCE(ii.hp_bonus, ml.snapshot_hp_bonus, it.hp_bonus) AS snapshot_hp_bonus,
                   COALESCE(ii.mp_bonus, ml.snapshot_mp_bonus, it.mp_bonus) AS snapshot_mp_bonus,
                   COALESCE(ii.crit_bonus, ml.snapshot_crit_bonus, it.crit_bonus) AS snapshot_crit_bonus,
                   COALESCE(ii.sell_price, ml.snapshot_sell_price, it.sell_price) AS snapshot_sell_price,
                   COALESCE(ml.snapshot_description, it.description) AS snapshot_description,
                   COALESCE(ml.snapshot_effect_type, it.effect_type) AS snapshot_effect_type,
                   COALESCE(ml.snapshot_effect_value, it.effect_value_json) AS snapshot_effect_value,
                   COALESCE(ii.enhancement_level, ml.item_enhancement_level) AS snapshot_enhancement_level,
                   COALESCE(ii.enhancement_luck, ml.item_enhancement_luck) AS snapshot_enhancement_luck,
                   COALESCE(ii.refine_level, ml.item_refine_level) AS snapshot_refine_level,
                   COALESCE(ii.ascension_level, ml.item_ascension_level) AS snapshot_ascension_level,
                   COALESCE(ml.snapshot_processing_summary, '') AS snapshot_processing_summary,
                   CASE WHEN ml.unit_price > 0 THEN ml.unit_price ELSE ml.price END AS unit_price,
                   ml.price, ml.status,
                   COALESCE(seller.controller_type, 'player') AS seller_kind
            FROM market_listing ml
            LEFT JOIN player seller ON seller.id = ml.seller_player_id
            LEFT JOIN item_instance ii ON ii.id = ml.item_id
            JOIN item_template it ON it.id = COALESCE(ii.template_id, ml.item_template_id)
            WHERE ml.status = 'listed'
            ORDER BY CASE
                         WHEN COALESCE(seller.controller_type, 'player') = 'robot' THEN 1
                         ELSE 0
                     END,
                     ml.created_at DESC,
                     ml.id DESC
            LIMIT 260
            """,
            (rs, rowNum) -> listingRow(rs)
        );
        List<ListingRow> candidates = rows.stream()
            .filter(row -> row.unitPrice() <= recommendedPrice(row.item()) * 240 / 100)
            .filter(this::robotMarketDesirable)
            .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        ListingRow listing;
        RobotSeller buyer;
        if (preferredBuyerId != null) {
            buyer = robotSeller(preferredBuyerId);
            if (buyer == null) {
                return false;
            }
            List<ListingRow> preferredCandidates = candidates.stream()
                .filter(row -> row.sellerPlayerId() == null || !row.sellerPlayerId().equals(preferredBuyerId))
                .filter(row -> acceptableForRobot(row, buyer))
                .toList();
            if (preferredCandidates.isEmpty()) {
                return false;
            }
            listing = preferredCandidates.get(random.nextInt(preferredCandidates.size()));
        } else {
            listing = candidates.get(random.nextInt(candidates.size()));
            List<RobotSeller> buyers = robotSellers(32, random).stream()
                .filter(robot -> listing.sellerPlayerId() == null || robot.id() != listing.sellerPlayerId())
                .filter(robot -> acceptableForRobot(listing, robot))
                .toList();
            if (buyers.isEmpty()) {
                return false;
            }
            List<RobotSeller> fundedBuyers = buyers.stream()
                .filter(robot -> robot.gold() >= listing.price())
                .toList();
            buyer = fundedBuyers.isEmpty()
                ? buyers.stream()
                    .sorted(Comparator.comparingLong(RobotSeller::realMoney).thenComparingInt(RobotSeller::wealthTierLevel).reversed())
                    .findFirst()
                    .orElse(null)
                : fundedBuyers.get(random.nextInt(fundedBuyers.size()));
        }
        if (buyer == null) {
            return false;
        }
        RechargeService.RechargeResult recharge = null;
        if (buyer.gold() < listing.price()) {
            recharge = rechargeService.rechargeForGoldNeed(
                    buyer.id(),
                    listing.price(),
                    "robot_market_buy",
                    "购买【" + listing.item().name() + "】"
                )
                .orElse(null);
            if (recharge == null || recharge.player().gold() < listing.price()) {
                return false;
            }
        }
        int sellerGold = netSellerProceeds(listing.price());
        jdbcTemplate.update("UPDATE player SET gold = gold - ? WHERE id = ?", listing.price(), buyer.id());
        if (listing.sellerPlayerId() != null) {
            jdbcTemplate.update("UPDATE player SET gold = gold + ? WHERE id = ?", sellerGold, listing.sellerPlayerId());
            if ("robot".equals(listing.sellerKind())) {
                robotActivityLogService.record(listing.sellerPlayerId(), "market_sell", "寄售的【" + listing.item().name() + "】被 " + buyer.name() + " 买走，入账 " + sellerGold + " 金。");
            } else {
                questService.recordEvent(listing.sellerPlayerId(), QuestEvent.of("marketSold"));
            }
        }
        PlayerRecord buyerPlayer = requireRobotPlayer(buyer.id());
        if (listing.itemId() > 0 && itemExists(listing.itemId())) {
            inventoryService.transferItemOwner(listing.itemId(), buyer.id());
            inventoryService.addExistingItemToInventory(buyer.id(), listing.itemId());
            ItemRecord item = inventoryService.requireItem(listing.itemId());
            if (item.equipment()) {
                robotEquipmentService.resolveDropIfUpgrade(buyerPlayer, item, random);
            }
        } else {
            ItemRecord item = createItemFromSnapshot(buyer.id(), listing.item());
            if (item.equipment()) {
                robotEquipmentService.resolveDropIfUpgrade(buyerPlayer, item, random);
            }
        }
        jdbcTemplate.update(
            MARK_LISTING_SOLD_SQL,
            buyer.id(),
            listing.id()
        );
        String rechargeText = recharge == null ? "" : "，先花 " + recharge.rmbAmount() + " 元换了 " + recharge.goldAmount() + " 金";
        robotActivityLogService.record(
            buyer.id(),
            "market_buy",
            "在商会买下【" + listing.item().name() + "】" + rechargeText + "，花费 " + listing.price() + " 金。" + decisionSuffix(decisionReason)
        );
        return true;
    }

    private boolean acceptableForRobot(ListingRow listing, RobotSeller robot) {
        int recommended = recommendedPrice(listing.item());
        int tolerance = 100 + Math.min(140, robot.wealthTierLevel() * 9 + qualityRank(listing.item().quality()) * 8);
        return listing.unitPrice() <= recommended * tolerance / 100;
    }

    private boolean robotMarketDesirable(ListingRow listing) {
        ItemSnapshot item = listing.item();
        if (item.equipment()) {
            return qualityRank(item.quality()) >= 2;
        }
        if ("gem".equals(item.marketCategory())) {
            return qualityRank(item.quality()) >= 2;
        }
        if ("consumable".equals(item.marketCategory())) {
            return "restoreStamina".equals(item.effectType()) || "grantGold".equals(item.effectType());
        }
        if ("material".equals(item.marketCategory())) {
            return qualityRank(item.quality()) >= 2 || item.effectValueJson().contains("ascension") || item.effectValueJson().contains("reforge") || item.effectValueJson().contains("socket");
        }
        return "chest".equals(item.marketCategory()) && qualityRank(item.quality()) >= 3;
    }

    private boolean shouldRobotListItem(ItemRecord item, Random random) {
        ItemTemplate template = gameConfigService.requireItem(item.templateId());
        if (!template.tradeable()) {
            return false;
        }
        if (item.equipment()) {
            return true;
        }
        int reserve = switch (template.marketCategory()) {
            case "material" -> qualityRank(item.quality()) >= 4 ? 12 : 24;
            case "gem" -> 2;
            case "consumable" -> 4;
            case "chest" -> 1;
            default -> 6;
        };
        if (item.stackable() && item.quantity() <= reserve) {
            return false;
        }
        int chance = switch (template.marketCategory()) {
            case "gem" -> 52;
            case "material" -> 38 + qualityRank(item.quality()) * 4;
            case "consumable" -> 32;
            case "chest" -> 45;
            default -> 22;
        };
        return random.nextInt(100) < chance;
    }

    private List<RobotSeller> robotSellers(int limit, Random random) {
        return jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, real_money, wealth_tier_level,
                   strength, agility, constitution, intelligence, spirit, free_points
            FROM player
            WHERE controller_type = 'robot'
            ORDER BY RAND()
            LIMIT ?
            """,
            (rs, rowNum) -> {
                PlayerRecord robot = mapPlayer(rs);
                return new RobotSeller(
                    robot.id(),
                    robot.name(),
                    rs.getString("title"),
                    robot.profession(),
                    robot.level(),
                    inventoryService.combatPower(robot),
                    robot.gold(),
                    rs.getLong("real_money"),
                    rs.getInt("wealth_tier_level")
                );
            },
            limit
        );
    }

    private RobotSeller robotSeller(long robotId) {
        return jdbcTemplate.query(
            """
            SELECT id, account_id, name, title, profession, level, experience, gold, real_money, wealth_tier_level,
                   strength, agility, constitution, intelligence, spirit, free_points
            FROM player
            WHERE id = ?
              AND controller_type = 'robot'
            """,
            (rs, rowNum) -> {
                PlayerRecord robot = mapPlayer(rs);
                return new RobotSeller(
                    robot.id(),
                    robot.name(),
                    rs.getString("title"),
                    robot.profession(),
                    robot.level(),
                    inventoryService.combatPower(robot),
                    robot.gold(),
                    rs.getLong("real_money"),
                    rs.getInt("wealth_tier_level")
                );
            },
            robotId
        ).stream().findFirst().orElse(null);
    }

    public MarketListingView listRobotOwnedItem(
        PlayerRecord robot,
        String title,
        ItemRecord item,
        String origin,
        Random random,
        boolean recordActivity
    ) {
        int targetQuantity = item.stackable() ? Math.max(1, Math.min(item.quantity(), 1 + random.nextInt(Math.min(20, Math.max(1, item.quantity()))))) : 1;
        ItemRecord listedItem = inventoryService.splitItemForMarket(robot.id(), item.id(), targetQuantity);
        ItemTemplate template = gameConfigService.requireItem(listedItem.templateId());
        ItemSnapshot snapshot = ItemSnapshot.from(listedItem, template, origin);
        int quantity = Math.max(1, listedItem.quantity());
        int recommended = recommendedPrice(snapshot);
        int unitPrice = Math.max(snapshot.sellPrice(), recommended * (72 + random.nextInt(76)) / 100);
        int price = Math.toIntExact((long) unitPrice * quantity);
        jdbcTemplate.update(
            """
            INSERT INTO market_listing
            (seller_player_id, seller_name, item_id, item_template_id, quantity, unit_price, item_category, stackable,
             item_enhancement_level, item_enhancement_luck, item_refine_level, item_ascension_level,
             snapshot_name, snapshot_item_type, snapshot_item_category, snapshot_quality, snapshot_required_level, snapshot_attack_bonus,
             snapshot_defense_bonus, snapshot_resistance_bonus, snapshot_hp_bonus, snapshot_mp_bonus,
             snapshot_crit_bonus, snapshot_sell_price, snapshot_description, snapshot_effect_type, snapshot_effect_value,
             snapshot_processing_summary, price, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'listed')
            """,
            robot.id(),
            robot.name(),
            listedItem.id(),
            listedItem.templateId(),
            quantity,
            unitPrice,
            snapshot.marketCategory(),
            snapshot.stackable(),
            listedItem.enhancementLevel(),
            listedItem.enhancementLuck(),
            listedItem.refineLevel(),
            listedItem.ascensionLevel(),
            snapshot.name(),
            snapshot.itemType(),
            snapshot.itemCategory(),
            snapshot.quality(),
            snapshot.requiredLevel(),
            snapshot.attackBonus(),
            snapshot.defenseBonus(),
            snapshot.resistanceBonus(),
            snapshot.hpBonus(),
            snapshot.mpBonus(),
            snapshot.critBonus(),
            snapshot.sellPrice(),
            snapshot.description(),
            snapshot.effectType(),
            snapshot.effectValueJson(),
            snapshot.processingSummary(),
            price
        );
        long listingId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (recordActivity) {
            robotActivityLogService.record(
                robot.id(),
                "market_list",
                "寄售【" + snapshot.name() + quantitySuffix(quantity) + "】，挂到商会 " + price + " 金。"
            );
        }
        return view(listingId, false, robot.name(), listedItem.id(), snapshot, quantity, unitPrice, price, "listed");
    }

    private PlayerRecord requireRobotPlayer(long robotId) {
        return jdbcTemplate.query(
            """
            SELECT id, account_id, name, profession, level, experience, gold, strength, agility,
                   constitution, intelligence, spirit, free_points
            FROM player
            WHERE id = ?
              AND controller_type = 'robot'
            """,
            (rs, rowNum) -> mapPlayer(rs),
            robotId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("机器人不存在"));
    }

    private ItemRecord createItemFromSnapshot(long playerId, ItemSnapshot item) {
        ItemTemplate template = gameConfigService.requireItem(item.templateId());
        if (!template.equipment()) {
            return inventoryService.grantItem(playerId, item.templateId(), Math.max(1, item.quantity()), new Random(Objects.hash(playerId, item.templateId(), System.nanoTime()))).getFirst();
        }
        return inventoryService.addMarketItemToInventory(
            playerId,
            item.templateId(),
            item.name(),
            item.itemType(),
            item.quality(),
            item.requiredLevel(),
            item.attackBonus(),
            item.defenseBonus(),
            item.resistanceBonus(),
            item.hpBonus(),
            item.mpBonus(),
            item.critBonus(),
            item.sellPrice(),
            item.enhancementLevel(),
            item.enhancementLuck()
        );
    }

    private boolean itemExists(long itemId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM item_instance WHERE id = ?",
            Integer.class,
            itemId
        );
        return count != null && count > 0;
    }

    private PlayerRecord mapPlayer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlayerRecord(
            rs.getLong("id"),
            rs.getObject("account_id") == null ? 0 : rs.getLong("account_id"),
            rs.getString("name"),
            rs.getString("profession"),
            rs.getInt("level"),
            rs.getInt("experience"),
            rs.getLong("gold"),
            rs.getInt("strength"),
            rs.getInt("agility"),
            rs.getInt("constitution"),
            rs.getInt("intelligence"),
            rs.getInt("spirit"),
            rs.getInt("free_points")
        );
    }

    private List<MarketActivity> marketActivities(List<MarketListingView> listings) {
        List<RobotActivityLogService.RobotActivityEvent> marketEvents = robotActivityLogService.latestEvents(
            24,
            List.of("market_list", "market_buy", "market_sell", "market_watch")
        );
        if (!marketEvents.isEmpty()) {
            return marketEvents.stream()
                .map(event -> new MarketActivity(
                    event.actorName(),
                    event.actorTitle(),
                    event.text(),
                    event.kind(),
                    event.createdAt()
                ))
                .toList();
        }
        List<MarketActivity> activities = new ArrayList<>();
        List<RobotSeller> robots = robotSellers(24, new Random());
        Random random = new Random(System.currentTimeMillis() / 120_000);
        for (int i = 0; i < Math.min(18, Math.max(8, listings.size())); i++) {
            MarketListingView listing = listings.get(i % listings.size());
            RobotSeller actor = robots.isEmpty() ? new RobotSeller(0, "银冠书记", "商会书记", "warrior", 1, 1, 0) : robots.get(random.nextInt(robots.size()));
            String text = switch (i % 5) {
                case 0 -> "正在压价询问 " + qualityName(listing.item().quality()) + " " + marketItemKindName(listing.item()) + "。";
                case 1 -> "刚把 " + listing.item().name() + " 加入关注清单。";
                case 2 -> "提醒商会：高于估值 3 倍的寄售不会进入机器人收购池。";
                case 3 -> "与另一名冒险者完成了一笔成长物资换金交易。";
                default -> "在看板前比较 " + listing.price() + " 金与商会估值 " + listing.recommendedPrice() + " 金。";
            };
            activities.add(new MarketActivity(
                actor.name(),
                actor.title(),
                text,
                i % 3 == 0 ? "trade" : i % 3 == 1 ? "watch" : "rule",
                Instant.now().minusSeconds((2L + i * 4L) * 60)
            ));
        }
        return activities;
    }

    private List<PlayerMarketSale> playerSales(long playerId) {
        return jdbcTemplate.query(
            """
            SELECT ml.id, ml.item_id,
                   COALESCE(ii.template_id, ml.item_template_id) AS snapshot_template_id,
                   COALESCE(ii.name, ml.snapshot_name, it.name) AS snapshot_name,
                   COALESCE(ii.item_type, ml.snapshot_item_type, it.item_type) AS snapshot_item_type,
                   ml.snapshot_item_category AS snapshot_item_category,
                   ml.item_category AS market_category,
                   COALESCE(ml.stackable, it.stackable) AS stackable,
                   GREATEST(1, COALESCE(ml.quantity, ii.quantity, 1)) AS quantity,
                   COALESCE(ii.quality, ml.snapshot_quality, it.quality) AS snapshot_quality,
                   COALESCE(ii.required_level, ml.snapshot_required_level, it.required_level) AS snapshot_required_level,
                   COALESCE(ii.attack_bonus, ml.snapshot_attack_bonus, it.attack_bonus) AS snapshot_attack_bonus,
                   COALESCE(ii.defense_bonus, ml.snapshot_defense_bonus, it.defense_bonus) AS snapshot_defense_bonus,
                   COALESCE(ii.resistance_bonus, ml.snapshot_resistance_bonus, it.resistance_bonus) AS snapshot_resistance_bonus,
                   COALESCE(ii.hp_bonus, ml.snapshot_hp_bonus, it.hp_bonus) AS snapshot_hp_bonus,
                   COALESCE(ii.mp_bonus, ml.snapshot_mp_bonus, it.mp_bonus) AS snapshot_mp_bonus,
                   COALESCE(ii.crit_bonus, ml.snapshot_crit_bonus, it.crit_bonus) AS snapshot_crit_bonus,
                   COALESCE(ii.sell_price, ml.snapshot_sell_price, it.sell_price) AS snapshot_sell_price,
                   COALESCE(ml.snapshot_description, it.description) AS snapshot_description,
                   COALESCE(ml.snapshot_effect_type, it.effect_type) AS snapshot_effect_type,
                   COALESCE(ml.snapshot_effect_value, it.effect_value_json) AS snapshot_effect_value,
                   COALESCE(ii.enhancement_level, ml.item_enhancement_level) AS snapshot_enhancement_level,
                   COALESCE(ii.enhancement_luck, ml.item_enhancement_luck) AS snapshot_enhancement_luck,
                   COALESCE(ii.refine_level, ml.item_refine_level) AS snapshot_refine_level,
                   COALESCE(ii.ascension_level, ml.item_ascension_level) AS snapshot_ascension_level,
                   COALESCE(ml.snapshot_processing_summary, '') AS snapshot_processing_summary,
                   CASE WHEN ml.unit_price > 0 THEN ml.unit_price ELSE ml.price END AS unit_price,
                   ml.price,
                   ml.sold_at,
                   COALESCE(buyer_player.name, '匿名买家') AS buyer_name
            FROM market_listing ml
            LEFT JOIN player buyer_player ON buyer_player.id = ml.buyer_player_id
            LEFT JOIN item_instance ii ON ii.id = ml.item_id
            JOIN item_template it ON it.id = COALESCE(ii.template_id, ml.item_template_id)
            WHERE ml.seller_player_id = ?
              AND ml.status = 'sold'
            ORDER BY ml.sold_at DESC, ml.id DESC
            LIMIT 12
            """,
            (rs, rowNum) -> {
                int price = rs.getInt("price");
                var soldAt = rs.getTimestamp("sold_at");
                return new PlayerMarketSale(
                    rs.getLong("id"),
                    snapshotFromRow(rs),
                    price,
                    netSellerProceeds(price),
                    rs.getString("buyer_name"),
                    soldAt == null ? Instant.now() : soldAt.toInstant()
                );
            },
            playerId
        );
    }

    private int recommendedPrice(ItemSnapshot item) {
        if (!item.equipment()) {
            int rank = qualityRank(item.quality());
            int categoryMultiplier = switch (item.marketCategory()) {
                case "gem" -> 18 + rank * 5;
                case "material" -> 10 + rank * 3;
                case "consumable" -> 9 + rank * 2;
                case "chest" -> 16 + rank * 4;
                default -> 8 + rank * 2;
            };
            int effectPremium = (item.effectType() == null || item.effectType().isBlank()) ? 0 : Math.max(10, item.sellPrice() * rank);
            return Math.max(5, item.sellPrice() * categoryMultiplier / 2 + effectPremium + item.requiredLevel() * Math.max(1, rank));
        }
        int statScore = item.attackBonus() * 16
            + item.defenseBonus() * 12
            + item.resistanceBonus() * 10
            + item.hpBonus() / 2
            + item.mpBonus() / 2
            + (int) Math.round(item.critBonus() * 1200)
            + item.enhancementLevel() * 80
            + item.refineLevel() * 140
            + item.ascensionLevel() * 260;
        int qualityBonus = qualityRank(item.quality()) * qualityRank(item.quality()) * 55;
        return Math.max(30, item.sellPrice() * 8 + statScore * 2 + item.requiredLevel() * 35 + qualityBonus);
    }

    private int dealChance(int price, int recommendedPrice, String quality, boolean playerListing) {
        double ratio = price / (double) Math.max(1, recommendedPrice);
        int chance;
        if (ratio <= 0.75) {
            chance = 88;
        } else if (ratio <= 1.05) {
            chance = 68;
        } else if (ratio <= 1.4) {
            chance = 43;
        } else if (ratio <= 2.0) {
            chance = 22;
        } else {
            chance = 7;
        }
        chance += qualityRank(quality) * 2;
        if (!playerListing) {
            chance += 6;
        }
        return Math.max(3, Math.min(94, chance));
    }

    private String marketTag(int priceRatio, int dealChance) {
        if (priceRatio <= 85) {
            return "低价抢手";
        }
        if (priceRatio <= 115) {
            return "公允价";
        }
        if (dealChance <= 15) {
            return "高价观望";
        }
        return "溢价流通";
    }

    private String decisionSuffix(String decisionReason) {
        if (decisionReason == null || decisionReason.isBlank()) {
            return "";
        }
        return "（决策：" + decisionReason.trim() + "）";
    }

    private ListingRow requireListing(long listingId) {
        return jdbcTemplate.query(
            """
            SELECT ml.id, ml.seller_player_id, ml.seller_name, ml.item_id,
                   COALESCE(ii.template_id, ml.item_template_id) AS snapshot_template_id,
                   COALESCE(ii.name, ml.snapshot_name, it.name) AS snapshot_name,
                   COALESCE(ii.item_type, ml.snapshot_item_type, it.item_type) AS snapshot_item_type,
                   ml.snapshot_item_category AS snapshot_item_category,
                   ml.item_category AS market_category,
                   COALESCE(ml.stackable, it.stackable) AS stackable,
                   GREATEST(1, COALESCE(ml.quantity, ii.quantity, 1)) AS quantity,
                   COALESCE(ii.quality, ml.snapshot_quality, it.quality) AS snapshot_quality,
                   COALESCE(ii.required_level, ml.snapshot_required_level, it.required_level) AS snapshot_required_level,
                   COALESCE(ii.attack_bonus, ml.snapshot_attack_bonus, it.attack_bonus) AS snapshot_attack_bonus,
                   COALESCE(ii.defense_bonus, ml.snapshot_defense_bonus, it.defense_bonus) AS snapshot_defense_bonus,
                   COALESCE(ii.resistance_bonus, ml.snapshot_resistance_bonus, it.resistance_bonus) AS snapshot_resistance_bonus,
                   COALESCE(ii.hp_bonus, ml.snapshot_hp_bonus, it.hp_bonus) AS snapshot_hp_bonus,
                   COALESCE(ii.mp_bonus, ml.snapshot_mp_bonus, it.mp_bonus) AS snapshot_mp_bonus,
                   COALESCE(ii.crit_bonus, ml.snapshot_crit_bonus, it.crit_bonus) AS snapshot_crit_bonus,
                   COALESCE(ii.sell_price, ml.snapshot_sell_price, it.sell_price) AS snapshot_sell_price,
                   COALESCE(ml.snapshot_description, it.description) AS snapshot_description,
                   COALESCE(ml.snapshot_effect_type, it.effect_type) AS snapshot_effect_type,
                   COALESCE(ml.snapshot_effect_value, it.effect_value_json) AS snapshot_effect_value,
                   COALESCE(ii.enhancement_level, ml.item_enhancement_level) AS snapshot_enhancement_level,
                   COALESCE(ii.enhancement_luck, ml.item_enhancement_luck) AS snapshot_enhancement_luck,
                   COALESCE(ii.refine_level, ml.item_refine_level) AS snapshot_refine_level,
                   COALESCE(ii.ascension_level, ml.item_ascension_level) AS snapshot_ascension_level,
                   COALESCE(ml.snapshot_processing_summary, '') AS snapshot_processing_summary,
                   CASE WHEN ml.unit_price > 0 THEN ml.unit_price ELSE ml.price END AS unit_price,
                   ml.price, ml.status,
                   COALESCE(seller.controller_type, 'player') AS seller_kind
            FROM market_listing ml
            LEFT JOIN player seller ON seller.id = ml.seller_player_id
            LEFT JOIN item_instance ii ON ii.id = ml.item_id
            JOIN item_template it ON it.id = COALESCE(ii.template_id, ml.item_template_id)
            WHERE ml.id = ?
            """,
            (rs, rowNum) -> listingRow(rs),
            listingId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("寄售不存在"));
    }

    private String originFor(String templateId) {
        return originCache.computeIfAbsent(templateId, this::resolveOriginFor);
    }

    private String resolveOriginFor(String templateId) {
        for (var dungeon : gameConfigService.dungeons()) {
            for (var room : dungeon.rooms()) {
                for (var roomMonster : room.monsters()) {
                    MonsterConfig monster = gameConfigService.requireMonster(roomMonster.monsterId());
                    if (monster.lootTable() == null) {
                        continue;
                    }
                    boolean found = monster.lootTable().stream().anyMatch(loot -> loot.itemId().equals(templateId));
                    if (found) {
                        return "副本掉落 · " + dungeon.name();
                    }
                }
            }
        }
        return "冒险者商会流通";
    }

    private ItemSnapshot snapshotFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String templateId = rs.getString("snapshot_template_id");
        ItemTemplate template = gameConfigService.requireItem(templateId);
        int requiredLevel = rs.getInt("snapshot_required_level");
        int sellPrice = rs.getInt("snapshot_sell_price");
        return new ItemSnapshot(
            templateId,
            cleanName(rs.getString("snapshot_name")),
            rs.getString("snapshot_item_type"),
            rs.getString("snapshot_item_category"),
            rs.getString("market_category"),
            rs.getString("snapshot_quality"),
            requiredLevel > 0 ? requiredLevel : template.requiredLevel(),
            rs.getInt("snapshot_attack_bonus"),
            rs.getInt("snapshot_defense_bonus"),
            rs.getInt("snapshot_resistance_bonus"),
            rs.getInt("snapshot_hp_bonus"),
            rs.getInt("snapshot_mp_bonus"),
            rs.getBigDecimal("snapshot_crit_bonus").doubleValue(),
            sellPrice > 0 ? sellPrice : template.sellPrice(),
            rs.getInt("snapshot_enhancement_level"),
            rs.getInt("snapshot_enhancement_luck"),
            rs.getInt("snapshot_refine_level"),
            rs.getInt("snapshot_ascension_level"),
            Math.max(1, rs.getInt("quantity")),
            rs.getBoolean("stackable"),
            rs.getString("snapshot_description") == null ? template.description() : rs.getString("snapshot_description"),
            rs.getString("snapshot_effect_type") == null ? "" : rs.getString("snapshot_effect_type"),
            rs.getString("snapshot_effect_value") == null ? "" : rs.getString("snapshot_effect_value"),
            rs.getString("snapshot_processing_summary") == null ? "" : rs.getString("snapshot_processing_summary"),
            originFor(templateId)
        );
    }

    private int qualityRank(String quality) {
        return switch (quality) {
            case "immortal" -> 6;
            case "legendary" -> 5;
            case "epic" -> 4;
            case "rare" -> 3;
            case "uncommon" -> 2;
            default -> 1;
        };
    }

    private String cleanName(String name) {
        if (name == null) {
            return "";
        }
        return name
            .replace("旧制旧制", "旧制")
            .replace("精制精制", "精制")
            .replace("秘纹秘纹", "秘纹")
            .replace("史诗史诗", "史诗")
            .replace("传说传说", "传说")
            .replace("不朽不朽", "不朽");
    }

    private String qualityName(String quality) {
        return switch (quality) {
            case "immortal" -> "不朽";
            case "legendary" -> "传说";
            case "epic" -> "史诗";
            case "rare" -> "稀有";
            case "uncommon" -> "优秀";
            default -> "普通";
        };
    }

    private boolean isLegendaryOrBetter(String quality) {
        return "legendary".equals(quality) || "immortal".equals(quality);
    }

    private String typeName(String itemType) {
        return switch (itemType) {
            case "weapon" -> "武器";
            case "helmet" -> "头盔";
            case "armor" -> "护甲";
            case "legs" -> "护腿";
            case "boots" -> "靴子";
            case "gloves" -> "护手";
            case "necklace" -> "项链";
            case "ring" -> "戒指";
            default -> itemType;
        };
    }

    private String marketItemKindName(ItemSnapshot item) {
        return switch (item.marketCategory()) {
            case "equipment" -> typeName(item.itemType());
            case "gem" -> "宝石";
            case "material" -> "材料";
            case "consumable" -> "消耗品";
            case "chest" -> "宝箱";
            default -> item.marketCategory();
        };
    }

    private static String processingSummary(ItemRecord item) {
        List<String> parts = new ArrayList<>();
        if (item.enhancementLevel() > 0) {
            parts.add("强化+" + item.enhancementLevel());
        }
        if (item.refineLevel() > 0) {
            parts.add("淬" + item.refineLevel());
        }
        if (item.ascensionLevel() > 0) {
            parts.add("阶" + item.ascensionLevel());
        }
        long sockets = item.sockets() == null ? 0 : item.sockets().stream().filter(ItemRecord.EquipmentSocketView::unlocked).count();
        if (sockets > 0) {
            parts.add(sockets + "孔");
        }
        long affixes = item.affixes() == null ? 0 : item.affixes().stream().filter(affix -> affix.statKey() != null && !affix.statKey().isBlank()).count();
        if (affixes > 0) {
            parts.add(affixes + "词条");
        }
        return String.join(" · ", parts);
    }

    private String quantitySuffix(int quantity) {
        return quantity > 1 ? " x" + quantity : "";
    }

    private record ListingRow(
        long id,
        Long sellerPlayerId,
        String sellerKind,
        String sellerName,
        long itemId,
        ItemSnapshot item,
        int quantity,
        int unitPrice,
        int price,
        String status
    ) {
    }

    private ListingRow listingRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ListingRow(
            rs.getLong("id"),
            rs.getObject("seller_player_id") == null ? null : rs.getLong("seller_player_id"),
            rs.getString("seller_kind"),
            rs.getString("seller_name"),
            rs.getObject("item_id") == null ? 0 : rs.getLong("item_id"),
            snapshotFromRow(rs),
            rs.getInt("quantity"),
            rs.getInt("unit_price"),
            rs.getInt("price"),
            rs.getString("status")
        );
    }

    private record RobotSeller(long id, String name, String title, String profession, int level, int power, long gold, long realMoney, int wealthTierLevel) {
        private RobotSeller(long id, String name, String title, String profession, int level, int power, long gold) {
            this(id, name, title, profession, level, power, gold, 0, 0);
        }
    }

    public record RobotLootOutcome(int listedCount, int equippedCount) {
    }

    public record ItemSnapshot(
        String templateId,
        String name,
        String itemType,
        String itemCategory,
        String marketCategory,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int resistanceBonus,
        int hpBonus,
        int mpBonus,
        double critBonus,
        int sellPrice,
        int enhancementLevel,
        int enhancementLuck,
        int refineLevel,
        int ascensionLevel,
        int quantity,
        boolean stackable,
        String description,
        String effectType,
        String effectValueJson,
        String processingSummary,
        String origin
    ) {
        boolean equipment() {
            return "equipment".equals(itemCategory);
        }

        static ItemSnapshot from(ItemRecord item, ItemTemplate template, String origin) {
            return new ItemSnapshot(
                item.templateId(),
                item.name(),
                item.itemType(),
                item.itemCategory(),
                template.marketCategory(),
                item.quality(),
                item.requiredLevel(),
                item.attackBonus(),
                item.defenseBonus(),
                item.resistanceBonus(),
                item.hpBonus(),
                item.mpBonus(),
                item.critBonus().doubleValue(),
                item.sellPrice(),
                item.enhancementLevel(),
                item.enhancementLuck(),
                item.refineLevel(),
                item.ascensionLevel(),
                Math.max(1, item.quantity()),
                item.stackable(),
                template.description(),
                item.effectType() == null ? "" : item.effectType(),
                item.effectValueJson() == null ? "" : item.effectValueJson(),
                MarketService.processingSummary(item),
                origin
            );
        }
    }

    public record MarketSnapshot(
        List<MarketListingView> listings,
        List<MarketActivity> activities,
        List<PlayerMarketSale> playerSales,
        MarketRules rules,
        int onlineTraders,
        int robotListings,
        int playerListings,
        int soldRecently,
        int averagePrice
    ) {
    }

    public record MarketRules(int taxRate, int priceCapMultiplier, int refreshMinutes, String antiExploit) {
    }

    public record MarketActivity(String actorName, String actorTitle, String text, String kind, Instant createdAt) {
    }

    public record PlayerMarketSale(long id, ItemSnapshot item, int price, int netGold, String buyerName, Instant soldAt) {
    }

    public record MarketListingView(
        long id,
        boolean playerListing,
        String sellerName,
        long itemId,
        ItemSnapshot item,
        int quantity,
        int unitPrice,
        int price,
        int recommendedPrice,
        int priceRatio,
        int dealChance,
        String status,
        String sellerType,
        String marketTag,
        Instant listedAt
    ) {
    }
}
