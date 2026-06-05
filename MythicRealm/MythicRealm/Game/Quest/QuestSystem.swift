import Foundation

enum QuestSystem {
    private static let clockRollbackTolerance: TimeInterval = 5 * 60
    private static let clockProtectionDuration: TimeInterval = 30 * 60

    @discardableResult
    static func bootstrap(gameState: GameState, now: Date = Date()) -> Bool {
        var state = gameState.questState
        let configs = ConfigLoader.shared.questConfigs
        var changed = prepareState(&state, configs: configs, gameState: gameState, now: now)
        changed = syncStateConditions(&state, configs: configs, gameState: gameState, now: now) || changed
        changed = updateCompletionAndLocks(&state, configs: configs, now: now) || changed

        gameState.questState = state
        if changed {
            gameState.saveProgress()
        }
        return changed
    }

    static func record(_ event: QuestEvent, gameState: GameState, now: Date = Date()) {
        var state = gameState.questState
        let configs = ConfigLoader.shared.questConfigs
        _ = prepareState(&state, configs: configs, gameState: gameState, now: now)

        var changed = false
        for config in configs {
            guard shouldTrack(config, state: state) else { continue }
            var progress = state.progressById[config.id] ?? QuestProgress(
                questId: config.id,
                status: .active,
                cycleKey: config.category == .daily ? state.dailyCycleKey : nil
            )

            for condition in config.conditions {
                if apply(event, to: condition, progress: &progress) {
                    changed = true
                }
            }
            state.progressById[config.id] = progress
        }

        changed = syncStateConditions(&state, configs: configs, gameState: gameState, now: now) || changed
        changed = updateCompletionAndLocks(&state, configs: configs, now: now) || changed

        if changed {
            gameState.questState = state
            gameState.saveProgress()
        } else {
            gameState.questState = state
        }
    }

    static func claim(questId: String, gameState: GameState, now: Date = Date()) -> QuestClaimResult {
        var state = gameState.questState
        let configs = ConfigLoader.shared.questConfigs
        _ = prepareState(&state, configs: configs, gameState: gameState, now: now)
        _ = syncStateConditions(&state, configs: configs, gameState: gameState, now: now)
        _ = updateCompletionAndLocks(&state, configs: configs, now: now)

        guard let config = configs.first(where: { $0.id == questId }) else {
            return .failure("委托不存在")
        }
        guard var progress = state.progressById[questId], progress.status == .completed else {
            return .failure("委托尚未完成")
        }
        if config.category == .daily, isClockProtected(state, now: now) {
            return .failure("公会沙漏正在校验，稍后再领取每日委托奖励")
        }
        guard canApplyRewards(config.rewards, gameState: gameState) else {
            return .failure("背包空间不足，无法领取装备奖励")
        }

        applyRewards(config.rewards, gameState: gameState)
        progress.status = .claimed
        progress.claimedAt = now
        state.progressById[questId] = progress
        _ = syncStateConditions(&state, configs: configs, gameState: gameState, now: now)
        _ = updateCompletionAndLocks(&state, configs: configs, now: now)
        gameState.questState = state
        gameState.saveProgress()
        return .success("领取成功：\(rewardSummary(config.rewards))")
    }

    static func claimAll(gameState: GameState, now: Date = Date()) -> QuestClaimResult {
        bootstrap(gameState: gameState, now: now)
        let configs = sortedQuestConfigs(ConfigLoader.shared.questConfigs)
        let claimable = configs.filter { config in
            gameState.questState.progressById[config.id]?.status == .completed
        }
        guard !claimable.isEmpty else {
            return .failure("暂无可领取委托")
        }
        let allRewards = claimable.flatMap(\.rewards)
        guard canApplyRewards(allRewards, gameState: gameState) else {
            return .failure("背包空间不足，无法一键领取")
        }

        var claimed = 0
        for config in claimable {
            let result = claim(questId: config.id, gameState: gameState, now: now)
            if result.success {
                claimed += 1
            }
        }
        return claimed > 0
            ? .success("已领取 \(claimed) 份委托奖励", claimedCount: claimed)
            : .failure("暂无可领取委托")
    }

    static func questRows(gameState: GameState, category: QuestCategory? = nil) -> [(QuestConfig, QuestProgress)] {
        let configs = sortedQuestConfigs(ConfigLoader.shared.questConfigs).filter { config in
            guard let category else { return true }
            return config.category == category
        }
        return configs.compactMap { config in
            guard let progress = gameState.questState.progressById[config.id] else { return nil }
            return (config, progress)
        }
    }

