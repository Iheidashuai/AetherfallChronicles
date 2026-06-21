package com.mythicrealm.api.gameplay.robot;

import com.mythicrealm.api.gameplay.admin.AdminAccessService;
import com.mythicrealm.api.gameplay.auth.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/robot-speed")
public class RobotSpeedController {
    private final AdminAccessService adminAccessService;
    private final RobotSpeedService robotSpeedService;
    private final RobotActivityService robotActivityService;

    public RobotSpeedController(
        AdminAccessService adminAccessService,
        RobotSpeedService robotSpeedService,
        RobotActivityService robotActivityService
    ) {
        this.adminAccessService = adminAccessService;
        this.robotSpeedService = robotSpeedService;
        this.robotActivityService = robotActivityService;
    }

    @GetMapping
    RobotSpeedService.RobotSpeedView view(@RequestHeader(name = "Authorization", required = false) String authorization) {
        adminAccessService.requireAdmin(authorization);
        return robotSpeedService.view();
    }

    @PutMapping
    RobotSpeedService.RobotSpeedView update(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @Valid @RequestBody RobotSpeedUpdateRequest request
    ) {
        AuthenticatedAccount account = adminAccessService.requireAdmin(authorization);
        RobotSpeedService.RobotSpeedView view = robotSpeedService.updateMultiplier(account.username(), request.multiplier());
        robotActivityService.triggerNow("admin:" + account.username());
        return view;
    }

    record RobotSpeedUpdateRequest(@NotNull Integer multiplier) {
    }
}
