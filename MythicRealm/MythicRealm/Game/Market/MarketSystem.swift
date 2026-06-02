import Foundation

enum MarketSystem {
    static let lightRefreshInterval: TimeInterval = 60
    static let listingDuration: TimeInterval = 8 * 60 * 60
    static let marketResaleLockDuration: TimeInterval = 30 * 60
    static let maxOfflineSimulation: TimeInterval = 24 * 60 * 60
    static let maxTradeRecords = 120
    static let maxActivities = 100

    private static let npcNames = [
        "南门斥候露娜", "灰斗篷贝伦", "红叶猎手伊芙", "旧井法师诺尔", "铜铃佣兵凯", "北桥盾卫玛拉",
        "银针游侠赛娜", "渡鸦书记温特", "黑灯矿工哈洛", "风车镇阿吉", "月湾术士莉塔", "烛火修女安妮",
        "霜脊猎人洛克", "蓝莓酒馆老板", "白石塔学徒", "裂谷巡山人", "海盐商人托比", "乌木剑士卡修",
        "金麦田护卫", "沼泽药剂师", "星砂旅人薇拉", "旧港船匠", "铁环角斗士", "雾林信使",
        "赤铜斧手", "鹰眼巡猎者", "雪松吟游者", "暗巷鉴定师", "橡木镇民兵", "夜雨炼金师",
        "蓝袍见习法师", "西境行商", "矮墙守备官", "麦穗旅店老板", "白鸦占星师", "碎盾老兵",
        "灯塔守夜人", "针叶林猎户", "黑面包厨娘", "银槌修补匠", "古井拾荒者", "花冠游医",
        "雨巷短弓手", "红铜信差", "暮钟护卫", "风息谷学徒", "北风背包客", "盐湖探路者"
    ]

    private static let npcGroups = [
        "蓝狮小队", "烛火旅团", "黑杉二队", "晨星南部分会", "灰鹰侦察营", "白鸦收藏会",
        "铜铃商队", "旧港搬运团", "风车镇护卫队", "月湾法师社", "银针猎团", "霜脊巡逻队",
        "西境行商团", "橡木镇民兵会", "灯塔守夜队", "雨巷短弓会", "盐湖探路队", "赤铜佣兵营"
    ]

    private static let stallNames = [
        "灰帆寄卖柜", "南门临时摊", "白石塔收货处", "旧港二号柜", "蓝狮代售箱", "烛火旅团货袋",
        "晨星分会看板", "铜铃商队货架", "黑杉备用货柜", "月湾法师摊", "西境行囊铺", "风息谷寄卖栏"
    ]

    static func bootstrap(gameState: GameState, now: Date = Date()) {
        var state = gameState.marketState
        let playerLevel = gameState.player?.level ?? 1
        resetDailyIfNeeded(state: &state, playerLevel: playerLevel, now: now)
        ensureRobotInventory(state: &state, playerLevel: playerLevel, now: now)
        if now.timeIntervalSince(state.lastRefreshTime) >= lightRefreshInterval {
            simulate(state: &state, gameState: gameState, now: now, minimumTicks: 0)
        }
        gameState.marketState = state
        gameState.saveProgress()
    }

    @discardableResult
    static func refreshMarket(gameState: GameState, manual: Bool = false, now: Date = Date()) -> MarketActionResult {
        var state = gameState.marketState
        let playerLevel = gameState.player?.level ?? 1
        resetDailyIfNeeded(state: &state, playerLevel: playerLevel, now: now)

        if manual {
            let cost = manualRefreshCost(state: state)
            if cost > 0 {
                guard (gameState.player?.gold ?? 0) >= cost else {
                    return .failure("金币不足，需要 \(cost) 金")
                }
                gameState.player?.gold -= cost
                state.paidRefreshCount += 1
            } else {
                state.freeRefreshCount = max(0, state.freeRefreshCount - 1)
            }
        }

        simulate(state: &state, gameState: gameState, now: now, minimumTicks: manual ? 5 : 3)
        ensureRobotInventory(state: &state, playerLevel: playerLevel, now: now)
        trimMarketState(&state)
        gameState.marketState = state
        gameState.saveProgress()

        if manual {
            return .success(costText(for: state))
        }
        return .success("商会记录已更新")
    }

    @discardableResult
    static func pulseMarket(gameState: GameState, now: Date = Date()) -> MarketActionResult {
        var state = gameState.marketState
        let playerLevel = gameState.player?.level ?? 1
        resetDailyIfNeeded(state: &state, playerLevel: playerLevel, now: now)
        simulate(state: &state, gameState: gameState, now: now, minimumTicks: 1)
        ensureRobotInventory(state: &state, playerLevel: playerLevel, now: now)
        trimMarketState(&state)
        gameState.marketState = state
        gameState.saveProgress()
        return .success("商会正在撮合交易")
    }

