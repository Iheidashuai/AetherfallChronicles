package com.mythicrealm.api.gameplay.player;

import com.mythicrealm.api.gameplay.auth.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {
    private final SessionService sessionService;
    private final PlayerService playerService;

    public PlayerController(SessionService sessionService, PlayerService playerService) {
        this.sessionService = sessionService;
        this.playerService = playerService;
    }

    @PostMapping
    PlayerRecord create(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @Valid @RequestBody CreatePlayerRequest request
    ) {
        return playerService.createPlayer(
            sessionService.require(authorization),
            request.name(),
            request.profession()
        );
    }

    record CreatePlayerRequest(@NotBlank String name, String profession) {
    }
}
