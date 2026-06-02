import Foundation

enum RobotLeaderboardSystem {
    static let refreshInterval: TimeInterval = 60
    static let maxOfflineSimulation: TimeInterval = 24 * 60 * 60
    static let robotCount = 120
    static let maxLogs = 140

    static func bootstrap(gameState: GameState, now: Date = Date()) {
        var state = gameState.robotLeaderboardState
        let playerPower = InventorySystem.combatPower(gameState: gameState)

        if state.robots.isEmpty {
            state = makeInitialState(playerPower: playerPower, playerLevel: gameState.player?.level ?? 1, now: now)
        }

        simulateElapsed(state: &state, gameState: gameState, now: now, forceMinute: false)
        updateRanks(state: &state, gameState: gameState, now: now)
        trimLogs(state: &state)
        gameState.robotLeaderboardState = state
        gameState.saveProgress()
    }

    static func refresh(gameState: GameState, now: Date = Date(), forceMinute: Bool = false) {
        var state = gameState.robotLeaderboardState
        if state.robots.isEmpty {
            state = makeInitialState(
                playerPower: InventorySystem.combatPower(gameState: gameState),
                playerLevel: gameState.player?.level ?? 1,
                now: now
            )
        }

        simulateElapsed(state: &state, gameState: gameState, now: now, forceMinute: forceMinute)
        updateRanks(state: &state, gameState: gameState, now: now)
        trimLogs(state: &state)
        gameState.robotLeaderboardState = state
        gameState.saveProgress()
    }

    static func rankedEntries(gameState: GameState) -> [LeaderboardEntry] {
        let playerPower = InventorySystem.combatPower(gameState: gameState)
        let player = gameState.player

        var candidates: [(id: String, name: String, title: String, profession: Profession, level: Int, power: Int, isPlayer: Bool, rankChange: Int, tag: String, highlight: String, robot: RobotAdventure?)] = []

        if let player {
            candidates.append((
                id: "player",
                name: player.name,
                title: "你",
                profession: player.profession,
                level: player.level,
                power: playerPower,
                isPlayer: true,
                rankChange: gameState.robotLeaderboardState.lastPlayerRankChange,
                tag: "YOU",
                highlight: playerHighlight(gameState: gameState),
                robot: nil
            ))
        }

        for robot in gameState.robotLeaderboardState.robots {
            candidates.append((
                id: "robot-\(robot.id)",
                name: robot.name,
                title: robot.title,
                profession: robot.profession,
                level: robot.level,
                power: combatPower(for: robot),
                isPlayer: false,
                rankChange: robot.lastRankChange,
                tag: robot.personality.kind.shortTag,
                highlight: highlight(for: robot),
                robot: robot
            ))
        }

        return candidates
            .sorted {
                if $0.power != $1.power { return $0.power > $1.power }
                if $0.level != $1.level { return $0.level > $1.level }
                return $0.name < $1.name
            }
            .enumerated()
            .map { index, item in
                LeaderboardEntry(
                    id: item.id,
                    rank: index + 1,
                    name: item.name,
                    title: item.title,
                    profession: item.profession,
                    level: item.level,
                    power: item.power,
                    isPlayer: item.isPlayer,
                    rankChange: item.rankChange,
                    tag: item.tag,
                    highlight: item.highlight,
                    robot: item.robot
                )
            }
    }

    static func playerGapText(gameState: GameState) -> String {
        let entries = rankedEntries(gameState: gameState)
        guard let playerIndex = entries.firstIndex(where: \.isPlayer) else { return "尚未入榜" }
        if playerIndex == 0 { return "你已经位列榜首" }
        let target = entries[playerIndex - 1]
        let playerPower = entries[playerIndex].power
        return "距离第 \(target.rank) 名还差 \(max(0, target.power - playerPower)) 战力"
    }

    static func combatPower(for robot: RobotAdventure) -> Int {
        let stats = baseStats(profession: robot.profession, level: robot.level)
        let equipment = equipmentTotals(for: robot)
        let attack = Double(stats.attack + equipment.attack)
        let defense = Double(stats.defense + equipment.defense)
        let hp = Double(stats.hp + equipment.hp)
        let mp = Double(stats.mp + equipment.mp)
        let critRate = min(0.45, Double(stats.agility) * 0.001 + equipment.crit)
        let equippedSlots = Double(robot.gear.count)

        let offenseScore = attack * 12
        let defenseScore = defense * 8
        let healthScore = sqrt(max(1, hp)) * 26
        let manaScore = sqrt(max(1, mp)) * 12
        let critScore = offenseScore * critRate * 0.8
        let levelScore = Double(robot.level) * 45
        let slotSetBonus = 1 + min(0.10, equippedSlots * 0.008)
        let ambitionBonus = 1 + (robot.personality.ambition - 0.5) * 0.025

        return Int((offenseScore + defenseScore + healthScore + manaScore + critScore + levelScore) * slotSetBonus * ambitionBonus)
    }

