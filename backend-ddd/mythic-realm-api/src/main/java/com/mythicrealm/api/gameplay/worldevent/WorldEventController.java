package com.mythicrealm.api.gameplay.worldevent;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world-events")
public class WorldEventController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final WorldEventService worldEventService;

    public WorldEventController(
        SessionService sessionService,
        PlayerService playerService,
        WorldEventService worldEventService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.worldEventService = worldEventService;
    }

    @GetMapping
    List<WorldEvent> events(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var account = sessionService.require(authorization);
        var player = playerService.requireByAccount(account);
        return worldEventService.events(player);
    }
}