    static func recommendedQuests(gameState: GameState, limit: Int = 3) -> [(QuestConfig, QuestProgress)] {
        questRows(gameState: gameState)
            .filter { config, progress in
                config.category == .main || progress.status == .completed || (config.category == .daily && progress.status == .active)
            }
            .sorted { lhs, rhs in
                let lhsScore = recommendationScore(config: lhs.0, progress: lhs.1)
                let rhsScore = recommendationScore(config: rhs.0, progress: rhs.1)
                if lhsScore != rhsScore { return lhsScore > rhsScore }
                return lhs.0.priority < rhs.0.priority
            }
            .prefix(limit)
            .map { $0 }
    }

    static func hasClaimableRewards(_ state: QuestState) -> Bool {
        state.progressById.values.contains { $0.status == .completed }
    }

    static func activeSummary(gameState: GameState) -> String {
        if let completed = questRows(gameState: gameState).first(where: { $0.1.status == .completed }) {
            return "可领取：\(completed.0.title)"
        }
        if let main = questRows(gameState: gameState, category: .main).first(where: { $0.1.status == .active }) {
            return "\(main.0.title) · \(progressText(config: main.0, progress: main.1))"
        }
        if let daily = questRows(gameState: gameState, category: .daily).first(where: { $0.1.status == .active }) {
            return "\(daily.0.title) · \(progressText(config: daily.0, progress: daily.1))"
        }
        return "公会书记正在整理新的委托"
    }

    static func progressText(config: QuestConfig, progress: QuestProgress) -> String {
        guard let condition = config.conditions.first else { return "完成" }
        let current = min(currentValue(for: condition, progress: progress), condition.targetValue)
        return "\(current)/\(condition.targetValue)"
    }

    static func rewardSummary(_ rewards: [QuestReward]) -> String {
        rewards.map { reward in
            switch reward.type {
            case .gold:
                return "\(reward.amount) 金"
            case .experience:
                return "\(reward.amount) 经验"
            case .itemTemplate:
                if let template = ConfigLoader.shared.itemTemplates.first(where: { $0.id == reward.targetId }) {
                    return reward.amount > 1 ? "\(template.compactDisplayName) x\(reward.amount)" : template.compactDisplayName
                }
                return "装备箱"
            }
        }
        .joined(separator: " / ")
    }

    static func isClockProtected(_ state: QuestState, now: Date = Date()) -> Bool {
        guard let until = state.clockProtectionUntil else { return false }
        return until > now
    }

    static func dailyCountdownText(now: Date = Date()) -> String {
        let remaining = max(0, Int(QuestDateHelper.nextDailyRefreshDate(after: now).timeIntervalSince(now)))
        let hours = remaining / 3600
        let minutes = (remaining % 3600) / 60
        if hours > 0 { return "\(hours)时\(minutes)分" }
        return "\(minutes)分"
    }

    // MARK: - State preparation

    private static func prepareState(
        _ state: inout QuestState,
        configs: [QuestConfig],
        gameState: GameState,
        now: Date
    ) -> Bool {
        var changed = false
        if let lastSeen = state.lastSeenAt, now < lastSeen.addingTimeInterval(-clockRollbackTolerance) {
            state.clockProtectionUntil = now.addingTimeInterval(clockProtectionDuration)
            changed = true
        }

        let protected = isClockProtected(state, now: now)
        let currentCycle = QuestDateHelper.dailyCycleKey(for: now)
        if state.dailyCycleKey.isEmpty {
            state.dailyCycleKey = currentCycle
            changed = true
        }
        if !protected && state.dailyCycleKey != currentCycle {
            state.dailyCycleKey = currentCycle
            state.lastDailyRefreshAt = now
            for config in configs where config.category == .daily {
                state.progressById[config.id] = QuestProgress(questId: config.id, status: .active, cycleKey: currentCycle)
            }
            changed = true
        }

        for config in configs {
            let expectedCycle = config.category == .daily ? state.dailyCycleKey : nil
            if var progress = state.progressById[config.id] {
                if config.category == .daily, progress.cycleKey != state.dailyCycleKey, !protected {
                    progress = QuestProgress(questId: config.id, status: .active, cycleKey: state.dailyCycleKey)
                    changed = true
                }
                let shouldBeLocked = !isUnlocked(config, state: state)
                if progress.status == .locked && !shouldBeLocked {
                    progress.status = .active
                    changed = true
                } else if progress.status == .active && shouldBeLocked {
                    progress.status = .locked
                    progress.conditionValues = [:]
                    changed = true
                }
                if progress.cycleKey != expectedCycle {
                    progress.cycleKey = expectedCycle
                    changed = true
                }
                state.progressById[config.id] = progress
            } else {
                let status: QuestStatus = isUnlocked(config, state: state) ? .active : .locked
                state.progressById[config.id] = QuestProgress(questId: config.id, status: status, cycleKey: expectedCycle)
                changed = true
            }
        }

        if state.lastSeenAt == nil || now > (state.lastSeenAt ?? now) {
            state.lastSeenAt = now
            changed = true
        }
        if state.lastDailyRefreshAt == nil {
            state.lastDailyRefreshAt = now
            changed = true
        }

        if changed {
            state = normalizeProgress(state, configs: configs, gameState: gameState)
        }
        return changed
    }

