package com.mythicrealm.api.gameplay.shop;

import com.mythicrealm.api.gameplay.common.ApiException;
import com.mythicrealm.api.gameplay.inventory.InventoryService;
import com.mythicrealm.api.gameplay.inventory.ItemRecord;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.recharge.RechargeService;
import java.util.List;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {
    private static final int MAX_PURCHASE_QUANTITY = 99;

    private final JdbcTemplate jdbcTemplate;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final RechargeService rechargeService;

    public ShopService(
        JdbcTemplate jdbcTemplate,
        PlayerService playerService,
        InventoryService inventoryService,
        RechargeService rechargeService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.rechargeService = rechargeService;
    }

    @Transactional(readOnly = true)
    public ShopSnapshot snapshot(PlayerRecord viewer) {
        PlayerRecord player = playerService.requireById(viewer.id());
        return new ShopSnapshot(
            rechargeService.wallet(player),
            offers(player),
            List.of("all", "gold", "stamina", "enhancement", "chest", "growth")
        );
    }

    @Transactional
    public ShopPurchaseResult purchase(PlayerRecord viewer, String offerId, int quantity) {
        int count = normalizeQuantity(quantity);
        PlayerRecord player = playerService.requireById(viewer.id());
        ShopOfferRow offer = requireOffer(offerId);
        if (player.level() < offer.requiredLevel()) {
            throw ApiException.badRequest("角色等级不足，需要 Lv." + offer.requiredLevel());
        }
        long totalPrice = safeMultiply(offer.priceRmb(), count);
        if (player.realMoney() < totalPrice) {
            throw ApiException.badRequest("余额不足，当前只有 " + player.realMoney() + " 元");
        }
        long goldGained = safeMultiply(offer.goldAmount(), count);
        int itemQuantity = safeIntMultiply(offer.itemQuantity(), count);
        if (goldGained <= 0 && (offer.itemTemplateId() == null || itemQuantity <= 0)) {
            throw ApiException.badRequest("商品配置无有效奖励");
        }

        int updated = jdbcTemplate.update(
            """
            UPDATE player
            SET real_money = real_money - ?, gold = gold + ?
            WHERE id = ? AND real_money >= ?
            """,
            totalPrice,
            goldGained,
            player.id(),
            totalPrice
        );
        if (updated == 0) {
            throw ApiException.badRequest("余额不足");
        }

        List<ItemRecord> rewards = List.of();
        if (offer.itemTemplateId() != null && itemQuantity > 0) {
            rewards = inventoryService.grantItem(
                player.id(),
                offer.itemTemplateId(),
                itemQuantity,
                new Random(System.nanoTime() + offer.id().hashCode())
            );
        }
        jdbcTemplate.update(
            """
            INSERT INTO shop_purchase_log
            (player_id, player_name, offer_id, offer_name, category, price_rmb, quantity,
             total_price_rmb, item_template_id, item_quantity, gold_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            player.id(),
            player.name(),
            offer.id(),
            offer.name(),
            offer.category(),
            offer.priceRmb(),
            count,
            totalPrice,
            offer.itemTemplateId(),
            itemQuantity,
            goldGained
        );
        PlayerRecord updatedPlayer = playerService.requireById(player.id());
        return new ShopPurchaseResult(
            offerView(offer, updatedPlayer),
            count,
            totalPrice,
            goldGained,
            rewards,
            rechargeService.wallet(updatedPlayer),
            inventoryService.snapshot(updatedPlayer),
            updatedPlayer
        );
    }

    private List<ShopOffer> offers(PlayerRecord player) {
        return jdbcTemplate.query(
            """
            SELECT so.id, so.name, so.description, so.category, so.price_rmb, so.item_template_id,
                   so.item_quantity, so.gold_amount, so.required_level, so.sort_order,
                   it.name AS item_name, it.item_type, it.item_category, it.quality
            FROM shop_offer so
            LEFT JOIN item_template it ON it.id = so.item_template_id
            WHERE so.enabled = TRUE
            ORDER BY so.sort_order, so.id
            """,
            (rs, rowNum) -> offerView(mapOfferRow(rs), player)
        );
    }

    private ShopOfferRow requireOffer(String offerId) {
        return jdbcTemplate.query(
            """
            SELECT so.id, so.name, so.description, so.category, so.price_rmb, so.item_template_id,
                   so.item_quantity, so.gold_amount, so.required_level, so.sort_order,
                   it.name AS item_name, it.item_type, it.item_category, it.quality
            FROM shop_offer so
            LEFT JOIN item_template it ON it.id = so.item_template_id
            WHERE so.id = ? AND so.enabled = TRUE
            """,
            (rs, rowNum) -> mapOfferRow(rs),
            offerId
        ).stream().findFirst().orElseThrow(() -> ApiException.notFound("商品不存在"));
    }

    private ShopOfferRow mapOfferRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShopOfferRow(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getLong("price_rmb"),
            rs.getString("item_template_id"),
            rs.getInt("item_quantity"),
            rs.getLong("gold_amount"),
            rs.getInt("required_level"),
            rs.getInt("sort_order"),
            rs.getString("item_name"),
            rs.getString("item_type"),
            rs.getString("item_category"),
            rs.getString("quality")
        );
    }

    private ShopOffer offerView(ShopOfferRow row, PlayerRecord player) {
        return new ShopOffer(
            row.id(),
            row.name(),
            row.description(),
            row.category(),
            row.priceRmb(),
            row.itemTemplateId(),
            row.itemName(),
            row.itemType(),
            row.itemCategory(),
            row.quality(),
            row.itemQuantity(),
            row.goldAmount(),
            row.requiredLevel(),
            player.level() >= row.requiredLevel(),
            player.realMoney() >= row.priceRmb(),
            row.sortOrder()
        );
    }

    private int normalizeQuantity(int quantity) {
        if (quantity < 1) {
            throw ApiException.badRequest("购买数量必须大于 0");
        }
        if (quantity > MAX_PURCHASE_QUANTITY) {
            throw ApiException.badRequest("单次最多购买 " + MAX_PURCHASE_QUANTITY + " 份");
        }
        return quantity;
    }

    private long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException error) {
            throw ApiException.badRequest("购买金额过大");
        }
    }

    private int safeIntMultiply(int left, int right) {
        long value = safeMultiply(left, right);
        if (value > Integer.MAX_VALUE) {
            throw ApiException.badRequest("购买数量过大");
        }
        return (int) value;
    }

    private record ShopOfferRow(
        String id,
        String name,
        String description,
        String category,
        long priceRmb,
        String itemTemplateId,
        int itemQuantity,
        long goldAmount,
        int requiredLevel,
        int sortOrder,
        String itemName,
        String itemType,
        String itemCategory,
        String quality
    ) {
    }

    public record ShopSnapshot(
        RechargeService.RechargeWallet wallet,
        List<ShopOffer> offers,
        List<String> categories
    ) {
    }

    public record ShopOffer(
        String id,
        String name,
        String description,
        String category,
        long priceRmb,
        String itemTemplateId,
        String itemName,
        String itemType,
        String itemCategory,
        String quality,
        int itemQuantity,
        long goldAmount,
        int requiredLevel,
        boolean unlocked,
        boolean affordable,
        int sortOrder
    ) {
    }

    public record ShopPurchaseResult(
        ShopOffer offer,
        int quantity,
        long totalPriceRmb,
        long goldGained,
        List<ItemRecord> rewards,
        RechargeService.RechargeWallet wallet,
        InventoryService.InventorySnapshot inventory,
        PlayerRecord player
    ) {
    }
}