    private static func makeInitialState(playerPower: Int, playerLevel: Int, now: Date) -> RobotLeaderboardState {
        var state = RobotLeaderboardState(now: now)
        let baselinePower = max(1_600, playerPower)
        var rng = LeaderboardRandom(seed: stableHash("leaderboard-\(Int(now.timeIntervalSince1970 / 86_400))-\(baselinePower)-\(playerLevel)"))

        for index in 0..<robotCount {
            let kind = personalityKind(index: index, rng: &rng)
            let profession = Profession.allLeaderboardCases[rng.nextInt(upperBound: Profession.allLeaderboardCases.count)]
            let targetPower = initialTargetPower(rankSeed: index, baselinePower: baselinePower, rng: &rng)
            let level = initialLevel(index: index, playerLevel: playerLevel, targetPower: targetPower, rng: &rng)
            let name = makeName(index: index, rng: &rng)

            var robot = RobotAdventure(
                id: 10_000 + index,
                name: name,
                title: makeTitle(kind: kind, profession: profession, rng: &rng),
                profession: profession,
                level: level,
                experience: rng.nextInt(upperBound: max(1, PlayerData.experienceRequired(for: max(1, level)))),
                gold: max(80, level * 160 + rng.nextInt(upperBound: max(120, level * 120))),
                stamina: Double(50 + rng.nextInt(upperBound: 51)),
                dungeonIndex: initialDungeonIndex(level: level, targetPower: targetPower),
                gear: [:],
                personality: RobotPersonality.preset(kind, rng: &rng),
                lastMajorLogTime: now.addingTimeInterval(-TimeInterval(600 + rng.nextInt(upperBound: 7_200))),
                previousRank: nil,
                lastRankChange: 0,
                previousPower: 0
            )

            seedGear(for: &robot, targetPower: targetPower, rng: &rng)
            robot.previousPower = combatPower(for: robot)
            state.robots.append(robot)
        }

        state.logs = [
            RobotActivityLog(
                id: UUID(),
                time: now,
                robotId: nil,
                type: .notice,
                text: "银冠冒险者公会更新了战力榜，边境远征记录开始同步。",
                importance: 2
            )
        ]
        updateRanks(state: &state, gameState: nil, now: now)
        return state
    }

    private static func simulateElapsed(
        state: inout RobotLeaderboardState,
        gameState: GameState,
        now: Date,
        forceMinute: Bool
    ) {
        if now < state.lastSimulationTime.addingTimeInterval(-5 * 60) {
            state.suspiciousClockUntil = now.addingTimeInterval(30 * 60)
            appendLog("公会沙漏记录异常，冒险者档案暂停同步。", type: .notice, importance: 3, state: &state, now: now)
            state.lastSimulationTime = now
            return
        }

        if now.timeIntervalSince(state.lastDailyReset) >= 24 * 60 * 60 {
            state.lastDailyReset = now
            state.daySeed = Int(now.timeIntervalSince1970) % 1_000_000
            appendLog("银冠公会发布了新的边境悬赏，冒险者们重新规划了今日路线。", type: .notice, importance: 1, state: &state, now: now)
        }

        var elapsedMinutes = Int(min(max(0, now.timeIntervalSince(state.lastSimulationTime)), maxOfflineSimulation) / 60)
        if forceMinute && elapsedMinutes == 0 {
            elapsedMinutes = 1
        }
        guard elapsedMinutes > 0 else { return }

        let blocks = simulationBlocks(for: elapsedMinutes)
        var blockTime = state.lastSimulationTime
        for minutes in blocks {
            blockTime = blockTime.addingTimeInterval(TimeInterval(minutes * 60))
            simulateBlock(minutes: minutes, compressed: minutes > 1, state: &state, gameState: gameState, now: min(blockTime, now))
        }

        state.lastSimulationTime = now
        state.lastRefreshTime = now
    }