    @discardableResult
    static func listItem(_ item: Item, price: Int, gameState: GameState, now: Date = Date()) -> MarketActionResult {
        guard item.type.equipSlot != nil else {
            return .failure("只有装备可以寄售")
        }
        guard gameState.inventory.contains(where: { $0.id == item.id }) else {
            return .failure("装备不在背包中")
        }
        guard !item.isMarketLocked(at: now) else {
            return .failure("商会正在核验该装备来源，暂不可寄售")
        }

        var state = gameState.marketState
        let playerLevel = gameState.player?.level ?? 1
        resetDailyIfNeeded(state: &state, playerLevel: playerLevel, now: now)

        let activeListings = state.playerListings.filter { $0.status == .listed }.count
        guard activeListings < playerListingLimit(playerLevel: playerLevel) else {
            return .failure("寄售栏位已满")
        }

        let window = MarketPricing.priceWindow(for: item, state: state)
        guard price >= window.minimum && price <= window.maximum else {
            return .failure("寄售价需在 \(window.minimum)-\(window.maximum) 金之间")
        }

        let fee = MarketPricing.listingFee(for: item, price: price, recommended: window.recommended)
        guard (gameState.player?.gold ?? 0) >= fee else {
            return .failure("上架费不足，需要 \(fee) 金")
        }

        gameState.player?.gold -= fee
        InventorySystem.removeItem(item, from: gameState)

        let listing = MarketListing(
            id: UUID(),
            sellerType: .player,
            sellerId: "player",
            sellerName: gameState.player?.name ?? "玩家",
            item: item,
            baseValue: window.recommended,
            listPrice: price,
            taxRate: window.taxRate,
            listingFee: fee,
            listTime: now,
            expireTime: now.addingTimeInterval(listingDuration),
            status: .listed,
            buyerName: nil,
            soldTime: nil,
            attentionScore: min(1.0, initialAttention(price: price, recommended: window.recommended, item: item) + 0.22),
            failCount: 0,
            priceTag: MarketPricing.priceTag(price: price, recommended: window.recommended)
        )
        state.playerListings.append(listing)
        appendActivity("商会估价师登记了你的 \(item.displayName)，附近买家正在看货", important: true, state: &state, now: now)

        gameState.marketState = state
        WorldChatSystem.recordMarketListing(listing, gameState: gameState, now: now)
        gameState.saveProgress()
        return .success("已寄售，扣除上架费 \(fee) 金")
    }

    @discardableResult
    static func cancelPlayerListing(_ listing: MarketListing, gameState: GameState, now: Date = Date()) -> MarketActionResult {
        var state = gameState.marketState
        guard let index = state.playerListings.firstIndex(where: { $0.id == listing.id }) else {
            return .failure("订单不存在")
        }
        guard state.playerListings[index].status == .listed else {
            return .failure("该订单无法下架")
        }
        guard gameState.inventory.count < InventorySystem.maxSlots else {
            return .failure("背包已满，无法取回")
        }

        let item = state.playerListings[index].item
        state.playerListings[index].status = .canceled
        gameState.inventory.append(item)
        state.playerListings.remove(at: index)
        appendActivity("你从商会取回了 \(item.displayName)", important: true, state: &state, now: now)

        gameState.marketState = state
        gameState.saveProgress()
        return .success("已下架并取回装备")
    }

    @discardableResult
    static func retrieveExpiredListing(_ listing: MarketListing, gameState: GameState, now: Date = Date()) -> MarketActionResult {
        var state = gameState.marketState
        guard let index = state.playerListings.firstIndex(where: { $0.id == listing.id }) else {
            return .failure("订单不存在")
        }
        guard state.playerListings[index].status == .expired else {
            return .failure("该订单还未到期")
        }
        guard gameState.inventory.count < InventorySystem.maxSlots else {
            return .failure("背包已满，无法取回")
        }

        let item = state.playerListings[index].item
        gameState.inventory.append(item)
        state.playerListings.remove(at: index)
        appendActivity("到期寄售已退回：\(item.displayName)", important: true, state: &state, now: now)

        gameState.marketState = state
        gameState.saveProgress()
        return .success("已取回到期装备")
    }

    static func clearCompletedPlayerListings(gameState: GameState) {
        var state = gameState.marketState
        state.playerListings.removeAll { $0.status == .sold || $0.status == .canceled }
        gameState.marketState = state
        gameState.saveProgress()
    }