    private static func normalizeProgress(_ state: QuestState, configs: [QuestConfig], gameState: GameState) -> QuestState {
        var normalized = state
        let validIds = Set(configs.map(\.id))
        normalized.progressById = normalized.progressById.filter { validIds.contains($0.key) }
        return normalized
    }

    private static func isUnlocked(_ config: QuestConfig, state: QuestState) -> Bool {
        config.prerequisiteIds.allSatisfy { prerequisiteId in
            guard let status = state.progressById[prerequisiteId]?.status else { return false }
            return status == .claimed
        }
    }

    private static func shouldTrack(_ config: QuestConfig, state: QuestState) -> Bool {
        guard let progress = state.progressById[config.id] else { return false }
        return progress.status == .active
    }

    // MARK: - Progress

    private static func apply(_ event: QuestEvent, to condition: QuestCondition, progress: inout QuestProgress) -> Bool {
        let before = currentValue(for: condition, progress: progress)
        var after = before

        switch (condition.type, event) {
        case (.dungeonCompleted, .dungeonCompleted(let dungeonId, _, _)):
            if condition.targetId == nil || condition.targetId == dungeonId {
                after += 1
            }
        case (.dungeonClears, .dungeonCompleted):
            after += 1
        case (.monsterKills, .dungeonCompleted(_, let monstersKilled, _)):
            after += monstersKilled
        case (.itemQualityObtained, .dungeonCompleted(_, _, let qualities)):
            after += qualities.filter { qualityMeets($0, targetId: condition.targetId) }.count
        case (.itemQualityObtained, .marketPurchased(let quality, _)):
            if qualityMeets(quality, targetId: condition.targetId) {
                after += 1
            }
        case (.equipmentEquipped, .equipmentEquipped):
            after += 1
        case (.enhancementAttempts, .enhancementAttempt):
            after += 1
        case (.enhancementSuccesses, .enhancementAttempt(let success)):
            if success { after += 1 }
        case (.combatPowerReached, .combatPowerChanged(let power)):
            after = max(after, power)
        case (.marketViewed, .marketViewed):
            after = max(after, condition.targetValue)
        case (.marketListed, .marketListed):
            after += 1
        case (.marketPurchased, .marketPurchased):
            after += 1
        case (.marketSold, .marketSold):
            after += 1
        case (.leaderboardViewed, .leaderboardViewed):
            after = max(after, condition.targetValue)
        case (.leaderboardRankReached, .leaderboardViewed(let rank)):
            if let rank, rank <= condition.targetValue {
                after = max(after, condition.targetValue)
            }
        case (.chatOpened, .chatOpened):
            after = max(after, condition.targetValue)
        case (.chatQuickMessageSent, .chatQuickMessageSent):
            after += 1
        case (.chatActionUsed, .chatActionUsed(let action)):
            if condition.targetId == nil || condition.targetId == action.rawValue {
                after += 1
            }
        default:
            break
        }

        after = max(0, min(after, condition.targetValue))
        guard after != before else { return false }
        progress.conditionValues[condition.id] = after
        return true
    }

    private static func syncStateConditions(
        _ state: inout QuestState,
        configs: [QuestConfig],
        gameState: GameState,
        now: Date
    ) -> Bool {
        var changed = false
        let power = InventorySystem.combatPower(gameState: gameState)
        let equippedCount = gameState.equippedItems.count
        let inventoryQualities = gameState.inventory.map(\.quality) + gameState.equippedItems.values.map(\.quality)
        let playerRank = RobotLeaderboardSystem.rankedEntries(gameState: gameState).first(where: \.isPlayer)?.rank
        let marketPurchases = gameState.marketState.tradeRecords.filter { $0.isPlayerRelated && $0.recordType == .purchase }.count
        let marketSales = gameState.marketState.tradeRecords.filter { $0.isPlayerRelated && $0.recordType == .sale }.count
        let totalEnhancementLevels = gameState.inventory.reduce(0) { $0 + $1.enhancementLevel }
            + gameState.equippedItems.values.reduce(0) { $0 + $1.enhancementLevel }

        for config in configs {
            guard var progress = state.progressById[config.id],
                  progress.status == .active || progress.status == .completed else {
                continue
            }

            for condition in config.conditions {
                let before = currentValue(for: condition, progress: progress)
                var after = before
                switch condition.type {
                case .dungeonCompleted:
                    if let targetId = condition.targetId, gameState.completedDungeonIds.contains(targetId) {
                        after = max(after, condition.targetValue)
                    }
                case .combatPowerReached:
                    after = max(after, min(power, condition.targetValue))
                case .equipmentEquipped:
                    if equippedCount > 0 {
                        after = max(after, min(condition.targetValue, equippedCount))
                    }
                case .itemQualityObtained:
                    let count = inventoryQualities.filter { qualityMeets($0, targetId: condition.targetId) }.count
                    after = max(after, min(count, condition.targetValue))
                case .leaderboardRankReached:
                    if let playerRank, playerRank <= condition.targetValue {
                        after = max(after, condition.targetValue)
                    }
                case .marketPurchased:
                    after = max(after, min(marketPurchases, condition.targetValue))
                case .marketSold:
                    after = max(after, min(marketSales, condition.targetValue))
                case .enhancementSuccesses:
                    after = max(after, min(totalEnhancementLevels, condition.targetValue))
                default:
                    break
                }

                if after != before {
                    progress.conditionValues[condition.id] = after
                    changed = true
                }
            }
            state.progressById[config.id] = progress
        }
        return changed
    }

