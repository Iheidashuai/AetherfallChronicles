package com.mythicrealm.api.gameplay.dungeon;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.gameconfig.GameConfigService;
import com.mythicrealm.api.gameplay.player.PlayerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dungeons")
public class DungeonController {
    private final SessionService sessionService;
    private final GameConfigService gameConfigService;
    private final DungeonService dungeonService;
    private final PlayerService playerService;

    public DungeonController(
        SessionService sessionService,
        GameConfigService gameConfigService,
        DungeonService dungeonService,
        PlayerService playerService
    ) {
        this.sessionService = sessionService;
        this.gameConfigService = gameConfigService;
        this.dungeonService = dungeonService;
        this.playerService = playerService;
    }

    @GetMapping
    List<DungeonService.DungeonProgressPreview> dungeons(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return gameConfigService.dungeonPreviews().stream()
                .map(preview -> DungeonService.DungeonProgressPreview.from(preview, false))
                .toList();
        }
        var player = playerService.requireByAccount(sessionService.require(authorization));
        return dungeonService.dungeonPreviews(player.id());
    }

    @PostMapping("/{dungeonId}/runs")
    DungeonService.DungeonRunResult run(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable("dungeonId") String dungeonId
    ) {
        return dungeonService.runDungeon(
            sessionService.require(authorization),
            dungeonId,
            idempotencyKey
        );
    }

    @PostMapping("/{dungeonId}/sweeps")
    DungeonService.DungeonSweepResult sweep(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable("dungeonId") String dungeonId,
        @RequestBody(required = false) SweepRequest request
    ) {
        return dungeonService.sweepDungeon(
            sessionService.require(authorization),
            dungeonId,
            request == null ? 10 : request.times(),
            idempotencyKey
        );
    }

    record SweepRequest(int times) {
    }
}