    @discardableResult
    static func buyRobotListing(_ listing: MarketListing, gameState: GameState, now: Date = Date()) -> MarketActionResult {
        var state = gameState.marketState
        guard let index = state.robotListings.firstIndex(where: { $0.id == listing.id && $0.status == .listed }) else {
            return .failure("这件装备已经被买走了")
        }
        guard gameState.inventory.count < InventorySystem.maxSlots else {
            return .failure("背包已满")
        }
        guard (gameState.player?.gold ?? 0) >= state.robotListings[index].listPrice else {
            return .failure("金币不足")
        }

        let purchasedListing = state.robotListings[index]
        var item = purchasedListing.item
        let price = state.robotListings[index].listPrice
        item.marketOrigin = .robotMarket
        item.marketLockUntil = now.addingTimeInterval(marketResaleLockDuration)

        gameState.player?.gold -= price
        gameState.inventory.append(item)

        let sellerName = state.robotListings[index].sellerName
        state.robotListings.remove(at: index)
        appendRecord(
            buyer: gameState.player?.name ?? "玩家",
            seller: sellerName,
            item: item.displayName,
            price: price,
            isPlayerRelated: true,
            type: .purchase,
            state: &state,
            now: now
        )
        appendActivity("你从 \(sellerName) 手中买下了 \(item.displayName)", important: true, state: &state, now: now)
        ensureRobotInventory(state: &state, playerLevel: gameState.player?.level ?? 1, now: now)

        gameState.marketState = state
        WorldChatSystem.recordPlayerPurchase(purchasedListing, gameState: gameState, now: now)
        gameState.saveProgress()
        return .success("购买成功，装备已放入背包")
    }

    static func manualRefreshCost(state: MarketState) -> Int {
        guard state.freeRefreshCount <= 0 else { return 0 }
        let multiplier = Int(pow(2.0, Double(min(5, state.paidRefreshCount))))
        return 100 * multiplier
    }

    static func playerListingLimit(playerLevel: Int) -> Int {
        min(8, 3 + max(0, playerLevel / 20))
    }

    static func saleExpectation(for listing: MarketListing) -> String {
        guard listing.baseValue > 0 else { return "普通" }
        let ratio = Double(listing.listPrice) / Double(listing.baseValue)
        if ratio <= 0.9 { return "较快" }
        if ratio <= 1.12 { return "普通" }
        if ratio <= 1.3 { return "较慢" }
        return "很难"
    }

    private static func simulate(
        state: inout MarketState,
        gameState: GameState,
        now: Date,
        minimumTicks: Int
    ) {
        if now < state.lastRefreshTime.addingTimeInterval(-5 * 60) {
            state.suspiciousClockUntil = now.addingTimeInterval(30 * 60)
            appendActivity("商会沙漏记录异常，市场暂停核验", important: true, state: &state, now: now)
            return
        }

        let playerLevel = gameState.player?.level ?? 1
        resetDailyIfNeeded(state: &state, playerLevel: playerLevel, now: now)

        let elapsed = min(max(0, now.timeIntervalSince(state.lastRefreshTime)), maxOfflineSimulation)
        let elapsedTicks = Int(elapsed / lightRefreshInterval)
        let tickCount = max(minimumTicks, elapsedTicks)
        guard tickCount > 0 || state.robotListings.isEmpty else {
            ensureRobotInventory(state: &state, playerLevel: playerLevel, now: now)
            return
        }

        var player = gameState.player
        for tick in 0..<min(144, max(1, tickCount)) {
            let tickTime = state.lastRefreshTime.addingTimeInterval(TimeInterval(tick + 1) * lightRefreshInterval)
            expirePlayerListings(state: &state, now: min(tickTime, now))
            processPlayerListings(state: &state, player: &player, playerLevel: playerLevel, now: min(tickTime, now))
            processRobotTrades(state: &state, playerLevel: playerLevel, now: min(tickTime, now))
            expireRobotListings(state: &state, now: min(tickTime, now))
            ensureRobotInventory(state: &state, playerLevel: playerLevel, now: min(tickTime, now))
        }
        gameState.player = player
        state.lastRefreshTime = now
        trimMarketState(&state)
    }

    private static func processPlayerListings(
        state: inout MarketState,
        player: inout PlayerData?,
        playerLevel: Int,
        now: Date
    ) {
        guard player != nil else { return }

        for index in state.playerListings.indices {
            guard state.playerListings[index].status == .listed else { continue }
            let listing = state.playerListings[index]
            guard now.timeIntervalSince(listing.listTime) >= minimumWait(for: listing) else { continue }

            let robot = buyerCandidate(for: listing.item, price: listing.listPrice, playerLevel: playerLevel, now: now)
            let probability = saleProbability(listing: listing, robot: robot, state: state, now: now)
            let rollSeed = "\(listing.id.uuidString)-\(Int(now.timeIntervalSince1970 / lightRefreshInterval))-\(listing.failCount)-sale"
            let roll = deterministicDouble(seed: rollSeed)

            if roll <= probability {
                let income = listing.netIncome
                let buyerName = npcBuyerName(
                    archetype: robot,
                    seed: "\(listing.id.uuidString)-\(state.daySeed)-\(listing.failCount)-player-buyer"
                )
                player?.gold += income
                state.playerDailyMarketIncome += income
                state.playerListings[index].status = .sold
                state.playerListings[index].buyerName = buyerName
                state.playerListings[index].soldTime = now
                appendRecord(
                    buyer: buyerName,
                    seller: player?.name ?? "玩家",
                    item: listing.item.displayName,
                    price: listing.listPrice,
                    isPlayerRelated: true,
                    type: .sale,
                    state: &state,
                    now: now
                )
                appendActivity(playerSaleActivity(buyer: buyerName, item: listing.item, seed: "\(listing.id.uuidString)-activity"), important: true, state: &state, now: now)
            } else {
                state.playerListings[index].failCount += 1
                state.playerListings[index].attentionScore = min(1.0, state.playerListings[index].attentionScore + 0.14)
            }
        }
    }

