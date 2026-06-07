package com.mythicrealm.backend.market;

import com.mythicrealm.backend.auth.SessionService;
import com.mythicrealm.backend.player.PlayerRecord;
import com.mythicrealm.backend.player.PlayerService;
import com.mythicrealm.backend.quest.QuestService;
import com.mythicrealm.backend.quest.QuestService.QuestEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final MarketService marketService;
    private final QuestService questService;

    public MarketController(
        SessionService sessionService,
        PlayerService playerService,
        MarketService marketService,
        QuestService questService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.marketService = marketService;
        this.questService = questService;
    }

    @GetMapping("/listings")
    MarketService.MarketSnapshot listings(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        questService.recordEvent(player.id(), QuestEvent.of("marketViewed"));
        return marketService.listings(player);
    }

    @PostMapping("/listings")
    MarketService.MarketListingView list(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @Valid @RequestBody ListItemRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var listing = marketService.listItem(player, request.itemId(), request.price());
        questService.recordEvent(player.id(), QuestEvent.of("marketListed"));
        return listing;
    }

    @PostMapping("/listings/{listingId}/buy")
    MarketService.MarketListingView buy(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long listingId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var listing = marketService.buy(player, listingId);
        questService.recordEvent(player.id(), QuestEvent.of("marketPurchased"));
        return listing;
    }

    @PostMapping("/listings/{listingId}/cancel")
    void cancel(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long listingId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        marketService.cancel(player, listingId);
    }

    record ListItemRequest(long itemId, @Min(1) int price) {
    }
}