    private static func simulateBlock(
        minutes: Int,
        compressed: Bool,
        state: inout RobotLeaderboardState,
        gameState: GameState,
        now: Date
    ) {
        let bucket = Int(now.timeIntervalSince1970 / refreshInterval)
        for index in state.robots.indices {
            var robot = state.robots[index]
            var rng = LeaderboardRandom(seed: stableHash("\(state.daySeed)-\(bucket)-\(robot.id)-\(minutes)"))
            robot.stamina = min(100, robot.stamina + Double(minutes) / 5.0)

            let activity = robot.personality.activity
            let cadence = compressed ? 8.0 : 5.0
            let variance = 0.85 + rng.nextDouble() * 0.3
            let expectedActions = Double(minutes) / cadence * activity * variance
            var actions = Int(expectedActions)
            if rng.nextBool(probability: expectedActions - Double(actions)) {
                actions += 1
            }
            actions = min(actions, compressed ? 10 : 2)

            for _ in 0..<actions {
                let choice = rng.nextDouble()
                let marketChance = 0.08 + robot.personality.marketBias * 0.12
                let enhanceChance = marketChance + robot.personality.enhanceBias * 0.18

                if choice < marketChance, tryMarketPurchase(robot: &robot, gameState: gameState, state: &state, rng: &rng, now: now) {
                    continue
                }
                if choice < enhanceChance, tryEnhance(robot: &robot, state: &state, rng: &rng, now: now) {
                    continue
                }
                runDungeon(robot: &robot, state: &state, rng: &rng, now: now)
            }

            state.robots[index] = robot
        }
    }

    private static func simulationBlocks(for totalMinutes: Int) -> [Int] {
        var remaining = min(24 * 60, totalMinutes)
        var blocks: [Int] = []

        let fullMinutes = min(30, remaining)
        blocks.append(contentsOf: Array(repeating: 1, count: fullMinutes))
        remaining -= fullMinutes

        let tenMinuteArea = min(8 * 60 - 30, remaining)
        blocks.append(contentsOf: Array(repeating: 10, count: tenMinuteArea / 10))
        if tenMinuteArea % 10 > 0 { blocks.append(tenMinuteArea % 10) }
        remaining -= tenMinuteArea

        blocks.append(contentsOf: Array(repeating: 30, count: remaining / 30))
        if remaining % 30 > 0 { blocks.append(remaining % 30) }
        return blocks
    }

    private static func runDungeon(
        robot: inout RobotAdventure,
        state: inout RobotLeaderboardState,
        rng: inout LeaderboardRandom,
        now: Date
    ) {
        let dungeons = ConfigLoader.shared.dungeonConfigs
        guard !dungeons.isEmpty else { return }

        let targetIndex = chooseDungeonIndex(for: robot, rng: &rng)
        let dungeon = dungeons[targetIndex]
        let staminaCost = Double(6 + targetIndex / 4)
        guard robot.stamina >= staminaCost else { return }

        let power = combatPower(for: robot)
        let ratio = Double(power) / Double(max(1, dungeon.recommendedPower))
        let successChance = LeaderboardMath.clamp(0.18 + 0.72 / (1 + exp(-5 * (ratio - 1))), to: 0.12...0.98)
        let success = rng.nextBool(probability: successChance)
        let rewards = dungeonRewards(for: dungeon)

        if success {
            robot.stamina -= staminaCost
            robot.gold += rewards.gold
            let levels = addExperience(rewards.exp, to: &robot)
            if targetIndex > robot.dungeonIndex {
                robot.dungeonIndex = targetIndex
            }

            for level in levels {
                appendRobotLog("\(robot.name) 晋升到 Lv.\(level)，战力档案已更新。", robot: robot, type: .levelUp, importance: 2, state: &state, now: now, rng: &rng)
            }

            if rng.nextBool(probability: 0.18 + robot.personality.activity * 0.06) {
                appendRobotLog("\(robot.name) 通关了 \(dungeon.name)。", robot: robot, type: .dungeonClear, importance: targetIndex >= 8 ? 1 : 0, state: &state, now: now, rng: &rng)
            }

            for item in rewards.loot {
                let equipped = tryEquip(item, to: &robot)
                if item.quality == .epic || item.quality == .legendary {
                    appendRobotLog("\(robot.name) 获得\(item.quality.displayName)装备：\(item.displayName)。", robot: robot, type: .loot, importance: item.quality == .legendary ? 3 : 2, state: &state, now: now, rng: &rng)
                }
                if equipped {
                    appendRobotLog("\(robot.name) 换上了 \(item.displayName)。", robot: robot, type: .equip, importance: item.quality == .common ? 0 : 1, state: &state, now: now, rng: &rng)
                }
            }
        } else {
            robot.stamina -= staminaCost * 0.5
            robot.gold += max(1, rewards.gold / 10)
            _ = addExperience(max(1, rewards.exp / 5), to: &robot)

            if rng.nextBool(probability: 0.16 + robot.personality.risk * 0.12) {
                appendRobotLog("\(robot.name) 挑战 \(dungeon.name) 失败，暂时撤回营地。", robot: robot, type: .dungeonFail, importance: targetIndex >= 10 ? 1 : 0, state: &state, now: now, rng: &rng)
            }
        }
    }