    private static func processRobotTrades(state: inout MarketState, playerLevel: Int, now: Date) {
        let bucket = Int(now.timeIntervalSince1970 / lightRefreshInterval)
        let roll = deterministicDouble(seed: "\(state.daySeed)-\(bucket)-npc-trade")
        appendAmbientActivity(state: &state, playerLevel: playerLevel, now: now)

        guard roll < 0.92 else {
            return
        }

        let tradeCount = 3 + deterministicInt(seed: "\(state.daySeed)-\(bucket)-npc-count", upperBound: 6)
        for tradeIndex in 0..<tradeCount {
            let activeIndexes = state.robotListings.indices.filter { state.robotListings[$0].status == .listed }
            guard activeIndexes.count > 80 else { return }

            let indexPosition = deterministicInt(
                seed: "\(state.daySeed)-\(bucket)-\(tradeIndex)-npc-index",
                upperBound: activeIndexes.count
            )
            let index = activeIndexes[indexPosition]
            let listing = state.robotListings[index]
            let buyerName = npcDisplayName(seed: "\(listing.id.uuidString)-\(tradeIndex)-buyer")
            state.robotListings.remove(at: index)

            appendRecord(
                buyer: buyerName,
                seller: listing.sellerName,
                item: listing.item.displayName,
                price: listing.listPrice,
                isPlayerRelated: false,
                type: .npcTrade,
                state: &state,
                now: now
            )
            appendActivity(npcTradeActivity(buyer: buyerName, seller: listing.sellerName, item: listing.item, seed: "\(listing.id.uuidString)-\(tradeIndex)-trade-text"), important: false, state: &state, now: now)
        }
    }

    private static func expirePlayerListings(state: inout MarketState, now: Date) {
        for index in state.playerListings.indices {
            guard state.playerListings[index].status == .listed,
                  now >= state.playerListings[index].expireTime else {
                continue
            }
            state.playerListings[index].status = .expired
            appendActivity("\(state.playerListings[index].item.displayName) 寄售到期", important: true, state: &state, now: now)
        }
    }

    private static func expireRobotListings(state: inout MarketState, now: Date) {
        state.robotListings.removeAll { listing in
            listing.status != .listed || now >= listing.expireTime
        }
    }

    private static func ensureRobotInventory(state: inout MarketState, playerLevel: Int, now: Date) {
        expireRobotListings(state: &state, now: now)
        let target = robotListingTarget(playerLevel: playerLevel, state: state, now: now)
        trimRobotInventoryIfNeeded(state: &state, target: target)
        var activeCount = state.robotListings.filter({ $0.status == .listed }).count
        let saltOffset = state.robotListings.count
        var attempts = 0
        let refillLimit = robotRefillLimit(activeCount: activeCount, target: target)
        var generated = 0
        while activeCount < target && generated < refillLimit && attempts < target * 3 {
            attempts += 1
            if let listing = generateRobotListing(state: &state, playerLevel: playerLevel, now: now, salt: saltOffset + attempts) {
                state.robotListings.append(listing)
                activeCount += 1
                generated += 1
                if attempts <= 8 {
                    appendRecord(
                        buyer: "商会看板",
                        seller: listing.sellerName,
                        item: listing.item.displayName,
                        price: listing.listPrice,
                        isPlayerRelated: false,
                        type: .listing,
                        state: &state,
                        now: now
                    )
                }
            }
        }
        ensureLegendaryInventory(state: &state, playerLevel: playerLevel, now: now, target: target)
    }

