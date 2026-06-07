package com.mythicrealm.backend.gameconfig;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final GameConfigService gameConfigService;

    public ConfigController(GameConfigService gameConfigService) {
        this.gameConfigService = gameConfigService;
    }

    @GetMapping("/bootstrap")
    GameConfigService.ConfigSummary bootstrap() {
        return gameConfigService.summary();
    }
}