    private static func tryEnhance(
        robot: inout RobotAdventure,
        state: inout RobotLeaderboardState,
        rng: inout LeaderboardRandom,
        now: Date
    ) -> Bool {
        let candidates = robot.gear
            .filter { $0.value.enhancementLevel < EnhancementRule.maxLevel && $0.value.nextEnhancementCost > 0 }
            .sorted { gearScore($0.value, profession: robot.profession) > gearScore($1.value, profession: robot.profession) }

        guard let target = candidates.prefix(3).randomElement() else { return false }
        var item = target.value
        let cost = max(1, Int(Double(item.nextEnhancementCost) * 0.75))
        guard robot.gold >= cost else { return false }

        robot.gold -= cost
        let targetLevel = item.enhancementLevel + 1
        let successRate = EnhancementRule.successRate(forTargetLevel: targetLevel, luck: item.enhancementLuck)

        if rng.nextBool(probability: successRate) {
            item.enhancementLevel = targetLevel
            item.enhancementLuck = 0
            robot.gear[target.key] = item
            let importance = targetLevel >= 10 ? 2 : (targetLevel >= 6 ? 1 : 0)
            appendRobotLog("\(robot.name) 将 \(item.compactDisplayName) 强化到了 +\(targetLevel)。", robot: robot, type: .enhanceSuccess, importance: importance, state: &state, now: now, rng: &rng)
        } else {
            item.enhancementLuck += 1
            let downgrade = EnhancementRule.downgradeAmount(forTargetLevel: targetLevel)
            if downgrade > 0 {
                item.enhancementLevel = max(3, item.enhancementLevel - downgrade)
            }
            robot.gear[target.key] = item
            if targetLevel >= 8 || rng.nextBool(probability: 0.28) {
                appendRobotLog("\(robot.name) 强化 \(item.compactDisplayName) 失败，工坊记录了这次尝试。", robot: robot, type: .enhanceFail, importance: targetLevel >= 10 ? 1 : 0, state: &state, now: now, rng: &rng)
            }
        }
        return true
    }

    private static func tryMarketPurchase(
        robot: inout RobotAdventure,
        gameState: GameState,
        state: inout RobotLeaderboardState,
        rng: inout LeaderboardRandom,
        now: Date
    ) -> Bool {
        let listings = gameState.marketState.robotListings.enumerated().filter { _, listing in
            listing.status == .listed
                && listing.item.requiredLevel <= robot.level + 3
                && listing.listPrice <= Int(Double(robot.gold) * (0.45 + robot.personality.marketBias * 0.35))
                && wouldEquip(listing.item, robot: robot)
        }
        guard !listings.isEmpty else { return false }

        let picked = listings[min(rng.nextInt(upperBound: min(8, listings.count)), listings.count - 1)]
        var item = picked.element.item
        item.marketOrigin = .robotMarket

        robot.gold -= picked.element.listPrice
        gameState.marketState.robotListings.remove(at: picked.offset)
        let equipped = tryEquip(item, to: &robot)

        gameState.marketState.tradeRecords.insert(
            MarketTradeRecord(
                id: UUID(),
                time: now,
                buyerName: robot.name,
                sellerName: picked.element.sellerName,
                itemName: item.displayName,
                price: picked.element.listPrice,
                isPlayerRelated: false,
                recordType: .npcTrade
            ),
            at: 0
        )
        gameState.marketState.activities.insert(
            MarketActivity(id: UUID(), time: now, text: "\(robot.name) 在商会买下了 \(item.displayName)", isImportant: item.quality == .epic || item.quality == .legendary),
            at: 0
        )
        if gameState.marketState.tradeRecords.count > MarketSystem.maxTradeRecords {
            gameState.marketState.tradeRecords = Array(gameState.marketState.tradeRecords.prefix(MarketSystem.maxTradeRecords))
        }
        if gameState.marketState.activities.count > MarketSystem.maxActivities {
            gameState.marketState.activities = Array(gameState.marketState.activities.prefix(MarketSystem.maxActivities))
        }

        if equipped {
            appendRobotLog("\(robot.name) 在商会买下并装备了 \(item.displayName)。", robot: robot, type: .marketBuy, importance: item.quality == .legendary ? 3 : 1, state: &state, now: now, rng: &rng)
        }
        return true
    }