    private static func generateRobotListing(
        state: inout MarketState,
        playerLevel: Int,
        now: Date,
        salt: Int,
        forcedQuality: ItemQuality? = nil
    ) -> MarketListing? {
        let robot = MarketRobotProfile.all[deterministicInt(seed: "\(state.daySeed)-\(salt)-robot", upperBound: MarketRobotProfile.all.count)]
        guard let template = chooseTemplate(for: robot, playerLevel: playerLevel, seed: "\(state.daySeed)-\(salt)-template", forcedQuality: forcedQuality) else {
            return nil
        }

        var item = Item(template: template)
        item.enhancementLevel = generatedEnhancement(for: item.quality, seed: "\(state.daySeed)-\(salt)-enhance")

        let baseValue = MarketPricing.estimateValue(for: item, state: state)
        let multiplier = robotPriceMultiplier(state: &state, quality: item.quality, seed: "\(state.daySeed)-\(salt)-price")
        let price = max(1, Int(Double(baseValue) * multiplier))
        let fee = MarketPricing.listingFee(for: item, price: price, recommended: baseValue)
        let sellerName = npcSellerName(archetype: robot, seed: "\(state.daySeed)-\(salt)-seller")

        return MarketListing(
            id: UUID(),
            sellerType: .robot,
            sellerId: "\(robot.id)-\(salt)",
            sellerName: sellerName,
            item: item,
            baseValue: baseValue,
            listPrice: price,
            taxRate: MarketPricing.baseTaxRate,
            listingFee: fee,
            listTime: now,
            expireTime: now.addingTimeInterval(TimeInterval(Int.random(in: 2...8)) * 60 * 60),
            status: .listed,
            buyerName: nil,
            soldTime: nil,
            attentionScore: initialAttention(price: price, recommended: baseValue, item: item),
            failCount: 0,
            priceTag: MarketPricing.priceTag(price: price, recommended: baseValue)
        )
    }

    private static func chooseTemplate(for robot: MarketRobotProfile, playerLevel: Int, seed: String, forcedQuality: ItemQuality? = nil) -> ItemTemplate? {
        let maxLevel = min(PlayerData.maxLevel, max(1, playerLevel + 2))
        let minLevel = max(1, playerLevel - 8)
        let desiredQuality = forcedQuality ?? generatedQuality(playerLevel: playerLevel, seed: seed)

        let templates = ConfigLoader.shared.itemTemplates.filter { template in
            template.type.equipSlot != nil
            && template.requiredLevel >= minLevel
            && template.requiredLevel <= maxLevel
        }

        let preferred = templates.filter { template in
            template.quality == desiredQuality && robot.preferredTypes.contains(template.type)
        }
        let sameQuality = templates.filter { $0.quality == desiredQuality }
        let pool = preferred.isEmpty ? (sameQuality.isEmpty ? templates : sameQuality) : preferred
        guard !pool.isEmpty else { return nil }

        let index = deterministicInt(seed: "\(seed)-pick", upperBound: pool.count)
        return pool[index]
    }

    private static func generatedQuality(playerLevel: Int, seed: String) -> ItemQuality {
        let roll = deterministicDouble(seed: seed)
        if playerLevel < 6 {
            if roll < 0.58 { return .common }
            if roll < 0.92 { return .uncommon }
            return .rare
        }
        if playerLevel < 20 {
            if roll < 0.25 { return .common }
            if roll < 0.62 { return .uncommon }
            if roll < 0.94 { return .rare }
            return .epic
        }
        if roll < 0.14 { return .common }
        if roll < 0.42 { return .uncommon }
        if roll < 0.78 { return .rare }
        if roll < 0.97 { return .epic }
        return .legendary
    }

    private static func generatedEnhancement(for quality: ItemQuality, seed: String) -> Int {
        let roll = deterministicDouble(seed: seed)
        switch quality {
        case .common:
            return roll < 0.1 ? 1 : 0
        case .uncommon:
            return roll < 0.2 ? 1 : 0
        case .rare:
            return roll < 0.22 ? 2 : (roll < 0.45 ? 1 : 0)
        case .epic:
            return roll < 0.14 ? 4 : (roll < 0.4 ? 2 : 1)
        case .legendary:
            return roll < 0.18 ? 6 : (roll < 0.5 ? 4 : 2)
        }
    }

    private static func robotPriceMultiplier(state: inout MarketState, quality: ItemQuality, seed: String) -> Double {
        let roll = deterministicDouble(seed: seed)
        if state.discountedFindsToday < 2 && quality != .legendary && roll < 0.06 {
            state.discountedFindsToday += 1
            return 0.72 + deterministicDouble(seed: "\(seed)-deal") * 0.14
        }
        if roll < 0.18 { return 0.9 + deterministicDouble(seed: "\(seed)-fair-low") * 0.12 }
        if roll < 0.78 { return 1.0 + deterministicDouble(seed: "\(seed)-fair") * 0.22 }
        if roll < 0.94 { return 1.22 + deterministicDouble(seed: "\(seed)-pricey") * 0.28 }
        return 1.55 + deterministicDouble(seed: "\(seed)-luxury") * 0.45
    }

    private static func buyerCandidate(for item: Item, price: Int, playerLevel: Int, now: Date) -> MarketRobotProfile {
        let candidates = MarketRobotProfile.all.filter { robot in
            let robotLevel = max(1, playerLevel + robot.levelOffset)
            return item.requiredLevel <= robotLevel + 4
                && item.requiredLevel >= max(1, robotLevel - 14)
                && Double(price) <= Double(robot.dailyBudget) * max(1, Double(playerLevel) / 8.0)
        }
        let pool = candidates.isEmpty ? MarketRobotProfile.all : candidates
        let index = deterministicInt(seed: "\(item.id.uuidString)-\(Int(now.timeIntervalSince1970 / lightRefreshInterval))-buyer", upperBound: pool.count)
        return pool[index]
    }

