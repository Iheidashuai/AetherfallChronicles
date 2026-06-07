package com.mythicrealm.backend.robot;

import com.mythicrealm.backend.auth.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/robots")
public class RobotActivityController {
    private final SessionService sessionService;
    private final RobotActivityLogService robotActivityLogService;

    public RobotActivityController(SessionService sessionService, RobotActivityLogService robotActivityLogService) {
        this.sessionService = sessionService;
        this.robotActivityLogService = robotActivityLogService;
    }

    @GetMapping("/activity")
    RobotActivityLogService.RobotActivitySnapshot activity(@RequestHeader(name = "Authorization", required = false) String authorization) {
        sessionService.require(authorization);
        return robotActivityLogService.snapshot();
    }

    @GetMapping("/{robotId}/activity")
    RobotActivityLogService.RobotActivityDetail activityDetail(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long robotId
    ) {
        sessionService.require(authorization);
        return robotActivityLogService.detail(robotId);
    }
}