    private static func updateRanks(state: inout RobotLeaderboardState, gameState: GameState?, now: Date) {
        let playerPower = gameState.map { InventorySystem.combatPower(gameState: $0) } ?? state.playerBestPower
        state.playerBestPower = max(state.playerBestPower, playerPower)

        var rows: [(id: String, power: Int, level: Int)] = state.robots.map { robot in
            ("robot-\(robot.id)", combatPower(for: robot), robot.level)
        }
        if gameState?.player != nil || playerPower > 0 {
            rows.append(("player", playerPower, gameState?.player?.level ?? 1))
        }

        let sorted = rows.sorted {
            if $0.power != $1.power { return $0.power > $1.power }
            if $0.level != $1.level { return $0.level > $1.level }
            return $0.id < $1.id
        }

        var rankById: [String: Int] = [:]
        for (index, row) in sorted.enumerated() {
            rankById[row.id] = index + 1
        }

        for index in state.robots.indices {
            let id = "robot-\(state.robots[index].id)"
            guard let rank = rankById[id] else { continue }
            let previous = state.robots[index].previousRank
            let change = previous.map { $0 - rank } ?? 0
            state.robots[index].lastRankChange = change
            state.robots[index].previousRank = rank
            state.robots[index].previousPower = combatPower(for: state.robots[index])

            if let previous, abs(change) >= 4, rank <= 60 {
                let direction = change > 0 ? "上升" : "下降"
                let importance = rank <= 20 ? 2 : 1
                var rng = LeaderboardRandom(seed: stableHash("\(state.daySeed)-rank-\(state.robots[index].id)-\(rank)-\(previous)"))
                appendRobotLog("\(state.robots[index].name) 排名\(direction)到第 \(rank) 名。", robot: state.robots[index], type: .rankMove, importance: importance, state: &state, now: now, rng: &rng)
            }
        }

        if let playerRank = rankById["player"] {
            let change = state.previousPlayerRank.map { $0 - playerRank } ?? 0
            state.lastPlayerRankChange = change
            state.previousPlayerRank = playerRank
        }
    }

    private static func chooseDungeonIndex(for robot: RobotAdventure, rng: inout LeaderboardRandom) -> Int {
        let dungeons = ConfigLoader.shared.dungeonConfigs
        guard !dungeons.isEmpty else { return 0 }

        let power = combatPower(for: robot)
        let levelAllowance = robot.level + (robot.personality.risk > 0.68 ? 2 : 0)
        let maxByLevel = dungeons.lastIndex { dungeon in
            dungeon.recommendedLevel <= levelAllowance
        } ?? 0

        var target = min(robot.dungeonIndex, maxByLevel)
        if target + 1 < dungeons.count {
            let next = dungeons[target + 1]
            if next.recommendedLevel <= levelAllowance && Double(power) >= Double(next.recommendedPower) * (0.92 - robot.personality.risk * 0.12) {
                target += 1
            }
        }
        if robot.personality.risk > 0.74, rng.nextBool(probability: 0.22), target + 1 <= maxByLevel {
            target += 1
        }
        if rng.nextBool(probability: 0.18) {
            target = max(0, target - rng.nextInt(upperBound: 2))
        }
        return min(max(0, target), dungeons.count - 1)
    }

    private static func dungeonRewards(for dungeon: DungeonConfig) -> (exp: Int, gold: Int, loot: [Item]) {
        let monsters = ConfigLoader.shared.monsterConfigs
        var exp = 0
        var gold = 0
        var loot: [Item] = []

        for room in dungeon.rooms {
            for roomMonster in room.monsters {
                guard let monster = monsters.first(where: { $0.id == roomMonster.monsterId }) else { continue }
                for _ in 0..<roomMonster.count {
                    exp += monster.expReward
                    gold += monster.goldReward
                    loot.append(contentsOf: LootSystem.generateLoot(from: monster.lootTable))
                }
            }
        }
        return (exp, gold, loot)
    }

    @discardableResult
    private static func addExperience(_ amount: Int, to robot: inout RobotAdventure) -> [Int] {
        guard amount > 0, robot.level < PlayerData.maxLevel else { return [] }
        robot.experience += amount
        var levels: [Int] = []

        while robot.level < PlayerData.maxLevel {
            let needed = PlayerData.experienceRequired(for: robot.level)
            guard needed > 0, robot.experience >= needed else { break }
            robot.experience -= needed
            robot.level += 1
            levels.append(robot.level)
        }

        if robot.level >= PlayerData.maxLevel {
            robot.experience = 0
        }
        return levels
    }

    @discardableResult
    private static func tryEquip(_ item: Item, to robot: inout RobotAdventure) -> Bool {
        guard item.type.equipSlot != nil, item.requiredLevel <= robot.level else { return false }
        let slotKey = bestSlotKey(for: item, robot: robot)
        let current = robot.gear[slotKey]
        let threshold = equipThreshold(for: robot.personality.kind)
        let newScore = gearScore(item, profession: robot.profession)
        let currentScore = current.map { gearScore($0, profession: robot.profession) } ?? 0

        guard current == nil || newScore > currentScore * threshold else { return false }
        robot.gear[slotKey] = item
        return true
    }

