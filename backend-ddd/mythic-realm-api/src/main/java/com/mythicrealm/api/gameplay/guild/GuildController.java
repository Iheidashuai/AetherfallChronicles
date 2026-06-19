package com.mythicrealm.api.gameplay.guild;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guild")
public class GuildController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final GuildService guildService;
    private final GuildBossService guildBossService;

    public GuildController(
        SessionService sessionService,
        PlayerService playerService,
        GuildService guildService,
        GuildBossService guildBossService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.guildService = guildService;
        this.guildBossService = guildBossService;
    }

    @GetMapping("/guilds")
    List<GuildService.GuildSummary> browse(@RequestHeader(name = "Authorization", required = false) String authorization) {
        requirePlayer(authorization);
        return guildService.browse();
    }

    @GetMapping
    GuildHomeResponse myGuild(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return new GuildHomeResponse(guildService.myGuild(requirePlayer(authorization)));
    }

    @PostMapping("/{guildId}/join")
    GuildHomeResponse join(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long guildId
    ) {
        return new GuildHomeResponse(guildService.join(requirePlayer(authorization), guildId));
    }

    @PostMapping("/leave")
    GuildHomeResponse leave(@RequestHeader(name = "Authorization", required = false) String authorization) {
        guildService.leave(requirePlayer(authorization));
        return new GuildHomeResponse(null);
    }

    @GetMapping("/boss")
    GuildBossService.GuildBossView boss(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return guildBossService.bossFor(requirePlayer(authorization));
    }

    @PostMapping("/boss/attack")
    GuildBossService.AttackResult attackBoss(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return guildBossService.attack(requirePlayer(authorization));
    }

    @GetMapping("/chat")
    List<GuildService.GuildChatMessage> chat(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return guildService.chat(requirePlayer(authorization));
    }

    @PostMapping("/chat")
    List<GuildService.GuildChatMessage> sendChat(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @Valid @RequestBody SendGuildMessageRequest request
    ) {
        return guildService.sendChat(requirePlayer(authorization), request.text());
    }

    @PostMapping("/donate")
    GuildService.DonateResult donate(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestBody DonateRequest request
    ) {
        return guildService.donate(requirePlayer(authorization), request.amount());
    }

    @GetMapping("/shop")
    GuildService.GuildShopView shop(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return guildService.shop(requirePlayer(authorization));
    }

    @PostMapping("/shop/{offerId}/buy")
    GuildService.GuildShopView buy(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable String offerId
    ) {
        return guildService.buy(requirePlayer(authorization), offerId);
    }

    @GetMapping("/ranking")
    List<GuildService.GuildRankEntry> ranking(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return guildService.ranking(requirePlayer(authorization));
    }

    private PlayerRecord requirePlayer(String authorization) {
        return playerService.requireByAccount(sessionService.require(authorization));
    }

    record GuildHomeResponse(GuildService.GuildView guild) {
    }

    record SendGuildMessageRequest(@NotBlank String text) {
    }

    record DonateRequest(long amount) {
    }
}
