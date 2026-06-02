import Foundation

enum WorldChatSystem {
    static let channelName = "公会传讯水晶"
    static let channelSubtitle = "银冠公会书记记录各地冒险者的传闻与留言"
    static let maxMessages = 180
    static let playerCooldown: TimeInterval = 4

    private static let generationIntervalRange: ClosedRange<TimeInterval> = 12...28
    private static let templateCooldown: TimeInterval = 105
    private static let maxImportedEventsPerTick = 4

    static let quickPhrases = [
        "有人卖法杖吗？",
        "强化又失败了。",
        "今天哪个副本适合刷？",
        "市场有便宜装备吗？",
        "排行榜卷起来了。",
        "有人刷到紫装吗？"
    ]

    static let robots: [ChatRobotProfile] = [
        ChatRobotProfile(
            id: "hotblood_warrior_ren", name: "铁靴雷恩", race: "人类", profession: "战士",
            level: 18, power: 6200, rank: 37, personality: "热血战士",
            voiceStyle: "短句、直接、喜欢挑战", activeWindow: "黄昏到深夜", chatFrequency: 0.85,
            focusSystems: ["dungeon", "leaderboard", "enhancement"], favoriteTopics: ["Boss", "战力", "巨剑"],
            dislikedTopics: ["拖价", "绕路"], tradePreference: "偏好武器和重甲", dungeonPreference: "黑石要塞",
            leaderboardGoal: "冲进前二十", likesShowOff: true, likesComplaining: false, likesBuying: false
        ),
        ChatRobotProfile(
            id: "sharp_mage_mia", name: "旅法师米娅", race: "精灵", profession: "法师",
            level: 21, power: 7100, rank: 26, personality: "毒舌法师",
            voiceStyle: "冷静、挑剔、会吐槽词缀", activeWindow: "午后到午夜", chatFrequency: 0.78,
            focusSystems: ["loot", "market", "dungeon"], favoriteTopics: ["法杖", "暴击", "冰霜洞穴"],
            dislikedTopics: ["无脑堆攻击"], tradePreference: "找法杖、戒指、项链", dungeonPreference: "冰霜洞穴",
            leaderboardGoal: "稳定前三十", likesShowOff: false, likesComplaining: true, likesBuying: true
        ),
        ChatRobotProfile(
            id: "steady_knight_leo", name: "寒霜骑士雷欧", race: "人类", profession: "骑士",
            level: 30, power: 11800, rank: 9, personality: "沉稳骑士",
            voiceStyle: "克制、可靠、像公会前辈", activeWindow: "清晨和夜晚", chatFrequency: 0.52,
            focusSystems: ["leaderboard", "dungeon"], favoriteTopics: ["防具", "队形", "排名"],
            dislikedTopics: ["赌气强化"], tradePreference: "买高防护甲", dungeonPreference: "深渊裂隙",
            leaderboardGoal: "守住前十", likesShowOff: false, likesComplaining: false, likesBuying: false
        ),
        ChatRobotProfile(
            id: "showoff_ranger_erin", name: "银月游侠艾琳", race: "精灵", profession: "游侠",
            level: 24, power: 8800, rank: 18, personality: "爱炫耀的游侠",
            voiceStyle: "轻快、得意、喜欢晒掉落", activeWindow: "全天零散", chatFrequency: 0.92,
            focusSystems: ["loot", "market"], favoriteTopics: ["长弓", "暴击", "稀有掉落"],
            dislikedTopics: ["慢吞吞的重甲"], tradePreference: "低价收饰品", dungeonPreference: "幽暗矿洞",
            leaderboardGoal: "追上前十", likesShowOff: true, likesComplaining: false, likesBuying: true
        ),
        ChatRobotProfile(
            id: "bargain_merchant_toby", name: "海盐商人托比", race: "半身人", profession: "商人",
            level: 16, power: 4100, rank: 61, personality: "喜欢捡漏的商人",
            voiceStyle: "精明、谨慎、总在看价格", activeWindow: "白天到傍晚", chatFrequency: 0.9,
            focusSystems: ["market"], favoriteTopics: ["低价货", "求购", "商会"],
            dislikedTopics: ["收藏价"], tradePreference: "倒卖热门部位", dungeonPreference: "不常刷本",
            leaderboardGoal: "能入榜就行", likesShowOff: false, likesComplaining: true, likesBuying: true
        ),
        ChatRobotProfile(
            id: "dwarf_smith_borin", name: "赤铜铁匠博林", race: "矮人", profession: "铁匠",
            level: 19, power: 5600, rank: 45, personality: "强化上头的矮人铁匠",
            voiceStyle: "粗犷、固执、总替铁锤说话", activeWindow: "深夜", chatFrequency: 0.68,
            focusSystems: ["enhancement", "market"], favoriteTopics: ["强化", "矿石", "护手"],
            dislikedTopics: ["怪铁匠"], tradePreference: "收强化过的装备", dungeonPreference: "幽暗矿洞",
            leaderboardGoal: "靠强化追排名", likesShowOff: true, likesComplaining: true, likesBuying: true
        ),
        ChatRobotProfile(
            id: "lucky_wanderer_veil", name: "星砂旅人薇拉", race: "人类", profession: "流浪者",
            level: 14, power: 3900, rank: 74, personality: "欧皇流浪者",
            voiceStyle: "轻松、惊喜、经常说自己刚捡到", activeWindow: "随机", chatFrequency: 0.64,
            focusSystems: ["loot", "event"], favoriteTopics: ["传闻", "掉落", "饰品"],
            dislikedTopics: ["保底"], tradePreference: "随缘上架稀有货", dungeonPreference: "雾林小径",
            leaderboardGoal: "不刻意冲榜", likesShowOff: true, likesComplaining: false, likesBuying: false
        ),
        ChatRobotProfile(
            id: "unlucky_merc_kai", name: "铜铃佣兵凯", race: "人类", profession: "佣兵",
            level: 17, power: 4800, rank: 57, personality: "非酋佣兵",
            voiceStyle: "自嘲、疲惫、但不恶意", activeWindow: "晚上", chatFrequency: 0.75,
            focusSystems: ["enhancement", "dungeon"], favoriteTopics: ["失败", "材料", "补给"],
            dislikedTopics: ["晒传说装备"], tradePreference: "买便宜防具", dungeonPreference: "荒原营地",
            leaderboardGoal: "别掉出前六十", likesShowOff: false, likesComplaining: true, likesBuying: true
        ),
        ChatRobotProfile(
            id: "rookie_apprentice_nia", name: "蓝袍学徒妮娅", race: "人类", profession: "见习法师",
            level: 7, power: 1700, rank: 108, personality: "新手冒险者",
            voiceStyle: "礼貌、好奇、问题多", activeWindow: "傍晚", chatFrequency: 0.55,
            focusSystems: ["dungeon", "market"], favoriteTopics: ["新手副本", "便宜装备", "法袍"],
            dislikedTopics: ["高价货"], tradePreference: "求购入门法袍", dungeonPreference: "旧矿道",
            leaderboardGoal: "先上榜", likesShowOff: false, likesComplaining: false, likesBuying: true
        ),
        ChatRobotProfile(
            id: "top10_cold_ashen", name: "灰烬伯爵卡修", race: "人类", profession: "剑士",
            level: 34, power: 14200, rank: 5, personality: "高冷排行榜前十",
            voiceStyle: "少言、压迫感、只谈结果", activeWindow: "深夜和清晨", chatFrequency: 0.35,
            focusSystems: ["leaderboard", "enhancement"], favoriteTopics: ["前十", "战力", "传说装备"],
            dislikedTopics: ["借口"], tradePreference: "只看极品", dungeonPreference: "深渊裂隙",
            leaderboardGoal: "守住前五", likesShowOff: false, likesComplaining: false, likesBuying: false
        ),
        ChatRobotProfile(
            id: "bard_winter", name: "雪松吟游者温特", race: "半精灵", profession: "吟游诗人",
            level: 20, power: 5000, rank: 50, personality: "喜欢讲传闻的吟游诗人",
            voiceStyle: "带画面感、会把消息说成传闻", activeWindow: "酒馆热闹时", chatFrequency: 0.82,
            focusSystems: ["event", "dungeon"], favoriteTopics: ["传闻", "Boss", "区域事件"],
            dislikedTopics: ["干巴巴数字"], tradePreference: "收故事比收装备多", dungeonPreference: "幽暗矿洞",
            leaderboardGoal: "保持故事素材", likesShowOff: false, likesComplaining: false, likesBuying: false
        ),
        ChatRobotProfile(
            id: "blackmarket_orin", name: "暗巷鉴定师奥林", race: "矮人", profession: "情报贩子",
            level: 26, power: 7600, rank: 24, personality: "黑市情报贩子",
            voiceStyle: "低声、隐晦、只给模糊线索", activeWindow: "午夜", chatFrequency: 0.58,
            focusSystems: ["market", "event"], favoriteTopics: ["黑市", "稀有商品", "饰品"],
            dislikedTopics: ["问得太细"], tradePreference: "只碰稀有饰品", dungeonPreference: "不公开",
            leaderboardGoal: "不惹眼", likesShowOff: false, likesComplaining: false, likesBuying: true
        )
    ]

