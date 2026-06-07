package com.mythicrealm.api.gameplay.chat;

import com.mythicrealm.api.gameplay.auth.SessionService;
import com.mythicrealm.api.gameplay.player.PlayerRecord;
import com.mythicrealm.api.gameplay.player.PlayerService;
import com.mythicrealm.api.gameplay.quest.QuestService;
import com.mythicrealm.api.gameplay.quest.QuestService.QuestEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final SessionService sessionService;
    private final PlayerService playerService;
    private final ChatService chatService;
    private final QuestService questService;

    public ChatController(SessionService sessionService, PlayerService playerService, ChatService chatService, QuestService questService) {
        this.sessionService = sessionService;
        this.playerService = playerService;
        this.chatService = chatService;
        this.questService = questService;
    }

    @GetMapping("/messages")
    List<ChatService.ChatMessageView> messages(@RequestHeader(name = "Authorization", required = false) String authorization) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        questService.recordEvent(player.id(), QuestEvent.of("chatOpened"));
        return chatService.messages();
    }

    @PostMapping("/messages")
    ChatService.ChatMessageView send(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @Valid @RequestBody SendMessageRequest request
    ) {
        PlayerRecord player = playerService.requireByAccount(sessionService.require(authorization));
        return chatService.send(player, request.text());
    }

    record SendMessageRequest(@NotBlank String text) {
    }
}
