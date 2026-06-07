package com.mythicrealm.api.gameplay.inventory;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final InventoryService inventoryService;
    private final QuestService questService;

    public InventoryController(
        SessionService sessionService,
        PlayerService playerService,
        InventoryService inventoryService,
        QuestService questService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.inventoryService = inventoryService;
        this.questService = questService;
    }

    @GetMapping
    InventoryService.InventorySnapshot inventory(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, inventoryService.combatPower(player)));
        return inventoryService.snapshot(player);
    }

    @PostMapping("/{itemId}/equip")
    InventoryService.InventorySnapshot equip(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var snapshot = inventoryService.equip(player, itemId);
        questService.recordEvent(player.id(), QuestEvent.of("equipmentEquipped"));
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, snapshot.combatPower()));
        return snapshot;
    }

    @PostMapping("/equip-best")
    InventoryService.InventorySnapshot equipBest(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var snapshot = inventoryService.equipBest(player);
        questService.recordEvent(player.id(), QuestEvent.of("equipmentEquipped"));
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, snapshot.combatPower()));
        return snapshot;
    }

    @PostMapping("/{itemId}/unequip")
    InventoryService.InventorySnapshot unequip(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var snapshot = inventoryService.unequip(player, itemId);
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, snapshot.combatPower()));
        return snapshot;
    }

    @PostMapping("/{itemId}/sell")
    InventoryService.InventorySnapshot sell(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return inventoryService.sell(player, itemId);
    }

    @PostMapping("/{itemId}/enhance")
    InventoryService.EnhanceResult enhance(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var result = inventoryService.enhance(player, itemId);
        questService.recordEvent(player.id(), QuestEvent.of("enhancementAttempts"));
        if (result.success()) {
            questService.recordEvent(player.id(), QuestEvent.of("enhancementSuccesses"));
        }
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, result.inventory().combatPower()));
        return result;
    }

    @PostMapping("/transfer-enhancement")
    InventoryService.EnhancementTransferResult transferEnhancement(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody EnhancementTransferRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        var result = inventoryService.transferEnhancement(player, request.sourceItemId(), request.targetItemId());
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, result.inventory().combatPower()));
        return result;
    }

    @PostMapping("/bulk-sell")
    InventoryService.BulkSellResult bulkSell(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody BulkSellRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return inventoryService.bulkSell(player, request.qualities(), request.itemTypes());
    }

    @PostMapping("/organize")
    InventoryService.InventorySnapshot organize(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody OrganizeRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return inventoryService.organize(player, request.sort());
    }

    record BulkSellRequest(List<String> qualities, List<String> itemTypes) {
    }

    record OrganizeRequest(String sort) {
    }

    record EnhancementTransferRequest(long sourceItemId, long targetItemId) {
    }
}