    @discardableResult
    static func bootstrap(gameState: GameState, now: Date = Date()) -> Bool {
        var state = gameState.chatState
        var changed = false

        if state.messages.isEmpty {
            seedOpeningMessages(state: &state, gameState: gameState, now: now)
            changed = true
        }

        changed = importSystemEvents(state: &state, gameState: gameState, now: now, limit: maxImportedEventsPerTick) || changed
        changed = addOfflineSummaryIfNeeded(state: &state, gameState: gameState, now: now) || changed

        if changed {
            gameState.chatState = state
            gameState.saveProgress()
        }
        return changed
    }

    static func tick(gameState: GameState, now: Date = Date()) {
        var state = gameState.chatState
        if state.messages.isEmpty {
            seedOpeningMessages(state: &state, gameState: gameState, now: now)
        }

        _ = importSystemEvents(state: &state, gameState: gameState, now: now, limit: maxImportedEventsPerTick)

        if !state.isMuted && now.timeIntervalSince(state.lastGenerateTime) >= nextInterval(for: state, now: now) {
            let count = Int.random(in: 1...2)
            for offset in 0..<count {
                if let message = makeAmbientMessage(state: &state, gameState: gameState, now: now.addingTimeInterval(TimeInterval(offset))) {
                    append(message, state: &state)
                }
            }
            if Bool.random(probability: 0.18), let reply = makeCrossTalkMessage(state: &state, gameState: gameState, now: now.addingTimeInterval(2)) {
                append(reply, state: &state)
            }
            state.lastGenerateTime = now
        }

        gameState.chatState = state
        gameState.saveProgress()
    }