    private static func wouldEquip(_ item: Item, robot: RobotAdventure) -> Bool {
        guard item.type.equipSlot != nil, item.requiredLevel <= robot.level + 3 else { return false }
        let slotKey = bestSlotKey(for: item, robot: robot)
        let current = robot.gear[slotKey]
        let threshold = equipThreshold(for: robot.personality.kind) + 0.02
        let newScore = gearScore(item, profession: robot.profession)
        let currentScore = current.map { gearScore($0, profession: robot.profession) } ?? 0
        return current == nil || newScore > currentScore * threshold
    }

    private static func bestSlotKey(for item: Item, robot: RobotAdventure) -> String {
        if item.type == .ring {
            let ring1 = robot.gear[EquipSlot.ring1.rawValue].map { gearScore($0, profession: robot.profession) } ?? 0
            let ring2 = robot.gear[EquipSlot.ring2.rawValue].map { gearScore($0, profession: robot.profession) } ?? 0
            return ring1 <= ring2 ? EquipSlot.ring1.rawValue : EquipSlot.ring2.rawValue
        }
        return item.type.equipSlot?.rawValue ?? item.type.rawValue
    }

    private static func equipThreshold(for kind: RobotPersonalityKind) -> Double {
        switch kind {
        case .climber, .gambler: return 1.01
        case .trader: return 1.025
        case .grinder: return 1.04
        case .casual: return 1.08
        }
    }

    private static func gearScore(_ item: Item, profession: Profession) -> Double {
        let attackWeight: Double
        let defenseWeight: Double
        let hpWeight: Double
        let mpWeight: Double
        let critWeight: Double

        switch profession {
        case .warrior:
            attackWeight = 2.1; defenseWeight = 2.25; hpWeight = 0.34; mpWeight = 0.12; critWeight = 120
        case .ranger:
            attackWeight = 2.35; defenseWeight = 1.65; hpWeight = 0.24; mpWeight = 0.12; critWeight = 170
        case .mage:
            attackWeight = 2.18; defenseWeight = 1.45; hpWeight = 0.22; mpWeight = 0.28; critWeight = 145
        }

        let qualityMultiplier: Double
        switch item.quality {
        case .common: qualityMultiplier = 1.0
        case .uncommon: qualityMultiplier = 1.08
        case .rare: qualityMultiplier = 1.18
        case .epic: qualityMultiplier = 1.35
        case .legendary: qualityMultiplier = 1.6
        }

        let raw = Double(item.enhancedAttackBonus) * attackWeight
            + Double(item.enhancedDefenseBonus) * defenseWeight
            + Double(item.enhancedHPBonus) * hpWeight
            + Double(item.enhancedMPBonus) * mpWeight
            + item.enhancedCritBonus * critWeight
            + Double(item.requiredLevel) * 4

        return raw * qualityMultiplier
    }

    private static func equipmentTotals(for robot: RobotAdventure) -> (attack: Int, defense: Int, hp: Int, mp: Int, crit: Double) {
        robot.gear.values.reduce((0, 0, 0, 0, 0.0)) { total, item in
            (
                total.0 + item.enhancedAttackBonus,
                total.1 + item.enhancedDefenseBonus,
                total.2 + item.enhancedHPBonus,
                total.3 + item.enhancedMPBonus,
                total.4 + item.enhancedCritBonus
            )
        }
    }

    private static func baseStats(profession: Profession, level: Int) -> (attack: Int, defense: Int, hp: Int, mp: Int, agility: Int) {
        let growth = max(0, level - 1)
        var strength: Int
        var agility: Int
        var constitution: Int
        var intelligence: Int

        switch profession {
        case .warrior:
            strength = 10 + growth * 2
            agility = 5 + growth
            constitution = 8 + growth * 2
            intelligence = 3 + growth
        case .ranger:
            strength = 6 + growth * 2
            agility = 10 + growth * 2
            constitution = 5 + growth
            intelligence = 4 + growth
        case .mage:
            strength = 3 + growth
            agility = 4 + growth
            constitution = 4 + growth
            intelligence = 10 + growth * 2
        }

        let attack = Int(Double(strength) * 2.0 + Double(level) * 3.0)
        let defense = Int(Double(constitution) * 2.0 + Double(level) * 1.5)
        let hp = Int(100 + Double(constitution) * 10 * (1 + Double(level) * 0.1))
        let mp = Int(50 + Double(intelligence) * 8 * (1 + Double(level) * 0.08))
        return (attack, defense, hp, mp, agility)
    }