    private static func saleProbability(listing: MarketListing, robot: MarketRobotProfile, state: MarketState, now: Date) -> Double {
        let attractiveness = MarketPricing.purchaseAttractiveness(item: listing.item, robot: robot, state: state)
        let priceReason = Double(listing.baseValue) / Double(max(1, listing.listPrice))
        let demand = MarketPricing.demandMultiplier(for: listing.item, state: state)
        var probability = attractiveness * priceReason * robot.buyIntent * demand * 1.08

        let priceRatio = Double(listing.listPrice) / Double(max(1, listing.baseValue))
        if priceRatio <= 0.9 { probability *= 1.55 }
        if priceRatio > 0.9 && priceRatio <= 1.05 { probability *= 1.25 }
        if priceRatio > 1.12 { probability *= 0.72 }
        if priceRatio > 1.25 { probability *= 0.55 }
        if priceRatio > 1.35 { probability *= 0.28 }
        if priceRatio < 0.85 { probability *= 1.35 }

        if listing.item.marketOrigin == .robotMarket { probability *= 0.3 }
        if state.playerDailyMarketIncome > state.dailyIncomeSoftCap { probability *= 0.5 }

        let timeBoost = min(0.42, Double(listing.failCount) * 0.16)
        let attentionBoost = min(0.22, listing.attentionScore * 0.2)
        return (probability + timeBoost + attentionBoost).clamped(to: 0.12...0.97)
    }

    private static func minimumWait(for listing: MarketListing) -> TimeInterval {
        let base: TimeInterval
        switch listing.item.quality {
        case .common, .uncommon:
            base = 10
        case .rare:
            base = 15
        case .epic:
            base = 25
        case .legendary:
            base = 35
        }

        let priceRatio = Double(listing.listPrice) / Double(max(1, listing.baseValue))
        if priceRatio > 1.35 { return base + 180 }
        if priceRatio > 1.2 { return base + 90 }
        return base
    }

    private static func resetDailyIfNeeded(state: inout MarketState, playerLevel: Int, now: Date) {
        guard now.timeIntervalSince(state.lastDailyReset) >= 24 * 60 * 60 else {
            state.dailyIncomeSoftCap = dailyIncomeSoftCap(playerLevel: playerLevel)
            return
        }

        state.lastDailyReset = now
        state.daySeed = Int(now.timeIntervalSince1970) % 1_000_000
        state.freeRefreshCount = 3
        state.paidRefreshCount = 0
        state.playerDailyMarketIncome = 0
        state.dailyIncomeSoftCap = dailyIncomeSoftCap(playerLevel: playerLevel)
        state.discountedFindsToday = 0
        state.hotTypes = generateHotTypes(seed: state.daySeed)
        state.demandMap = generateDemandMap(hotTypes: state.hotTypes)
        appendActivity("商会公告更新：今日热门 \(state.hotTypes.map(\.displayName).joined(separator: "、"))", important: false, state: &state, now: now)
    }

    private static func dailyIncomeSoftCap(playerLevel: Int) -> Int {
        max(800, playerLevel * 420)
    }

    private static func generateHotTypes(seed: Int) -> [ItemType] {
        let equipmentTypes: [ItemType] = [.weapon, .helmet, .armor, .legs, .boots, .gloves, .necklace, .ring]
        let first = deterministicInt(seed: "\(seed)-hot-a", upperBound: equipmentTypes.count)
        var second = deterministicInt(seed: "\(seed)-hot-b", upperBound: equipmentTypes.count)
        if second == first {
            second = (second + 3) % equipmentTypes.count
        }
        return [equipmentTypes[first], equipmentTypes[second]]
    }

    private static func generateDemandMap(hotTypes: [ItemType]) -> [String: Double] {
        var map: [String: Double] = [:]
        for type in [ItemType.weapon, .helmet, .armor, .legs, .boots, .gloves, .necklace, .ring] {
            map[type.rawValue] = hotTypes.contains(type) ? 1.18 : 0.95
        }
        return map
    }

    private static func robotListingTarget(playerLevel: Int, state: MarketState, now: Date) -> Int {
        let base: Int
        let range: ClosedRange<Int>
        if playerLevel < 10 {
            base = 980
            range = 860...1_120
        } else if playerLevel < 30 {
            base = 1_180
            range = 1_050...1_360
        } else {
            base = 1_480
            range = 1_300...1_720
        }

        let timeBucket = Int(now.timeIntervalSince1970 / (10 * 60))
        let dailyWave = deterministicInt(seed: "\(state.daySeed)-stock-daily", upperBound: 161) - 80
        let crowdWave = deterministicInt(seed: "\(state.daySeed)-\(timeBucket)-stock-crowd", upperBound: 121) - 60
        return min(max(base + dailyWave + crowdWave, range.lowerBound), range.upperBound)
    }

