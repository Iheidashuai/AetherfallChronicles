package com.mythicrealm.api.gameplay.arena;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.domain.arena.ArenaService;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaMatchDetail;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaOverview;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaProfileView;
import com.mythicrealm.domain.arena.model.ArenaModels.ArenaShopPurchaseResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arena")
public class ArenaController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final ArenaService arenaService;

    public ArenaController(SessionService sessionService, PlayerService playerService, ArenaService arenaService) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.arenaService = arenaService;
    }

    @GetMapping
    ArenaOverview overview(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return arenaService.overview(player.id());
    }

    @PostMapping("/challenge/{targetId}")
    ArenaMatchDetail challenge(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long targetId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return arenaService.challenge(player.id(), targetId);
    }

    @GetMapping("/matches/{matchId}")
    ArenaMatchDetail match(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long matchId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return arenaService.matchDetail(player.id(), matchId);
    }

    @GetMapping("/rankings")
    List<ArenaProfileView> rankings(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return arenaService.rankings(player.id(), 100);
    }

    @PostMapping("/shop/{offerId}/buy")
    ArenaShopPurchaseResult buyShopOffer(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable String offerId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return arenaService.buyShopOffer(player.id(), offerId);
    }
}
