package com.mythicrealm.api.gameplay.shop;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
public class ShopController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final ShopService shopService;

    public ShopController(
        SessionService sessionService,
        PlayerService playerService,
        ShopService shopService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.shopService = shopService;
    }

    @GetMapping
    ShopService.ShopSnapshot snapshot(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return shopService.snapshot(player);
    }

    @PostMapping("/offers/{offerId}/buy")
    ShopService.ShopPurchaseResult buy(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("offerId") String offerId,
        @Valid @RequestBody PurchaseRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return shopService.purchase(player, offerId, request.quantity());
    }

    record PurchaseRequest(@Min(1) @Max(999) int quantity) {
    }
}
