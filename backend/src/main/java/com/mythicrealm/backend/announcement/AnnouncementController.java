package com.mythicrealm.backend.announcement;

import com.mythicrealm.backend.auth.SessionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final SessionService sessionService;
    private final AnnouncementService announcementService;

    public AnnouncementController(SessionService sessionService, AnnouncementService announcementService) {
        this.sessionService = sessionService;
        this.announcementService = announcementService;
    }

    @GetMapping
    List<AnnouncementService.AnnouncementView> latest(@RequestHeader(name = "Authorization", required = false) String authorization) {
        sessionService.require(authorization);
        return announcementService.latest();
    }
}
