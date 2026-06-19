package com.mythicrealm.api.gameplay.build;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.endgame.EndgameRiftService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/builds")
public class BuildController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final BuildService buildService;

    public BuildController(SessionService sessionService, PlayerService playerService, BuildService buildService) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.buildService = buildService;
    }

    @GetMapping
    public BuildService.BuildSnapshot snapshot(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return buildService.snapshot(player(authorization));
    }

    @PostMapping
    public BuildService.BuildSnapshot create(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody(required = false) BuildService.BuildMutationRequest request
    ) {
        return buildService.create(player(authorization), request);
    }

    @PostMapping("/presets/{presetId}/copy")
    public BuildService.BuildSnapshot copyPreset(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("presetId") String presetId
    ) {
        return buildService.copyPreset(player(authorization), presetId);
    }

    @PutMapping("/{buildId}")
    public BuildService.BuildSnapshot update(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("buildId") long buildId,
        @RequestBody BuildService.BuildMutationRequest request
    ) {
        return buildService.update(player(authorization), buildId, request);
    }

    @PostMapping("/{buildId}/activate")
    public BuildService.BuildActivationResult activate(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("buildId") long buildId
    ) {
        return buildService.activate(player(authorization), buildId);
    }

    @PostMapping("/{buildId}/simulate")
    public EndgameRiftService.RiftSimulationResult simulate(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("buildId") long buildId,
        @RequestBody(required = false) BuildSimulationRequest request
    ) {
        int tier = request == null ? 1 : Math.max(1, request.tier());
        return buildService.simulate(player(authorization), buildId, tier);
    }

    private PlayerRecord player(String authorization) {
        return playerService.requireByAccount(sessionService.require(authorization));
    }

    public record BuildSimulationRequest(int tier) {
    }
}