    private static func seedGear(for robot: inout RobotAdventure, targetPower: Int, rng: inout LeaderboardRandom) {
        let preferredSlots: [ItemType] = [.weapon, .helmet, .armor, .legs, .boots, .gloves, .necklace, .ring, .ring]
        for slot in preferredSlots {
            guard let item = makeItem(type: slot, level: robot.level, targetPower: targetPower, rng: &rng) else { continue }
            _ = tryEquip(item, to: &robot)
        }

        var safety = 0
        while combatPower(for: robot) < Int(Double(targetPower) * 0.72), safety < 10 {
            safety += 1
            guard let type = preferredSlots.randomElement(),
                  let item = makeItem(type: type, level: min(PlayerData.maxLevel, robot.level + 2), targetPower: targetPower, rng: &rng) else {
                continue
            }
            _ = tryEquip(item, to: &robot)
            if safety % 3 == 0 && robot.level < PlayerData.maxLevel {
                robot.level += 1
            }
        }
    }

    private static func makeItem(type: ItemType, level: Int, targetPower: Int, rng: inout LeaderboardRandom) -> Item? {
        let templates = ConfigLoader.shared.itemTemplates.filter {
            $0.type == type
                && $0.requiredLevel <= max(1, level)
                && $0.requiredLevel >= max(1, level - 10)
        }
        guard !templates.isEmpty else { return nil }

        let quality = preferredQuality(targetPower: targetPower, level: level, rng: &rng)
        let qualityPool = templates.filter { $0.quality == quality }
        let pool = qualityPool.isEmpty ? templates : qualityPool
        var item = Item(template: pool[rng.nextInt(upperBound: pool.count)])
        item.enhancementLevel = initialEnhancement(for: item.quality, level: level, rng: &rng)
        return item
    }

    private static func preferredQuality(targetPower: Int, level: Int, rng: inout LeaderboardRandom) -> ItemQuality {
        let roll = rng.nextDouble()
        let highBand = targetPower > max(4_000, level * 520)
        if highBand {
            if roll < 0.16 { return .legendary }
            if roll < 0.46 { return .epic }
            if roll < 0.82 { return .rare }
            return .uncommon
        }
        if roll < 0.04 { return .legendary }
        if roll < 0.16 { return .epic }
        if roll < 0.48 { return .rare }
        if roll < 0.82 { return .uncommon }
        return .common
    }

    private static func initialEnhancement(for quality: ItemQuality, level: Int, rng: inout LeaderboardRandom) -> Int {
        let roll = rng.nextDouble()
        let levelBonus = min(4, level / 12)
        switch quality {
        case .common:
            return roll < 0.12 ? 1 : 0
        case .uncommon:
            return roll < 0.2 ? 1 + min(1, levelBonus) : 0
        case .rare:
            return roll < 0.18 ? 3 + min(2, levelBonus) : (roll < 0.52 ? 1 + min(1, levelBonus) : 0)
        case .epic:
            return roll < 0.16 ? 6 + min(3, levelBonus) : (roll < 0.54 ? 3 + min(2, levelBonus) : 1)
        case .legendary:
            return roll < 0.2 ? 8 + min(4, levelBonus) : (roll < 0.62 ? 5 + min(3, levelBonus) : 2)
        }
    }

    private static func initialTargetPower(rankSeed: Int, baselinePower: Int, rng: inout LeaderboardRandom) -> Int {
        let range: ClosedRange<Double>
        switch rankSeed {
        case 0:
            range = 1.18...1.32
        case 1..<10:
            range = 0.94...1.18
        case 10..<50:
            range = 0.58...0.98
        case 50..<100:
            range = 0.28...0.62
        default:
            range = 0.18...0.50
        }
        let multiplier = range.lowerBound + rng.nextDouble() * (range.upperBound - range.lowerBound)
        return max(900, Int(Double(baselinePower) * multiplier))
    }

    private static func initialLevel(index: Int, playerLevel: Int, targetPower: Int, rng: inout LeaderboardRandom) -> Int {
        let offset: Int
        switch index {
        case 0..<5: offset = 2 + rng.nextInt(upperBound: 4)
        case 5..<20: offset = rng.nextInt(upperBound: 4)
        case 20..<70: offset = -rng.nextInt(upperBound: 5)
        default: offset = -rng.nextInt(upperBound: 9)
        }
        let powerHint = max(1, Int(Double(targetPower).squareRoot() / 8.0))
        return min(PlayerData.maxLevel, max(1, max(playerLevel + offset, powerHint)))
    }