    static func markOpened(gameState: GameState, now: Date = Date()) {
        var state = gameState.chatState
        state.lastOpenedTime = now
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func hasUnread(_ state: WorldChatState) -> Bool {
        guard let latest = state.messages.last?.time else { return false }
        return latest > state.lastOpenedTime
    }

    static func toggleMuted(gameState: GameState) {
        var state = gameState.chatState
        state.isMuted.toggle()
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func clearMessages(gameState: GameState, now: Date = Date()) {
        var state = gameState.chatState
        state.messages = []
        seedOpeningMessages(state: &state, gameState: gameState, now: now)
        state.lastOpenedTime = now
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func sendPlayerMessage(_ rawText: String, gameState: GameState, now: Date = Date()) -> WorldChatSendResult {
        var state = gameState.chatState
        let text = sanitizedPlayerText(rawText)
        guard !text.isEmpty else {
            return WorldChatSendResult(success: false, message: "公会书记没有记录空白留言", replies: [])
        }

        if let last = state.lastPlayerMessageTime, now.timeIntervalSince(last) < playerCooldown {
            return WorldChatSendResult(success: false, message: "传讯水晶还在冷却，稍等片刻", replies: [])
        }

        guard isSafePlayerText(text) else {
            return WorldChatSendResult(success: false, message: "公会书记没有记录这句话", replies: [])
        }

        let playerMessage = WorldChatMessage(
            id: UUID(),
            time: now,
            channelId: "guild_crystal",
            senderId: "player",
            senderName: gameState.player?.name ?? "你",
            senderSubtitle: "本地冒险者留言",
            senderIcon: "person.fill",
            kind: .player,
            text: text,
            action: .none,
            referenceName: nil,
            isPlayer: true,
            importance: 1
        )
        append(playerMessage, state: &state)
        state.lastPlayerMessageTime = now

        let replies = makeKeywordReplies(for: text, state: &state, gameState: gameState, now: now)
        gameState.chatState = state
        gameState.saveProgress()
        return WorldChatSendResult(success: true, message: "留言已送入传讯水晶", replies: replies)
    }

    static func appendScheduledReply(_ reply: WorldChatMessage, gameState: GameState) {
        var state = gameState.chatState
        append(reply, state: &state)
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func recordMarketListing(_ listing: MarketListing, gameState: GameState, now: Date = Date()) {
        var state = gameState.chatState
        let ratio = Double(listing.listPrice) / Double(max(1, listing.baseValue))
        let text: String
        if listing.item.quality == .epic || listing.item.quality == .legendary {
            text = "\(listing.sellerName) 把【\(listing.item.displayName)】挂上了商会看板，柜台旁已经有人停步。"
        } else if ratio <= 0.92 {
            text = "商会看板刚出现一件价格不错的【\(listing.item.displayName)】，手慢可能就没了。"
        } else {
            text = "\(listing.sellerName) 新寄售了【\(listing.item.displayName)】，估价师正在复核。"
        }
        append(systemMessage(
            senderName: "商会书记",
            icon: "storefront",
            kind: .market,
            text: text,
            action: .market,
            reference: listing.item.displayName,
            importance: listing.item.quality == .legendary ? 3 : 2,
            now: now
        ), state: &state)
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func recordPlayerPurchase(_ listing: MarketListing, gameState: GameState, now: Date = Date()) {
        var state = gameState.chatState
        let text = "\(gameState.player?.name ?? "一位冒险者") 刚从 \(listing.sellerName) 手里买下【\(listing.item.displayName)】，商会柜台结算得很快。"
        append(systemMessage(
            senderName: "商会书记",
            icon: "bag.fill",
            kind: .market,
            text: text,
            action: .market,
            reference: listing.item.displayName,
            importance: listing.item.quality == .legendary ? 3 : 1,
            now: now
        ), state: &state)
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func recordDungeonResult(_ result: DungeonResult, gameState: GameState, now: Date = Date()) {
        var state = gameState.chatState
        let key = "dungeon-\(result.dungeonName)-\(result.isSuccess)-\(result.monstersKilled)-\(result.loot.count)-\(Int(result.timeTaken))"
        guard !state.deliveredEventKeys.contains(key) else { return }
        state.deliveredEventKeys.append(key)

        if result.isSuccess {
            let text = "\(gameState.player?.name ?? "一位冒险者") 通关了 \(result.dungeonName)，击败 \(result.monstersKilled) 只魔物，公会评价为 \(result.rating.rawValue)。"
            append(systemMessage(
                senderName: "公会书记",
                icon: "checkmark.seal.fill",
                kind: .dungeon,
                text: text,
                action: .dungeonList,
                reference: result.dungeonName,
                importance: result.rating == .S ? 3 : 2,
                now: now
            ), state: &state)

            if let bestLoot = result.loot.sorted(by: { qualityRank($0.quality) > qualityRank($1.quality) }).first,
               bestLoot.quality != .common {
                let lootText = "\(gameState.player?.name ?? "一位冒险者") 从 \(result.dungeonName) 带回了【\(bestLoot.displayName)】，\(bestLoot.quality.displayName)光泽很显眼。"
                append(systemMessage(
                    senderName: "掉落记录员",
                    icon: "sparkles",
                    kind: .loot,
                    text: lootText,
                    action: .inventory,
                    reference: bestLoot.displayName,
                    importance: bestLoot.quality == .legendary ? 3 : 2,
                    now: now.addingTimeInterval(1)
                ), state: &state)
            }
        } else {
            let text = "\(gameState.player?.name ?? "一位冒险者") 从 \(result.dungeonName) 撤回营地，公会医师已经备好绷带。"
            append(systemMessage(
                senderName: "公会书记",
                icon: "exclamationmark.triangle.fill",
                kind: .dungeon,
                text: text,
                action: .dungeonList,
                reference: result.dungeonName,
                importance: 1,
                now: now
            ), state: &state)
        }

        trimDeliveredKeys(state: &state)
        gameState.chatState = state
        gameState.saveProgress()
    }

    static func recordEnhancement(item: Item, success: Bool, targetLevel: Int, gameState: GameState, now: Date = Date()) {
        guard targetLevel >= 4 || item.quality == .epic || item.quality == .legendary || Bool.random(probability: 0.45) else { return }
        var state = gameState.chatState
        let text: String
        let importance: Int
        if success {
            text = "\(gameState.player?.name ?? "一位冒险者") 把【\(item.compactDisplayName)】强化到了 +\(targetLevel)，工坊火星溅到了门口。"
            importance = targetLevel >= 8 ? 3 : 2
        } else {
            text = "\(gameState.player?.name ?? "一位冒险者") 强化【\(item.compactDisplayName)】失败了，赤铜铁匠说材料还可以再攒。"
            importance = targetLevel >= 8 ? 2 : 1
        }
        append(systemMessage(
            senderName: "赤铜工坊",
            icon: "hammer.fill",
            kind: .enhancement,
            text: text,
            action: .inventory,
            reference: item.displayName,
            importance: importance,
            now: now
        ), state: &state)
        gameState.chatState = state
        gameState.saveProgress()
    }

    private static func seedOpeningMessages(state: inout WorldChatState, gameState: GameState, now: Date) {
        append(systemMessage(
            senderName: "银冠书记",
            icon: "scroll.fill",
            kind: .system,
            text: "传讯水晶已校准。这里记录的是银冠公会与各地旅人的留言、传闻和商会动态。",
            action: .none,
            reference: nil,
            importance: 2,
            now: now.addingTimeInterval(-24)
        ), state: &state)

        let firstLines = [
            "幽暗矿洞的回声今天不太对，像是有矿镐敲到了旧门。",
            "商会柜台已经换过三轮价签，热门部位还是\(hotTypeName(gameState))。",
            "银冠战力榜刚同步过，前二十附近又开始动了。",
            "别把所有金币都砸进一次强化，赤铜工坊不会替你心疼。",
            "黑石要塞外的营火没灭，看来还有队伍准备夜里进去。"
        ]

        for (index, line) in firstLines.prefix(4).enumerated() {
            let robot = robots[index % robots.count]
            append(robotMessage(robot: robot, kind: .adventurer, text: line, action: .none, reference: nil, importance: 0, now: now.addingTimeInterval(TimeInterval(-20 + index * 4))), state: &state)
        }

        state.lastGenerateTime = now
    }

    private static func addOfflineSummaryIfNeeded(state: inout WorldChatState, gameState: GameState, now: Date) -> Bool {
        let elapsed = now.timeIntervalSince(state.lastGenerateTime)
        guard elapsed >= 20 * 60 else { return false }

        let hours = max(1, Int(elapsed / 3600))
        let maxCount = min(6, max(3, Int(elapsed / (2 * 3600)) + 2))
        let summaries = offlineSummaryLines(gameState: gameState, hours: hours)
        for line in summaries.prefix(maxCount) {
            append(systemMessage(
                senderName: "夜班书记",
                icon: "moon.stars.fill",
                kind: .event,
                text: line,
                action: .none,
                reference: nil,
                importance: 1,
                now: now.addingTimeInterval(TimeInterval(-summaries.count))
            ), state: &state)
        }
        state.lastGenerateTime = now
        return true
    }

    private static func offlineSummaryLines(gameState: GameState, hours: Int) -> [String] {
        let records = gameState.marketState.tradeRecords.prefix(3).map { record in
            "你离开水晶后的 \(hours) 小时里，商会有一笔醒目的成交：【\(record.itemName)】以 \(record.price) 金转手。"
        }
        let logs = gameState.robotLeaderboardState.logs.prefix(3).map { log in
            "离线记录：\(log.text)"
        }
        let fallback = [
            "离线记录：公会成员整理了几份副本路线，\(dungeonName(gameState)) 的留言最多。",
            "离线记录：\(hotTypeName(gameState)) 的求购牌被翻了好几次。",
            "离线记录：战力榜边缘有几名冒险者交换了位置。"
        ]
        let merged = Array(records) + Array(logs)
        return merged.isEmpty ? fallback : Array(merged.prefix(6)) + fallback.prefix(max(0, 3 - merged.count))
    }

    private static func importSystemEvents(
        state: inout WorldChatState,
        gameState: GameState,
        now: Date,
        limit: Int
    ) -> Bool {
        var changed = false
        var importedCount = 0
        let importedRecords = Set(state.importedMarketRecordIds)
        let newRecords = gameState.marketState.tradeRecords
            .prefix(10)
            .filter { !importedRecords.contains($0.id) }
            .prefix(limit)
            .reversed()

        for record in newRecords where importedCount < limit {
            append(message(for: record, now: max(record.time, now.addingTimeInterval(TimeInterval(-limit + importedCount)))), state: &state)
            state.importedMarketRecordIds.append(record.id)
            importedCount += 1
            changed = true
        }

        let importedActivities = Set(state.importedMarketActivityIds)
        let newActivities = gameState.marketState.activities
            .prefix(8)
            .filter { $0.isImportant && !importedActivities.contains($0.id) }
            .prefix(max(0, limit - importedCount))
            .reversed()

        for activity in newActivities where importedCount < limit {
            append(systemMessage(
                senderName: "商会书记",
                icon: "storefront",
                kind: .market,
                text: activity.text,
                action: .market,
                reference: nil,
                importance: activity.isImportant ? 2 : 1,
                now: max(activity.time, now.addingTimeInterval(TimeInterval(-limit + importedCount)))
            ), state: &state)
            state.importedMarketActivityIds.append(activity.id)
            importedCount += 1
            changed = true
        }

        let importedLogs = Set(state.importedLeaderboardLogIds)
        let newLogs = gameState.robotLeaderboardState.logs
            .prefix(10)
            .filter { !importedLogs.contains($0.id) && $0.importance >= 1 }
            .prefix(max(0, limit - importedCount))
            .reversed()

        for log in newLogs where importedCount < limit {
            append(message(for: log, now: max(log.time, now.addingTimeInterval(TimeInterval(-limit + importedCount)))), state: &state)
            state.importedLeaderboardLogIds.append(log.id)
            importedCount += 1
            changed = true
        }

        trimImportedIds(state: &state)
        return changed
    }

    private static func message(for record: MarketTradeRecord, now: Date) -> WorldChatMessage {
        let kind: WorldChatMessageKind = .market
        let action: WorldChatAction = .market
        let text: String
        let importance: Int

        switch record.recordType {
        case .sale:
            text = "\(record.buyerName) 在商会买走了 \(record.sellerName) 寄售的【\(record.itemName)】，成交 \(record.price) 金。"
            importance = record.isPlayerRelated ? 3 : 1
        case .purchase:
            text = "\(record.buyerName) 刚从 \(record.sellerName) 手里买下【\(record.itemName)】，价签是 \(record.price) 金。"
            importance = record.isPlayerRelated ? 2 : 1
        case .npcTrade:
            text = "\(record.buyerName) 和 \(record.sellerName) 在柜台完成结算，【\(record.itemName)】换了主人。"
            importance = 1
        case .listing:
            text = "\(record.sellerName) 把【\(record.itemName)】挂上商会看板，标价 \(record.price) 金。"
            importance = 1
        case .notice:
            text = record.itemName
            importance = 1
        }

        return systemMessage(
            senderName: "商会书记",
            icon: "storefront",
            kind: kind,
            text: text,
            action: action,
            reference: record.itemName,
            importance: importance,
            now: now
        )
    }

    private static func message(for log: RobotActivityLog, now: Date) -> WorldChatMessage {
        let kind: WorldChatMessageKind
        let action: WorldChatAction
        switch log.type {
        case .marketBuy:
            kind = .market
            action = .market
        case .rankMove, .levelUp:
            kind = .leaderboard
            action = .leaderboard
        case .loot, .equip:
            kind = .loot
            action = .none
        case .enhanceSuccess, .enhanceFail:
            kind = .enhancement
            action = .none
        case .dungeonClear, .dungeonFail:
            kind = .dungeon
            action = .dungeonList
        case .notice:
            kind = .event
            action = .none
        }

        return systemMessage(
            senderName: "银冠档案员",
            icon: icon(for: log.type),
            kind: kind,
            text: log.text,
            action: action,
            reference: nil,
            importance: log.importance,
            now: now
        )
    }

    private static func makeAmbientMessage(state: inout WorldChatState, gameState: GameState, now: Date) -> WorldChatMessage? {
        guard let template = pickTemplate(state: state, gameState: gameState, now: now) else { return nil }
        let robot = pickRobot(avoiding: state.lastRobotId, focusedOn: template.focus)
        state.lastRobotId = robot.id
        state.templateCooldowns[template.key] = now
        let text = render(template.text, robot: robot, gameState: gameState)
        return robotMessage(
            robot: robot,
            kind: template.kind,
            text: text,
            action: template.action,
            reference: referenceName(for: template, gameState: gameState),
            importance: template.importance,
            now: now
        )
    }

    private static func makeCrossTalkMessage(state: inout WorldChatState, gameState: GameState, now: Date) -> WorldChatMessage? {
        let robot = pickRobot(avoiding: state.lastRobotId, focusedOn: nil)
        state.lastRobotId = robot.id
        let lines = [
            "你们先聊，我去商会看一眼价签。",
            "这话我记下了，等我通完下一间房再回来对账。",
            "水晶那边别吵，书记已经皱眉了。",
            "如果又是强化失败，我建议先离铁砧远一点。",
            "有人提到 \(dungeonName(gameState))？那地方我刚好想再试一次。"
        ]
        return robotMessage(
            robot: robot,
            kind: .adventurer,
            text: lines.randomElement() ?? "公会大厅今天挺热闹。",
            action: .none,
            reference: nil,
            importance: 0,
            now: now
        )
    }

    private static func makeKeywordReplies(
        for text: String,
        state: inout WorldChatState,
        gameState: GameState,
        now: Date
    ) -> [WorldChatDelayedReply] {
        let lower = text.lowercased()
        let focus: String
        let replyPool: [String]
        let action: WorldChatAction

        if lower.contains("卖") || lower.contains("买") || lower.contains("市场") || lower.contains("法杖") || lower.contains("装备") {
            focus = "market"
            action = .market
            replyPool = [
                "我刚在商会看见一件【\(itemName(gameState))】，价格不低，但词缀还算干净。",
                "\(hotTypeName(gameState)) 现在问价的人多，真要买就别等太久。",
                "别只看标价，先看看强化等级和需要等级，商会价签很会骗人。",
                "求购牌可以挂着，海盐商人托比会盯这种单子。"
            ]
        } else if lower.contains("强化") || lower.contains("失败") || lower.contains("+") {
            focus = "enhancement"
            action = .inventory
            replyPool = [
                "别怪铁锤，材料不够硬才是问题。",
                "懂你，我昨天也在赤铜工坊折了三次。",
                "强化石不够的时候，最好先去刷一轮 \(dungeonName(gameState))。",
                "上头之前先数金币，工坊从不赊账。"
            ]
        } else if lower.contains("副本") || lower.contains("boss") || lower.contains("Boss") || lower.contains("刷") {
            focus = "dungeon"
            action = .dungeonList
            replyPool = [
                "\(dungeonName(gameState)) 今天留言很多，带够补给再进。",
                "战力不够就别硬闯 Boss 房，公会医师会记住你的名字。",
                "如果只是刷材料，选你已经通关的副本更稳。",
                "骷髅弓手烦归烦，掉落表还是值得看一眼。"
            ]
        } else if lower.contains("排行") || lower.contains("战力") || lower.contains("冲榜") {
            focus = "leaderboard"
            action = .leaderboard
            replyPool = [
                "银冠榜刚同步过，前二十附近确实卷得厉害。",
                "\(playerName(gameState)) 的战力再涨一点，榜单旁边就会有人抬头看了。",
                "别只盯排名，装备空槽补齐通常比硬强化更划算。",
                "前十那些人话少，但他们的装备不会少。"
            ]
        } else if lower.contains("掉落") || lower.contains("紫装") || lower.contains("传说") || lower.contains("稀有") {
            focus = "loot"
            action = .dungeonList
            replyPool = [
                "想看稀有光泽，就多盯 Boss 房，普通小怪别抱太大希望。",
                "\(dungeonName(gameState)) 的掉落传闻今天确实多了一些。",
                "刚才有人晒【\(itemName(gameState))】，水晶亮得我眼睛疼。",
                "紫装会来的，只是通常会先折磨你的背包。"
            ]
        } else if lower.contains("黑市") || lower.contains("传闻") {
            focus = "event"
            action = .market
            replyPool = [
                "黑市的消息只适合听一半，另一半通常写在价签背面。",
                "暗巷那边今晚像是有稀有饰品，但别问得太细。",
                "传闻说 \(dungeonName(gameState)) 深处的魔物有点躁动。",
                "王国路标没变，变的是走路的人。"
            ]
        } else {
            focus = "event"
            action = .none
            replyPool = [
                "水晶收到你的留言了，公会大厅也有人在看。",
                "这话题可以留着，等商会下一轮价签更新再说。",
                "我先记一笔，万一今晚用得上。",
                "公会大厅今天不缺故事，缺的是愿意先探路的人。"
            ]
        }

        let replyCount = Int.random(in: 1...min(3, max(1, replyPool.count)))
        var selectedRobots: [ChatRobotProfile] = []
        var replies: [WorldChatDelayedReply] = []

        for index in 0..<replyCount {
            let robot = pickRobot(avoiding: selectedRobots.last?.id ?? state.lastRobotId, focusedOn: focus)
            if selectedRobots.contains(where: { $0.id == robot.id }) { continue }
            selectedRobots.append(robot)
            state.lastRobotId = robot.id

            let text = replyPool[min(index, replyPool.count - 1)]
            let delay = TimeInterval(Double(index) * 1.2 + Double.random(in: 0.9...2.6))
            let message = robotMessage(
                robot: robot,
                kind: focus == "event" ? .adventurer : kindForFocus(focus),
                text: text,
                action: action,
                reference: nil,
                importance: 1,
                now: now.addingTimeInterval(delay)
            )
            replies.append(WorldChatDelayedReply(message: message, delay: delay))
        }
        return replies
    }

    private static func pickTemplate(state: WorldChatState, gameState: GameState, now: Date) -> ChatTemplate? {
        let playerLevel = gameState.player?.level ?? 1
        let candidates = templates.filter { template in
            template.minLevel <= playerLevel + 3
                && now.timeIntervalSince(state.templateCooldowns[template.key] ?? .distantPast) >= templateCooldown
        }
        let pool = candidates.isEmpty ? templates.filter { $0.minLevel <= playerLevel + 3 } : candidates
        guard !pool.isEmpty else { return nil }

        let totalWeight = pool.reduce(0.0) { $0 + $1.weight }
        var roll = Double.random(in: 0..<max(0.01, totalWeight))
        for template in pool {
            roll -= template.weight
            if roll <= 0 {
                return template
            }
        }
        return pool.last
    }

    private static func pickRobot(avoiding lastRobotId: String?, focusedOn focus: String?) -> ChatRobotProfile {
        let focused = focus.map { focus in
            robots.filter { $0.focusSystems.contains(focus) }
        } ?? []
        let base = focused.isEmpty ? robots : focused
        let pool = base.filter { $0.id != lastRobotId }
        return (pool.isEmpty ? base : pool).randomElement() ?? robots[0]
    }

    private static func render(_ template: String, robot: ChatRobotProfile, gameState: GameState) -> String {
        template
            .replacingOccurrences(of: "{robot}", with: robot.name)
            .replacingOccurrences(of: "{dungeon}", with: dungeonName(gameState))
            .replacingOccurrences(of: "{item}", with: itemName(gameState))
            .replacingOccurrences(of: "{slot}", with: hotTypeName(gameState))
            .replacingOccurrences(of: "{monster}", with: monsterName(gameState))
            .replacingOccurrences(of: "{gold}", with: "\(Int.random(in: 120...2600))")
            .replacingOccurrences(of: "{rank}", with: "\(Int.random(in: 8...80))")
            .replacingOccurrences(of: "{power}", with: "\(Int.random(in: 120...680))")
            .replacingOccurrences(of: "{player}", with: playerName(gameState))
            .replacingOccurrences(of: "{profession}", with: gameState.player?.profession.displayName ?? "战士")
    }

    private static func referenceName(for template: ChatTemplate, gameState: GameState) -> String? {
        switch template.action {
        case .market:
            return itemName(gameState)
        case .dungeonList:
            return dungeonName(gameState)
        case .leaderboard:
            return "银冠战力榜"
        case .inventory:
            return "背包"
        case .none:
            return nil
        }
    }

    private static func robotMessage(
        robot: ChatRobotProfile,
        kind: WorldChatMessageKind,
        text: String,
        action: WorldChatAction,
        reference: String?,
        importance: Int,
        now: Date
    ) -> WorldChatMessage {
        WorldChatMessage(
            id: UUID(),
            time: now,
            channelId: "guild_crystal",
            senderId: robot.id,
            senderName: robot.name,
            senderSubtitle: robot.subtitle,
            senderIcon: icon(for: robot),
            kind: kind,
            text: text,
            action: action,
            referenceName: reference,
            isPlayer: false,
            importance: importance
        )
    }

    private static func systemMessage(
        senderName: String,
        icon: String,
        kind: WorldChatMessageKind,
        text: String,
        action: WorldChatAction,
        reference: String?,
        importance: Int,
        now: Date
    ) -> WorldChatMessage {
        WorldChatMessage(
            id: UUID(),
            time: now,
            channelId: "guild_crystal",
            senderId: nil,
            senderName: senderName,
            senderSubtitle: WorldChatSystem.channelName,
            senderIcon: icon,
            kind: kind,
            text: text,
            action: action,
            referenceName: reference,
            isPlayer: false,
            importance: importance
        )
    }

    private static func append(_ message: WorldChatMessage, state: inout WorldChatState) {
        state.messages.append(message)
        state.messages.sort { $0.time < $1.time }
        if state.messages.count > maxMessages {
            state.messages = Array(state.messages.suffix(maxMessages))
        }
    }

    private static func nextInterval(for state: WorldChatState, now: Date) -> TimeInterval {
        let seed = "\(state.messages.count)-\(Int(now.timeIntervalSince1970 / 10))"
        let ratio = Double(abs(seed.hashValue % 1000)) / 1000.0
        return generationIntervalRange.lowerBound + (generationIntervalRange.upperBound - generationIntervalRange.lowerBound) * ratio
    }

    private static func sanitizedPlayerText(_ rawText: String) -> String {
        let trimmed = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > 64 else { return trimmed }
        return String(trimmed.prefix(64))
    }

    private static func isSafePlayerText(_ text: String) -> Bool {
        let lowered = text.lowercased()
        let blocked = [
            "http", "www.", ".com", "微信", "qq", "vx", "手机号", "电话", "银行卡",
            "支付宝", "充值", "真钱", "人民币", "线下", "加我", "广告", "色情", "政治",
            "身份证", "@"
        ]
        return !blocked.contains { lowered.contains($0.lowercased()) }
    }

    private static func trimImportedIds(state: inout WorldChatState) {
        if state.importedMarketRecordIds.count > 80 {
            state.importedMarketRecordIds = Array(state.importedMarketRecordIds.suffix(80))
        }
        if state.importedMarketActivityIds.count > 80 {
            state.importedMarketActivityIds = Array(state.importedMarketActivityIds.suffix(80))
        }
        if state.importedLeaderboardLogIds.count > 80 {
            state.importedLeaderboardLogIds = Array(state.importedLeaderboardLogIds.suffix(80))
        }
    }

    private static func trimDeliveredKeys(state: inout WorldChatState) {
        if state.deliveredEventKeys.count > 80 {
            state.deliveredEventKeys = Array(state.deliveredEventKeys.suffix(80))
        }
    }

    private static func kindForFocus(_ focus: String) -> WorldChatMessageKind {
        switch focus {
        case "market": return .market
        case "leaderboard": return .leaderboard
        case "dungeon": return .dungeon
        case "loot": return .loot
        case "enhancement": return .enhancement
        case "event": return .event
        default: return .adventurer
        }
    }

    private static func icon(for robot: ChatRobotProfile) -> String {
        if robot.profession.contains("法师") { return "wand.and.stars" }
        if robot.profession.contains("商人") || robot.profession.contains("情报") { return "bag.fill" }
        if robot.profession.contains("游侠") || robot.profession.contains("射手") { return "scope" }
        if robot.profession.contains("铁匠") { return "hammer.fill" }
        if robot.profession.contains("吟游") { return "music.note" }
        if robot.profession.contains("骑士") { return "shield.lefthalf.filled" }
        return "person.fill"
    }

    private static func icon(for type: RobotLogType) -> String {
        switch type {
        case .dungeonClear: return "checkmark.seal.fill"
        case .dungeonFail: return "exclamationmark.triangle.fill"
        case .levelUp: return "arrow.up.circle.fill"
        case .loot: return "sparkles"
        case .equip: return "shield.lefthalf.filled"
        case .enhanceSuccess: return "hammer.fill"
        case .enhanceFail: return "xmark.circle.fill"
        case .marketBuy: return "bag.fill"
        case .rankMove: return "chart.line.uptrend.xyaxis"
        case .notice: return "megaphone.fill"
        }
    }

    private static func dungeonName(_ gameState: GameState) -> String {
        let level = gameState.player?.level ?? 1
        let pool = ConfigLoader.shared.dungeonConfigs.filter { $0.recommendedLevel <= level + 3 }
        return (pool.randomElement() ?? ConfigLoader.shared.dungeonConfigs.first)?.name ?? "幽暗矿洞"
    }

    private static func itemName(_ gameState: GameState) -> String {
        let level = gameState.player?.level ?? 1
        let pool = ConfigLoader.shared.itemTemplates.filter {
            $0.type.equipSlot != nil && $0.requiredLevel <= level + 4
        }
        return (pool.randomElement() ?? ConfigLoader.shared.itemTemplates.first)?.name ?? "秘纹长剑"
    }

    private static func monsterName(_ gameState: GameState) -> String {
        ConfigLoader.shared.monsterConfigs.randomElement()?.name ?? "骷髅弓手"
    }

    private static func hotTypeName(_ gameState: GameState) -> String {
        (gameState.marketState.hotTypes.randomElement() ?? .weapon).displayName
    }

    private static func playerName(_ gameState: GameState) -> String {
        gameState.player?.name ?? "一位冒险者"
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

private struct ChatTemplate {
    let key: String
    let focus: String
    let kind: WorldChatMessageKind
    let action: WorldChatAction
    let minLevel: Int
    let weight: Double
    let importance: Int
    let text: String
}

private extension WorldChatSystem {
    static let templates: [ChatTemplate] = [
        ChatTemplate(key: "daily_01", focus: "event", kind: .adventurer, action: .none, minLevel: 1, weight: 1.0, importance: 0, text: "今天公会大厅的火盆烧得很旺，像是又有人从 {dungeon} 带回麻烦。"),
        ChatTemplate(key: "daily_02", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 1, weight: 1.1, importance: 0, text: "{dungeon} 的 {monster} 真的烦，明明不强，却总拖时间。"),
        ChatTemplate(key: "daily_03", focus: "loot", kind: .loot, action: .dungeonList, minLevel: 1, weight: 1.0, importance: 0, text: "我在 {dungeon} 刷了半天，只看见一堆旧护手。"),
        ChatTemplate(key: "daily_04", focus: "event", kind: .event, action: .none, minLevel: 1, weight: 0.8, importance: 0, text: "王国信使路过时说，北边的雾比昨天重。"),
        ChatTemplate(key: "daily_05", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 1.0, importance: 0, text: "商会柜台那边又换价签了，{slot} 估计又热起来了。"),
        ChatTemplate(key: "dungeon_01", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 1, weight: 1.2, importance: 1, text: "刚从 {dungeon} 出来，Boss 房前最好留点药。"),
        ChatTemplate(key: "dungeon_02", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 1, weight: 1.0, importance: 0, text: "{dungeon} 今天不算难，难的是背包格子不够。"),
        ChatTemplate(key: "dungeon_03", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 3, weight: 0.9, importance: 0, text: "别低估 {monster}，它打断施法的时机太准了。"),
        ChatTemplate(key: "dungeon_04", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 5, weight: 0.9, importance: 1, text: "{dungeon} 深处像是刷新了精英怪，脚印比平时更乱。"),
        ChatTemplate(key: "dungeon_05", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 8, weight: 0.7, importance: 1, text: "深一点的副本别硬闯，战力差 {power} 都会很难受。"),
        ChatTemplate(key: "loot_01", focus: "loot", kind: .loot, action: .dungeonList, minLevel: 1, weight: 1.1, importance: 1, text: "有人在 {dungeon} 见过【{item}】吗？我只听过名字。"),
        ChatTemplate(key: "loot_02", focus: "loot", kind: .loot, action: .none, minLevel: 1, weight: 1.0, importance: 0, text: "刚出一件带好词缀的 {slot}，今晚酒钱有了。"),
        ChatTemplate(key: "loot_03", focus: "loot", kind: .loot, action: .dungeonList, minLevel: 4, weight: 0.9, importance: 1, text: "{dungeon} 掉落表别只看传闻，Boss 才是重点。"),
        ChatTemplate(key: "loot_04", focus: "loot", kind: .loot, action: .none, minLevel: 8, weight: 0.8, importance: 1, text: "我看见【{item}】的光了，可惜不是我的。"),
        ChatTemplate(key: "loot_05", focus: "loot", kind: .loot, action: .inventory, minLevel: 12, weight: 0.65, importance: 2, text: "稀有装备别急着卖，先看是不是能补你身上的空槽。"),
        ChatTemplate(key: "market_01", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 1.3, importance: 1, text: "市场刚有人低价挂了一件 {slot}，我怀疑他急着筹强化费。"),
        ChatTemplate(key: "market_02", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 1.1, importance: 0, text: "求一件适合 {profession} 的 {slot}，别开收藏价。"),
        ChatTemplate(key: "market_03", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 1.1, importance: 1, text: "我刚买到【{item}】，战力涨了一截。"),
        ChatTemplate(key: "market_04", focus: "market", kind: .market, action: .market, minLevel: 3, weight: 0.95, importance: 0, text: "商会估价师说 {slot} 需求在涨，别把好货太早贱卖。"),
        ChatTemplate(key: "market_05", focus: "market", kind: .market, action: .market, minLevel: 5, weight: 0.8, importance: 1, text: "有人把【{item}】挂到 {gold} 金，我看不懂但尊重。"),
        ChatTemplate(key: "market_06", focus: "market", kind: .market, action: .market, minLevel: 8, weight: 0.75, importance: 1, text: "今晚商会货架补得很快，低价货也消失得很快。"),
        ChatTemplate(key: "rank_01", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 1, weight: 1.0, importance: 1, text: "银冠战力榜第 {rank} 名附近又换人了。"),
        ChatTemplate(key: "rank_02", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 1, weight: 0.95, importance: 1, text: "刚被人超过，我得去刷两把 {dungeon}。"),
        ChatTemplate(key: "rank_03", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 5, weight: 0.85, importance: 1, text: "战力涨 {power} 看着不多，排名旁边的人会很有感觉。"),
        ChatTemplate(key: "rank_04", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 8, weight: 0.75, importance: 2, text: "前十那几位不怎么说话，但每次同步都在涨。"),
        ChatTemplate(key: "rank_05", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 12, weight: 0.65, importance: 1, text: "榜单越往上越像雪坡，一停就往下滑。"),
        ChatTemplate(key: "enhance_01", focus: "enhancement", kind: .enhancement, action: .inventory, minLevel: 1, weight: 1.1, importance: 0, text: "强化 +6 又失败，赤铜工坊的门槛都快被我踩平了。"),
        ChatTemplate(key: "enhance_02", focus: "enhancement", kind: .enhancement, action: .inventory, minLevel: 1, weight: 1.0, importance: 0, text: "别上头，强化石真的不够用。"),
        ChatTemplate(key: "enhance_03", focus: "enhancement", kind: .enhancement, action: .inventory, minLevel: 4, weight: 0.9, importance: 1, text: "我的【{item}】终于强化成功，战力多了 {power}。"),
        ChatTemplate(key: "enhance_04", focus: "enhancement", kind: .enhancement, action: .market, minLevel: 7, weight: 0.75, importance: 1, text: "强化过的 {slot} 在商会好卖，但价签别太离谱。"),
        ChatTemplate(key: "enhance_05", focus: "enhancement", kind: .enhancement, action: .inventory, minLevel: 10, weight: 0.65, importance: 1, text: "+10 以后每一下都像把金币扔进火里。"),
        ChatTemplate(key: "event_01", focus: "event", kind: .event, action: .dungeonList, minLevel: 1, weight: 0.9, importance: 1, text: "听说 {dungeon} 深处出现了新的脚印，不像普通魔物。"),
        ChatTemplate(key: "event_02", focus: "event", kind: .event, action: .market, minLevel: 1, weight: 0.85, importance: 1, text: "商会今天收到不少稀有矿石，赤铜工坊会忙到很晚。"),
        ChatTemplate(key: "event_03", focus: "event", kind: .event, action: .market, minLevel: 5, weight: 0.8, importance: 1, text: "地下黑市今晚可能会有好东西，但价签不会温柔。"),
        ChatTemplate(key: "event_04", focus: "event", kind: .event, action: .none, minLevel: 8, weight: 0.65, importance: 1, text: "王国边境的路灯灭了三盏，巡夜人说不是风。"),
        ChatTemplate(key: "event_05", focus: "event", kind: .event, action: .dungeonList, minLevel: 12, weight: 0.6, importance: 2, text: "深渊裂隙的传闻又多了，最好别一个人去证实。"),
        ChatTemplate(key: "showoff_01", focus: "loot", kind: .loot, action: .none, minLevel: 1, weight: 0.8, importance: 0, text: "别问，问就是刚捡到【{item}】，今天水晶都替我发亮。"),
        ChatTemplate(key: "showoff_02", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 6, weight: 0.7, importance: 1, text: "排名刚往上挪了一点点，只有一点点。"),
        ChatTemplate(key: "showoff_03", focus: "market", kind: .market, action: .market, minLevel: 6, weight: 0.7, importance: 1, text: "我抢到的那件低价货已经下架了，商会手速也是战力的一部分。"),
        ChatTemplate(key: "complain_01", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 1, weight: 0.8, importance: 0, text: "{monster} 能不能别一直躲在后排？"),
        ChatTemplate(key: "complain_02", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 0.75, importance: 0, text: "谁把普通 {slot} 挂成收藏价了，商会书记真的不管吗？"),
        ChatTemplate(key: "complain_03", focus: "enhancement", kind: .enhancement, action: .inventory, minLevel: 4, weight: 0.75, importance: 0, text: "今天不适合碰铁匠铺，我的金币已经替我证明了。"),
        ChatTemplate(key: "rookie_01", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 1, weight: 0.7, importance: 0, text: "新手问一句，{dungeon} 是先看推荐等级还是推荐战力？"),
        ChatTemplate(key: "rookie_02", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 0.7, importance: 0, text: "入门 {slot} 真的有人卖便宜点吗？我的钱袋很诚实。"),
        ChatTemplate(key: "rookie_03", focus: "loot", kind: .loot, action: .dungeonList, minLevel: 1, weight: 0.65, importance: 0, text: "第一次看到稀有装备的光，差点以为水晶裂了。"),
        ChatTemplate(key: "blackmarket_01", focus: "event", kind: .event, action: .market, minLevel: 5, weight: 0.55, importance: 1, text: "暗巷里有人问【{item}】的来路，这通常不是坏事，也不算好事。"),
        ChatTemplate(key: "blackmarket_02", focus: "market", kind: .market, action: .market, minLevel: 8, weight: 0.5, importance: 1, text: "黑市价只适合听个影子，真买还得看商会柜台。"),
        ChatTemplate(key: "blackmarket_03", focus: "event", kind: .event, action: .none, minLevel: 12, weight: 0.45, importance: 2, text: "有人把深渊裂隙的旧地图拿出来了，边角是烧焦的。"),
        ChatTemplate(key: "guild_01", focus: "event", kind: .system, action: .none, minLevel: 1, weight: 0.55, importance: 1, text: "公会书记提醒：传讯水晶只记录冒险传闻，不记录私人约见。"),
        ChatTemplate(key: "guild_02", focus: "leaderboard", kind: .leaderboard, action: .leaderboard, minLevel: 1, weight: 0.6, importance: 1, text: "银冠公会档案已同步，{player} 的名字也在记录册里。"),
        ChatTemplate(key: "guild_03", focus: "market", kind: .market, action: .market, minLevel: 1, weight: 0.6, importance: 1, text: "商会看板提醒：今日热门是 {slot}，寄售价别偏离太多。"),
        ChatTemplate(key: "guild_04", focus: "dungeon", kind: .dungeon, action: .dungeonList, minLevel: 1, weight: 0.55, importance: 1, text: "公会巡逻队建议：挑战 {dungeon} 前，先确认背包还能装下掉落。"),
        ChatTemplate(key: "guild_05", focus: "enhancement", kind: .enhancement, action: .inventory, minLevel: 6, weight: 0.5, importance: 1, text: "赤铜工坊今日仍开放，但书记建议量力而行。")
    ]
}

private extension Bool {
    static func random(probability: Double) -> Bool {
        Double.random(in: 0...1) <= min(max(probability, 0), 1)
    }
}
