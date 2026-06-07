package com.mythicrealm.domain.enhancement.interfaces;

import com.mythicrealm.domain.enhancement.application.EnhancementApplicationService;
import com.mythicrealm.domain.enhancement.application.command.EnhanceCommand;
import com.mythicrealm.domain.enhancement.interfaces.dto.EnhanceResultDTO;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementResult;
import org.springframework.web.bind.annotation.*;

/**
 * 强化控制器
 */
@RestController
@RequestMapping("/api/enhancement")
public class EnhancementController {

    private final EnhancementApplicationService enhancementApplicationService;

    public EnhancementController(EnhancementApplicationService enhancementApplicationService) {
        this.enhancementApplicationService = enhancementApplicationService;
    }

    /**
     * 强化装备
     *
     * @param playerId 玩家ID
     * @param itemId 装备ID
     * @param itemRequiredLevel 装备需求等级（从请求中获取或从数据库查询）
     * @param currentGold 当前金币
     * @return 强化结果
     */
    @PostMapping("/enhance")
    public EnhanceResultDTO enhance(
        @RequestParam("playerId") long playerId,
        @RequestParam("itemId") long itemId,
        @RequestParam("itemRequiredLevel") int itemRequiredLevel,
        @RequestParam("currentGold") int currentGold
    ) {
        EnhanceCommand command = new EnhanceCommand(playerId, itemId);
        EnhancementResult result = enhancementApplicationService.enhance(
            command,
            itemRequiredLevel,
            currentGold
        );

        return EnhanceResultDTO.from(
            result.success(),
            result.previousLevel().value(),
            result.currentLevel().value(),
            result.previousLuck().value(),
            result.currentLuck().value(),
            result.cost(),
            result.successRate()
        );
    }
}
