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
@RequestMapping("/api/equipment-processing")
public class EquipmentProcessingController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final EquipmentProcessingService processingService;
    private final QuestService questService;

    public EquipmentProcessingController(
        SessionService sessionService,
        PlayerService playerService,
        EquipmentProcessingService processingService,
        QuestService questService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.processingService = processingService;
        this.questService = questService;
    }

    @GetMapping
    EquipmentProcessingService.ProcessingSnapshot snapshot(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return processingService.snapshot(player(authorization));
    }

    @PostMapping("/{itemId}/sockets/unlock")
    EquipmentProcessingService.ProcessingResult unlockSocket(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId
    ) {
        var result = processingService.unlockSocket(player(authorization), itemId);
        recordProcessingQuest(result);
        return result;
    }

    @PostMapping("/{itemId}/sockets/{socketIndex}/socket")
    EquipmentProcessingService.ProcessingResult socketGem(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId,
        @PathVariable("socketIndex") int socketIndex,
        @RequestBody SocketGemRequest request
    ) {
        var result = processingService.socketGem(player(authorization), itemId, socketIndex, request.gemItemId());
        recordProcessingQuest(result);
        return result;
    }

    @PostMapping("/{itemId}/sockets/{socketIndex}/unsocket")
    EquipmentProcessingService.ProcessingResult unsocketGem(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId,
        @PathVariable("socketIndex") int socketIndex
    ) {
        var result = processingService.unsocketGem(player(authorization), itemId, socketIndex);
        recordProcessingQuest(result);
        return result;
    }

    @PostMapping("/gems/upgrade")
    EquipmentProcessingService.ProcessingResult upgradeGems(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody UpgradeGemsRequest request
    ) {
        var result = processingService.upgradeGems(player(authorization), request.gemItemIds());
        recordProcessingQuest(result);
        return result;
    }

    @PostMapping("/{itemId}/reforge")
    EquipmentProcessingService.ProcessingResult reforge(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId,
        @RequestBody(required = false) ReforgeRequest request
    ) {
        var result = processingService.reforge(player(authorization), itemId, request == null ? List.of() : request.lockedAffixIndexes());
        recordProcessingQuest(result);
        return result;
    }

    @PostMapping("/{itemId}/ascend")
    EquipmentProcessingService.ProcessingResult ascend(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("itemId") long itemId,
        @RequestBody(required = false) AscendRequest request
    ) {
        var result = processingService.ascend(player(authorization), itemId, request != null && request.useProtector());
        recordProcessingQuest(result);
        return result;
    }

    private PlayerRecord player(String authorization) {
        return playerService.requireByAccount(sessionService.require(authorization));
    }

    private void recordProcessingQuest(EquipmentProcessingService.ProcessingResult result) {
        PlayerRecord player = playerService.requireById(result.item().playerId());
        questService.recordEvent(player.id(), new QuestEvent("equipmentProcessed", result.actionType(), 1));
        questService.recordEvent(player.id(), new QuestEvent("combatPowerReached", null, result.powerAfter()));
    }

    record SocketGemRequest(long gemItemId) {
    }

    record UpgradeGemsRequest(List<Long> gemItemIds) {
    }

    record ReforgeRequest(List<Integer> lockedAffixIndexes) {
    }

    record AscendRequest(boolean useProtector) {
    }
}