    private static func robotRefillLimit(activeCount: Int, target: Int) -> Int {
        let missing = max(0, target - activeCount)
        if activeCount < max(120, target / 3) {
            return missing
        }
        return min(missing, max(12, target / 42))
    }

    private static func trimRobotInventoryIfNeeded(state: inout MarketState, target: Int) {
        var activeCount = state.robotListings.filter(\.isActive).count
        let tolerance = max(12, target / 90)
        guard activeCount > target + tolerance else { return }

        let removeCount = activeCount - target
        for _ in 0..<removeCount {
            guard let index = removableRobotListingIndex(state: state) else { return }
            state.robotListings.remove(at: index)
            activeCount -= 1
            if activeCount <= target { return }
        }
    }

    private static func ensureLegendaryInventory(state: inout MarketState, playerLevel: Int, now: Date, target: Int) {
        let quota = legendaryListingQuota(playerLevel: playerLevel, target: target, state: state, now: now)
        var legendaryCount = state.robotListings.filter { $0.isActive && $0.item.quality == .legendary }.count
        guard legendaryCount < quota else { return }

        var activeCount = state.robotListings.filter(\.isActive).count
        var attempts = 0
        while legendaryCount < quota && attempts < quota * 4 {
            attempts += 1
            if activeCount >= target, let index = removableRobotListingIndex(state: state) {
                state.robotListings.remove(at: index)
                activeCount -= 1
            }

            let salt = state.robotListings.count + 10_000 + attempts + Int(now.timeIntervalSince1970 / lightRefreshInterval)
            guard let listing = generateRobotListing(
                state: &state,
                playerLevel: playerLevel,
                now: now,
                salt: salt,
                forcedQuality: .legendary
            ) else {
                continue
            }

            state.robotListings.append(listing)
            activeCount += 1
            legendaryCount += 1
        }
    }

    private static func legendaryListingQuota(playerLevel: Int, target: Int, state: MarketState, now: Date) -> Int {
        let timeBucket = Int(now.timeIntervalSince1970 / (30 * 60))
        let wave = deterministicInt(seed: "\(state.daySeed)-\(timeBucket)-legendary-quota", upperBound: 6)
        if playerLevel < 10 {
            return max(2, target / 220 + wave / 2)
        }
        if playerLevel < 20 {
            return max(5, target / 150 + wave)
        }
        return max(12, target / 80 + wave)
    }

    private static func removableRobotListingIndex(state: MarketState) -> Int? {
        let qualityOrder: [ItemQuality] = [.common, .uncommon, .rare, .epic]
        for quality in qualityOrder {
            if let index = state.robotListings.firstIndex(where: { $0.isActive && $0.item.quality == quality }) {
                return index
            }
        }
        return state.robotListings.firstIndex(where: { $0.isActive && $0.item.quality != .legendary })
    }

    private static func initialAttention(price: Int, recommended: Int, item: Item) -> Double {
        let priceRatio = Double(price) / Double(max(1, recommended))
        let qualityBonus: Double
        switch item.quality {
        case .common: qualityBonus = 0.08
        case .uncommon: qualityBonus = 0.16
        case .rare: qualityBonus = 0.28
        case .epic: qualityBonus = 0.42
        case .legendary: qualityBonus = 0.55
        }
        let priceBonus = max(0.05, 1.25 - priceRatio) * 0.25
        return min(1.0, qualityBonus + priceBonus)
    }

    private static func appendAmbientActivity(state: inout MarketState, playerLevel: Int, now: Date) {
        let typeName = (state.hotTypes.first ?? .weapon).displayName
        let name = npcDisplayName(seed: "\(state.daySeed)-\(Int(now.timeIntervalSince1970))-ambient-name")
        let group = npcDisplayName(seed: "\(state.daySeed)-\(Int(now.timeIntervalSince1970))-ambient-group")
        let messages = [
            "晨星公会正在收购 Lv\(max(1, playerLevel - 2)) 左右的\(typeName)",
            "黑杉商队说今日\(typeName)走货很快",
            "旅法师米娅在看带暴击词条的饰品",
            "商会估价师提醒：高价寄售会明显拉长成交时间",
            "铁靴雷恩刚在货架前挑走了一批防具",
            "灰鹰艾什说今晚的稀有武器成交特别快",
            "藏品师奥林正在寻找带强化等级的饰品",
            "\(name) 在估价台前追加了一张求购单",
            "\(group) 刚把几件旧装备送去复核",
            "大厅看板更新：Lv\(max(1, playerLevel)) 附近的\(typeName)询价变多",
            "\(name) 试穿护腿后又回头看了一眼价格牌",
            "\(group) 的跑腿员在柜台排队结账",
            "商会书记把一批低价货挪到了前排",
            "\(name) 正在和估价师讨价还价"
        ]
        let index = deterministicInt(seed: "\(state.daySeed)-\(Int(now.timeIntervalSince1970))-ambient", upperBound: messages.count)
        appendActivity(messages[index], important: false, state: &state, now: now)
    }