    private static func updateCompletionAndLocks(_ state: inout QuestState, configs: [QuestConfig], now: Date) -> Bool {
        var changed = false
        for config in configs {
            guard var progress = state.progressById[config.id] else { continue }
            if progress.status == .active && isComplete(config: config, progress: progress) {
                progress.status = .completed
                progress.completedAt = now
                state.progressById[config.id] = progress
                changed = true
            }
        }

        for config in configs {
            guard var progress = state.progressById[config.id], progress.status == .locked, isUnlocked(config, state: state) else {
                continue
            }
            progress.status = .active
            progress.conditionValues = [:]
            progress.completedAt = nil
            progress.claimedAt = nil
            state.progressById[config.id] = progress
            changed = true
        }
        return changed
    }

    private static func isComplete(config: QuestConfig, progress: QuestProgress) -> Bool {
        config.conditions.allSatisfy { condition in
            currentValue(for: condition, progress: progress) >= condition.targetValue
        }
    }

    private static func currentValue(for condition: QuestCondition, progress: QuestProgress) -> Int {
        progress.conditionValues[condition.id] ?? 0
    }

    // MARK: - Rewards

    private static func canApplyRewards(_ rewards: [QuestReward], gameState: GameState) -> Bool {
        let itemCount = rewards.reduce(0) { total, reward in
            total + (reward.type == .itemTemplate ? max(1, reward.amount) : 0)
        }
        return gameState.inventory.count + itemCount <= InventorySystem.maxSlots
    }

    private static func applyRewards(_ rewards: [QuestReward], gameState: GameState) {
        for reward in rewards {
            switch reward.type {
            case .gold:
                gameState.player?.gold += reward.amount
            case .experience:
                _ = gameState.player?.addExperience(reward.amount)
            case .itemTemplate:
                guard let templateId = reward.targetId,
                      let template = ConfigLoader.shared.itemTemplates.first(where: { $0.id == templateId }) else {
                    continue
                }
                for _ in 0..<max(1, reward.amount) {
                    _ = InventorySystem.addItem(Item(template: template), to: gameState)
                }
            }
        }
    }

    // MARK: - Presentation

    private static func sortedQuestConfigs(_ configs: [QuestConfig]) -> [QuestConfig] {
        configs.sorted {
            if categoryRank($0.category) != categoryRank($1.category) {
                return categoryRank($0.category) < categoryRank($1.category)
            }
            return $0.priority < $1.priority
        }
    }

    private static func recommendationScore(config: QuestConfig, progress: QuestProgress) -> Int {
        var score = 0
        if progress.status == .completed { score += 10_000 }
        switch config.category {
        case .main: score += 4_000
        case .daily: score += 2_000
        case .achievement: score += 1_000
        case .weekly, .bounty, .event: score += 300
        }
        score += max(0, 1_000 - config.priority)
        return score
    }

    private static func categoryRank(_ category: QuestCategory) -> Int {
        switch category {
        case .main: return 0
        case .daily: return 1
        case .achievement: return 2
        case .weekly: return 3
        case .bounty: return 4
        case .event: return 5
        }
    }

    private static func qualityMeets(_ quality: ItemQuality, targetId: String?) -> Bool {
        guard let targetId, let target = ItemQuality(rawValue: targetId) else { return true }
        return qualityRank(quality) >= qualityRank(target)
    }

    private static func qualityRank(_ quality: ItemQuality) -> Int {
        switch quality {
        case .common: return 0
        case .uncommon: return 1
        case .rare: return 2
        case .epic: return 3
        case .legendary: return 4
        }
    }
}
