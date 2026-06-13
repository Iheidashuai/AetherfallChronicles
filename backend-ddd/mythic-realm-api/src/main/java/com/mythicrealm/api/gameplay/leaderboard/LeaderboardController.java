package com.mythicrealm.api.gameplay.leaderboard;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final LeaderboardService leaderboardService;
    private final QuestService questService;

    public LeaderboardController(
        SessionService sessionService,
        PlayerService playerService,
        LeaderboardService leaderboardService,
        QuestService questService
    ) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.leaderboardService = leaderboardService;
        this.questService = questService;
    }

    @GetMapping("/power")
    List<LeaderboardService.LeaderboardEntry> power(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        questService.recordEvent(player.id(), QuestEvent.of("leaderboardViewed"));
        List<LeaderboardService.LeaderboardEntry> entries = leaderboardService.entries(player);
        entries.stream()
            .filter(LeaderboardService.LeaderboardEntry::player)
            .findFirst()
            .ifPresent(entry -> questService.recordEvent(player.id(), new QuestEvent("leaderboardRankReached", null, entry.rank())));
        return entries;
    }
}
