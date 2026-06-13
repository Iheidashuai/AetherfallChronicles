package com.mythicrealm.domain.enhancement.application;

import com.mythicrealm.domain.enhancement.application.command.EnhanceCommand;
import com.mythicrealm.domain.enhancement.event.EnhancementAttemptedEvent;
import com.mythicrealm.domain.enhancement.event.EnhancementFailedEvent;
import com.mythicrealm.domain.enhancement.event.EnhancementSucceededEvent;
import com.mythicrealm.domain.enhancement.model.Enhancement;
import com.mythicrealm.domain.enhancement.repository.EnhancementRepository;
import com.mythicrealm.domain.enhancement.service.EnhancementCostCalculator;
import com.mythicrealm.domain.enhancement.service.EnhancementStrategy;
import com.mythicrealm.domain.enhancement.service.NormalEnhancementStrategy;
import com.mythicrealm.domain.enhancement.service.RiskyEnhancementStrategy;
import com.mythicrealm.domain.enhancement.service.SafeEnhancementStrategy;
import com.mythicrealm.domain.enhancement.valueobject.EnhancementResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

/**
 * 强化应用服务
 */
@Service
public class EnhancementApplicationService {

    private final EnhancementRepository enhancementRepository;
    private final EnhancementCostCalculator costCalculator;
    private final List<EnhancementStrategy> strategies;
    private final ApplicationEventPublisher eventPublisher;

    public EnhancementApplicationService(
        EnhancementRepository enhancementRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.enhancementRepository = enhancementRepository;
        this.eventPublisher = eventPublisher;
        this.costCalculator = new EnhancementCostCalculator();
        this.strategies = List.of(
            new SafeEnhancementStrategy(),
            new NormalEnhancementStrategy(),
            new RiskyEnhancementStrategy()
        );
    }

    /**
     * 执行强化
     *
     * @param command 强化命令
     * @param itemRequiredLevel 装备需求等级（从外部获取）
     * @param currentGold 当前金币（从外部获取）
     * @return 强化结果
     */
    @Transactional
    public EnhancementResult enhance(EnhanceCommand command, int itemRequiredLevel, int currentGold) {
        // 1. 获取或创建强化对象
        Enhancement enhancement = enhancementRepository.findByItemId(command.itemId())
            .orElseGet(() -> Enhancement.create(command.itemId(), command.playerId()));

        // 2. 验证前置条件
        if (enhancement.getLevel().isMaxLevel()) {
            throw new IllegalStateException("装备已强化到上限");
        }

        // 3. 计算成本
        int targetLevel = enhancement.getLevelValue() + 1;
        int cost = costCalculator.calculate(itemRequiredLevel, targetLevel);

        if (currentGold < cost) {
            throw new IllegalStateException("金币不足，需要 " + cost + " 金");
        }

        // 4. 选择策略并计算成功率
        EnhancementStrategy strategy = selectStrategy(targetLevel);
        double successRate = strategy.calculateFinalSuccessRate(enhancement);

        // 5. 发布尝试事件
        eventPublisher.publishEvent(EnhancementAttemptedEvent.of(
            command.itemId(),
            command.playerId(),
            enhancement.getLevelValue(),
            targetLevel,
            successRate,
            cost
        ));

        // 6. 随机判定成功/失败
        boolean success = new Random(System.nanoTime() + command.itemId()).nextDouble() <= successRate;

        // 7. 执行强化
        EnhancementResult result = enhancement.attemptEnhancement(success, cost, successRate);

        // 8. 保存结果
        enhancementRepository.save(enhancement);

        // 9. 发布结果事件
        if (result.success()) {
            eventPublisher.publishEvent(EnhancementSucceededEvent.of(
                command.itemId(),
                command.playerId(),
                result.previousLevel().value(),
                result.currentLevel().value(),
                cost,
                successRate
            ));
        } else {
            eventPublisher.publishEvent(EnhancementFailedEvent.of(
                command.itemId(),
                command.playerId(),
                result.previousLevel().value(),
                result.currentLevel().value(),
                result.currentLuck().value(),
                cost,
                successRate
            ));
        }

        return result;
    }

    /**
     * 根据目标等级选择合适的策略
     */
    private EnhancementStrategy selectStrategy(int targetLevel) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(targetLevel))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的目标等级: " + targetLevel));
    }
}
