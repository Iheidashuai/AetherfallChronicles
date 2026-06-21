package com.mythicrealm.api.gameplay.ai;

import com.mythicrealm.api.gameplay.admin.AdminAccessService;
import com.mythicrealm.api.gameplay.common.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/usage")
public class AiUsageController {
    private final AdminAccessService adminAccessService;
    private final AiUsageService aiUsageService;

    public AiUsageController(AdminAccessService adminAccessService, AiUsageService aiUsageService) {
        this.adminAccessService = adminAccessService;
        this.aiUsageService = aiUsageService;
    }

    @GetMapping("/summary")
    AiUsageService.AiUsageSummary summary(@RequestHeader(name = "Authorization", required = false) String authorization) {
        adminAccessService.requireAdmin(authorization);
        return aiUsageService.summary();
    }

    @GetMapping("/calls")
    java.util.List<AiUsageService.AiModelCallRow> calls(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestParam(defaultValue = "100") int limit
    ) {
        adminAccessService.requireAdmin(authorization);
        return aiUsageService.calls(limit);
    }

    @GetMapping("/calls/{id}")
    AiUsageService.AiModelCallDetail call(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @PathVariable long id
    ) {
        adminAccessService.requireAdmin(authorization);
        AiUsageService.AiModelCallDetail detail = aiUsageService.call(id);
        if (detail == null) {
            throw ApiException.notFound("AI 调用不存在");
        }
        return detail;
    }
}
