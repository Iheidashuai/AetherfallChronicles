package com.mythicrealm.api.gameplay.skill;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final SkillService skillService;

    public SkillController(SessionService sessionService, PlayerService playerService, SkillService skillService) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.skillService = skillService;
    }

    @GetMapping
    public SkillService.SkillSnapshot snapshot(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return skillService.snapshot(player);
    }

    @PostMapping("/{skillId}/learn")
    public SkillService.SkillSnapshot learn(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("skillId") String skillId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return skillService.learn(player, skillId);
    }

    @PostMapping("/{skillId}/upgrade")
    public SkillService.SkillSnapshot upgrade(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable("skillId") String skillId
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return skillService.upgrade(player, skillId);
    }
}
