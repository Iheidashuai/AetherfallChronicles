package com.mythicrealm.backend.home;

import com.mythicrealm.backend.auth.SessionService;
import com.mythicrealm.backend.gameconfig.GameConfigService;
import com.mythicrealm.backend.inventory.InventoryService;
import com.mythicrealm.backend.inventory.ItemRecord;
import com.mythicrealm.backend.player.PlayerRecord;
import com.mythicrealm.backend.player.PlayerService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class HomeController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final GameConfigService gameConfigService;

    public HomeController(
        SessionService sessionService,
        PlayerService playerService,
        InventoryService inventoryService,
        GameConfigService gameConfigService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.gameConfigService = gameConfigService;
    }

    @GetMapping("/home")
    HomeSnapshot home(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var account = sessionService.require(authorization);
        PlayerRecord player = playerService.requireByAccount(account);
        Map<String, ItemRecord> equipped = inventoryService.equippedItems(player.id());
        List<ItemRecord> inventory = inventoryService.inventoryItems(player.id());
        return new HomeSnapshot(
            player,
            inventoryService.combatPower(player),
            player.maxHp(),
            player.maxMp(),
            equipped,
            inventory.size(),
            InventoryService.MAX_SLOTS,
            gameConfigService.summary()
        );
    }

    public record HomeSnapshot(
        PlayerRecord player,
        int combatPower,
        double maxHp,
        double maxMp,
        Map<String, ItemRecord> equippedItems,
        int inventoryCount,
        int inventoryCapacity,
        GameConfigService.ConfigSummary config
    ) {
    }
}
