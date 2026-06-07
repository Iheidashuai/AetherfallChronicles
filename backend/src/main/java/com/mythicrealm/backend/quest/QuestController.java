package com.mythicrealm.backend.quest;

import com.mythicrealm.backend.auth.SessionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quests")
public class QuestController {
    private final SessionService sessionService;
    private final QuestService questService;

    public QuestController(SessionService sessionService, QuestService questService) {
        this.sessionService = sessionService;
        this.questService = questService;
    }

    @GetMapping
    List<QuestService.QuestRow> list(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return questService.rows(sessionService.require(authorization));
    }

    @PostMapping("/{questId}/claim")
    QuestService.QuestClaimResult claim(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable String questId
    ) {
        return questService.claim(sessionService.require(authorization), questId);
    }
}
