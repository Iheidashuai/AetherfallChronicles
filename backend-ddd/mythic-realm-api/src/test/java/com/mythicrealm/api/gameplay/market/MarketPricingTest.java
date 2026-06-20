package com.mythicrealm.api.gameplay.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Money-path: market 8% sale tax and 3x price cap. */
class MarketPricingTest {

    @Test
    void taxTakesEightPercentFromSeller() {
        // 1000 * (100 - 8) / 100 = 920
        assertEquals(920, MarketService.netSellerProceeds(1000));
        assertEquals(92, MarketService.netSellerProceeds(100));
    }

    @Test
    void proceedsAreFlooredAtOne() {
        // a 1-gold sale would tax down to 0, but the floor keeps it at 1
        assertEquals(1, MarketService.netSellerProceeds(1));
        // 10 gold => 10 * 92 / 100 = 9 (integer math), floor not yet engaged
        assertEquals(9, MarketService.netSellerProceeds(10));
    }

    @Test
    void proceedsNeverExceedPrice() {
        for (int price = 1; price <= 100_000; price += 137) {
            int net = MarketService.netSellerProceeds(price);
            assertTrue(net <= price, "net should not exceed price at " + price);
            assertTrue(net >= 1, "net should be at least 1 at " + price);
        }
    }

    @Test
    void priceCapIsThreeTimesRecommended() {
        assertEquals(3000, MarketService.maxListingPrice(1000));
        assertEquals(0, MarketService.maxListingPrice(0));
    }

    @Test
    void historicalListingsDetachFromMovedItems() {
        assertTrue(MarketService.MARK_LISTING_SOLD_SQL.contains("item_id = NULL"));
        assertTrue(MarketService.MARK_LISTING_CANCELED_SQL.contains("item_id = NULL"));
    }

    @Test
    void marketItemForeignKeyAllowsConsumedSoldItems() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("CONSTRAINT fk_market_item FOREIGN KEY (item_id) REFERENCES item_instance (id) ON DELETE SET NULL"));
    }
}
