package com.mythicrealm.api.gameplay.endgame;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/endgame/rifts")
public class EndgameRiftController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final EndgameRiftService riftService;

    public EndgameRiftController(SessionService sessionService, PlayerService playerService, EndgameRiftService riftService) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.riftService = riftService;
    }

    @GetMapping
    EndgameRiftService.RiftSnapshot snapshot(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var player = playerService.requireByAccount(sessionService.require(authorization));
        return riftService.snapshot(player);
    }

    @PostMapping("/{tier}/runs")
    EndgameRiftService.RiftRunResult run(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable("tier") int tier
    ) {
        var player = playerService.requireByAccount(sessionService.require(authorization));
        return riftService.run(player, tier, idempotencyKey);
    }

    @PostMapping("/weekly-reward")
    EndgameRiftService.WeeklyRewardResult weeklyReward(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var player = playerService.requireByAccount(sessionService.require(authorization));
        return riftService.claimWeeklyReward(player);
    }
}