    private static func initialDungeonIndex(level: Int, targetPower: Int) -> Int {
        let dungeons = ConfigLoader.shared.dungeonConfigs
        guard !dungeons.isEmpty else { return 0 }
        return dungeons.lastIndex { dungeon in
            dungeon.recommendedLevel <= level && dungeon.recommendedPower <= Int(Double(targetPower) * 1.08)
        } ?? 0
    }

    private static func personalityKind(index: Int, rng: inout LeaderboardRandom) -> RobotPersonalityKind {
        if index < 8 { return .climber }
        let roll = rng.nextDouble()
        if roll < 0.10 { return .climber }
        if roll < 0.45 { return .grinder }
        if roll < 0.65 { return .trader }
        if roll < 0.82 { return .gambler }
        return .casual
    }

    private static func makeName(index: Int, rng: inout LeaderboardRandom) -> String {
        let prefixes = ["艾德温", "薇拉", "罗兰", "伊森", "玛洛", "赛琳", "雷恩", "奥林", "洛克", "米娅", "卡修", "露娜", "贝伦", "艾什", "诺尔", "凯恩", "温特", "莉塔", "安妮", "托比"]
        let suffixes = ["银弦", "晨刃", "灰斗篷", "白石", "黑杉", "烛火", "霜脊", "月湾", "赤铜", "蓝狮", "夜雨", "星砂", "碎盾", "风息", "渡鸦", "旧港", "暮钟", "雪松", "鹰眼", "北桥"]
        let first = prefixes[(index + rng.nextInt(upperBound: prefixes.count)) % prefixes.count]
        let second = suffixes[rng.nextInt(upperBound: suffixes.count)]
        return "\(first)·\(second)"
    }

    private static func makeTitle(kind: RobotPersonalityKind, profession: Profession, rng: inout LeaderboardRandom) -> String {
        let professionTitle: String
        switch profession {
        case .warrior: professionTitle = ["剑誓骑士", "盾卫", "破阵者"][rng.nextInt(upperBound: 3)]
        case .ranger: professionTitle = ["巡林客", "鹰眼射手", "风行者"][rng.nextInt(upperBound: 3)]
        case .mage: professionTitle = ["秘法师", "白塔学徒", "星辉术士"][rng.nextInt(upperBound: 3)]
        }
        return "\(kind.displayName) · \(professionTitle)"
    }

    private static func highlight(for robot: RobotAdventure) -> String {
        if let item = robot.gear.values.max(by: { gearScore($0, profession: robot.profession) < gearScore($1, profession: robot.profession) }) {
            return "\(item.displayName)"
        }
        let dungeons = ConfigLoader.shared.dungeonConfigs
        if dungeons.indices.contains(robot.dungeonIndex) {
            return "推进至 \(dungeons[robot.dungeonIndex].name)"
        }
        return "正在整备远征"
    }

    private static func playerHighlight(gameState: GameState) -> String {
        if let item = gameState.equippedItems.values.max(by: { $0.powerScore < $1.powerScore }) {
            return item.displayName
        }
        return "等待新的战利品"
    }

    private static func appendRobotLog(
        _ text: String,
        robot: RobotAdventure,
        type: RobotLogType,
        importance: Int,
        state: inout RobotLeaderboardState,
        now: Date,
        rng: inout LeaderboardRandom
    ) {
        let recentSameRobot = state.logs.prefix(8).contains { log in
            log.robotId == robot.id && now.timeIntervalSince(log.time) < 120
        }
        if recentSameRobot && importance < 2 { return }

        let probability = importance >= 2 ? 1.0 : (0.2 + Double(importance) * 0.28 + robot.personality.ambition * 0.18)
        guard rng.nextBool(probability: probability) else { return }
        appendLog(text, robotId: robot.id, type: type, importance: importance, state: &state, now: now)
    }

    private static func appendLog(
        _ text: String,
        robotId: Int? = nil,
        type: RobotLogType,
        importance: Int,
        state: inout RobotLeaderboardState,
        now: Date
    ) {
        state.logs.insert(
            RobotActivityLog(id: UUID(), time: now, robotId: robotId, type: type, text: text, importance: importance),
            at: 0
        )
        trimLogs(state: &state)
    }

    private static func trimLogs(state: inout RobotLeaderboardState) {
        if state.logs.count > maxLogs {
            state.logs = Array(state.logs.prefix(maxLogs))
        }
    }

    private static func stableHash(_ string: String) -> UInt64 {
        var hash: UInt64 = 1_469_598_103_934_665_603
        for byte in string.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 1_099_511_628_211
        }
        return hash
    }
}

private extension Profession {
    static var allLeaderboardCases: [Profession] {
        [.warrior, .ranger, .mage]
    }
}