    private static func npcSellerName(archetype: MarketRobotProfile, seed: String) -> String {
        let roll = deterministicDouble(seed: seed)
        if roll < 0.08 {
            return archetype.name
        }
        if roll < 0.28 {
            return stallNames[deterministicInt(seed: "\(seed)-stall", upperBound: stallNames.count)]
        }
        return npcDisplayName(seed: seed)
    }

    private static func npcBuyerName(archetype: MarketRobotProfile, seed: String) -> String {
        let roll = deterministicDouble(seed: seed)
        if roll < 0.06 {
            return archetype.name
        }
        return npcDisplayName(seed: seed)
    }

    private static func npcDisplayName(seed: String) -> String {
        let roll = deterministicDouble(seed: seed)
        if roll < 0.24 {
            return npcGroups[deterministicInt(seed: "\(seed)-group", upperBound: npcGroups.count)]
        }
        return npcNames[deterministicInt(seed: "\(seed)-name", upperBound: npcNames.count)]
    }

    private static func playerSaleActivity(buyer: String, item: Item, seed: String) -> String {
        let messages = [
            "\(buyer) 在寄售柜买下了你的 \(item.displayName)",
            "\(buyer) 试价后直接带走了你的 \(item.displayName)",
            "商会书记刚把你的 \(item.displayName) 交给 \(buyer)",
            "\(buyer) 看中词条，拍下了你的 \(item.displayName)",
            "柜台成交：\(buyer) 买走你的 \(item.displayName)"
        ]
        return messages[deterministicInt(seed: seed, upperBound: messages.count)]
    }

    private static func npcTradeActivity(buyer: String, seller: String, item: Item, seed: String) -> String {
        let messages = [
            "\(buyer) 从 \(seller) 手里挑走了 \(item.type.displayName)",
            "\(seller) 的 \(item.quality.displayName)货被 \(buyer) 买下",
            "\(buyer) 和 \(seller) 在柜台完成结算",
            "\(buyer) 刚从 \(seller) 那里收了一件 \(item.type.displayName)",
            "跑堂喊号：\(seller) 的货交给了 \(buyer)"
        ]
        return messages[deterministicInt(seed: seed, upperBound: messages.count)]
    }

    private static func appendRecord(
        buyer: String,
        seller: String,
        item: String,
        price: Int,
        isPlayerRelated: Bool,
        type: MarketRecordType,
        state: inout MarketState,
        now: Date
    ) {
        state.tradeRecords.insert(
            MarketTradeRecord(
                id: UUID(),
                time: now,
                buyerName: buyer,
                sellerName: seller,
                itemName: item,
                price: price,
                isPlayerRelated: isPlayerRelated,
                recordType: type
            ),
            at: 0
        )
    }

    private static func appendActivity(_ text: String, important: Bool, state: inout MarketState, now: Date) {
        state.activities.insert(
            MarketActivity(id: UUID(), time: now, text: text, isImportant: important),
            at: 0
        )
    }

    private static func trimMarketState(_ state: inout MarketState) {
        if state.tradeRecords.count > maxTradeRecords {
            state.tradeRecords = Array(state.tradeRecords.prefix(maxTradeRecords))
        }
        if state.activities.count > maxActivities {
            state.activities = Array(state.activities.prefix(maxActivities))
        }
    }

    private static func costText(for state: MarketState) -> String {
        if state.freeRefreshCount > 0 {
            return "市场已刷新，今日免费刷新剩余 \(state.freeRefreshCount) 次"
        }
        return "市场已刷新，下次刷新需 \(manualRefreshCost(state: state)) 金"
    }

    private static func deterministicDouble(seed: String) -> Double {
        var rng = SeededRandom(seed: stableHash(seed))
        return rng.nextDouble()
    }

    private static func deterministicInt(seed: String, upperBound: Int) -> Int {
        guard upperBound > 0 else { return 0 }
        var rng = SeededRandom(seed: stableHash(seed))
        return rng.nextInt(upperBound: upperBound)
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

private struct SeededRandom {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed == 0 ? 0x9E37_79B9_7F4A_7C15 : seed
    }

    mutating func nextDouble() -> Double {
        Double(next() % 10_000) / 10_000.0
    }

    mutating func nextInt(upperBound: Int) -> Int {
        Int(next() % UInt64(upperBound))
    }

    private mutating func next() -> UInt64 {
        state ^= state >> 12
        state ^= state << 25
        state ^= state >> 27
        return state &* 2_685_821_657_736_338_717
    }
}

private extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
