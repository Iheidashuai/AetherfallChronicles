package com.mythicrealm.api.gameplay.recharge;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recharge")
public class RechargeController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final RechargeService rechargeService;

    public RechargeController(
        SessionService sessionService,
        PlayerService playerService,
        RechargeService rechargeService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.rechargeService = rechargeService;
    }

    @GetMapping("/dashboard")
    RechargeService.RechargeDashboard dashboard(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return rechargeService.dashboard(player);
    }

    @PostMapping
    RechargeService.RechargeResult recharge(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @Valid @RequestBody RechargeRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return rechargeService.recharge(player, request.rmbAmount(), "manual_click", "player_wallet");
    }

    record RechargeRequest(@Min(1) long rmbAmount) {
    }
}
