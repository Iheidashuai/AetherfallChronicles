import SwiftUI

struct HomeView: View {
    @EnvironmentObject var gameState: GameState

    var player: PlayerData? { gameState.player }

    var combatPower: Int {
        InventorySystem.combatPower(gameState: gameState)
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.12), Color(red: 0.08, green: 0.02, blue: 0.15)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                // Character info card
                if let player = player {
                    VStack(spacing: 12) {
                        // Avatar placeholder + name
                        HStack(spacing: 16) {
                            // Avatar
                            ZStack {
                                Circle()
                                    .fill(Color.cyan.opacity(0.2))
                                    .frame(width: 64, height: 64)
                                Circle()
                                    .stroke(Color.cyan.opacity(0.6), lineWidth: 2)
                                    .frame(width: 64, height: 64)
                                Text("⚔️")
                                    .font(.system(size: 28))
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(player.name)
                                    .font(.title3.bold())
                                    .foregroundColor(.white)

                                Text("Lv.\(player.level) \(player.profession.displayName)")
                                    .font(.subheadline)
                                    .foregroundColor(.cyan)

                                HStack(spacing: 4) {
                                    Text("战力")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                    Text("\(combatPower)")
                                        .font(.caption.bold())
                                        .foregroundColor(.yellow)
                                }
                            }

                            Spacer()
                        }

                        ExpProgressBar(player: player)

                        // HP / MP bars
                        HStack(spacing: 12) {
                            MiniBar(label: "HP", value: player.maxHP + CGFloat(InventorySystem.totalEquipmentHP(gameState: gameState)), max: player.maxHP + CGFloat(InventorySystem.totalEquipmentHP(gameState: gameState)), color: .red)
                            MiniBar(label: "MP", value: player.maxMP + CGFloat(InventorySystem.totalEquipmentMP(gameState: gameState)), max: player.maxMP + CGFloat(InventorySystem.totalEquipmentMP(gameState: gameState)), color: .blue)
                        }

                        // Quick stats
                        HStack(spacing: 20) {
                            QuickStat(icon: "⚔️", label: "攻击", value: "\(Int(player.attack) + InventorySystem.totalEquipmentAttack(gameState: gameState))")
                            QuickStat(icon: "🛡️", label: "防御", value: "\(Int(player.defense) + InventorySystem.totalEquipmentDefense(gameState: gameState))")
                            QuickStat(icon: "💰", label: "金币", value: "\(player.gold)")
                            QuickStat(icon: "📦", label: "背包", value: "\(gameState.inventory.count)/\(InventorySystem.maxSlots)")
                        }

                        HomeEquippedItemsView()
                    }
                    .padding(20)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(16)
                    .padding(.horizontal, 16)
                    .padding(.top, 60)
                }

                // Main action buttons
                VStack(spacing: 16) {
                    // Dungeon button
                    Button(action: { gameState.currentScreen = .dungeonList }) {
                        HStack(spacing: 12) {
                            Text("⚔️")
                                .font(.title2)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("进入副本")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text("选择副本 · 查看掉落")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(16)
                        .background(Color.red.opacity(0.15))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.red.opacity(0.4), lineWidth: 1)
                        )
                    }

                    // Inventory button
                    Button(action: { gameState.currentScreen = .inventory }) {
                        HStack(spacing: 12) {
                            Text("🎒")
                                .font(.title2)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("背包")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text("\(gameState.inventory.count) 件物品")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(16)
                        .background(Color.blue.opacity(0.15))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.blue.opacity(0.4), lineWidth: 1)
                        )
                    }

                    Button(action: { gameState.currentScreen = .quests }) {
                        HStack(spacing: 12) {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: "scroll.fill")
                                    .font(.title2)
                                    .foregroundColor(.yellow)
                                if QuestSystem.hasClaimableRewards(gameState.questState) {
                                    Circle()
                                        .fill(Color.red)
                                        .frame(width: 8, height: 8)
                                        .offset(x: 2, y: -2)
                                }
                            }
                            .frame(width: 28)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("银冠公会委托")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text(QuestSystem.activeSummary(gameState: gameState))
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.68)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(16)
                        .background(Color.yellow.opacity(0.12))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.yellow.opacity(0.35), lineWidth: 1)
                        )
                    }

                    // Market button
                    Button(action: { gameState.currentScreen = .market }) {
                        HStack(spacing: 12) {
                            Image(systemName: "storefront")
                                .font(.title2)
                                .foregroundColor(.yellow)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("冒险者商会")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text("寄售装备 · 浏览市场")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(16)
                        .background(Color.yellow.opacity(0.12))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.yellow.opacity(0.35), lineWidth: 1)
                        )
                    }

                    Button(action: { gameState.currentScreen = .leaderboard }) {
                        HStack(spacing: 12) {
                            Image(systemName: "chart.line.uptrend.xyaxis")
                                .font(.title2)
                                .foregroundColor(.cyan)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("银冠战力榜")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text("冒险者成长 · 每分钟刷新")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(16)
                        .background(Color.cyan.opacity(0.12))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.cyan.opacity(0.35), lineWidth: 1)
                        )
                    }

                    Button(action: {
                        WorldChatSystem.markOpened(gameState: gameState)
                        gameState.currentScreen = .worldChat
                    }) {
                        HStack(spacing: 12) {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: "bubble.left.and.bubble.right.fill")
                                    .font(.title2)
                                    .foregroundColor(.green)
                                if WorldChatSystem.hasUnread(gameState.chatState) {
                                    Circle()
                                        .fill(Color.red)
                                        .frame(width: 8, height: 8)
                                        .offset(x: 2, y: -2)
                                }
                            }
                            .frame(width: 28)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("公会传讯水晶")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Text(latestChatPreview)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.68)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(16)
                        .background(Color.green.opacity(0.12))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.green.opacity(0.35), lineWidth: 1)
                        )
                    }
                }
                .padding(.horizontal, 16)

                // Bottom: logout
                Button(action: {
                    gameState.logout()
                }) {
                    Text("退出登录")
                        .font(.subheadline)
                        .foregroundColor(.red.opacity(0.7))
                }
                .padding(.bottom, 40)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .onAppear {
            MarketSystem.bootstrap(gameState: gameState)
            RobotLeaderboardSystem.bootstrap(gameState: gameState)
            WorldChatSystem.bootstrap(gameState: gameState)
            QuestSystem.bootstrap(gameState: gameState)
        }
    }

    private var latestChatPreview: String {
        guard let message = gameState.chatState.messages.last else {
            return "冒险者留言 · 商会与榜单传闻"
        }
        return "\(message.senderName)：\(message.text)"
    }
}

struct ExpProgressBar: View {
    let player: PlayerData

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text("EXP")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.cyan)
                    .frame(width: 28, alignment: .leading)

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Rectangle().fill(Color.gray.opacity(0.2))
                        Rectangle()
                            .fill(Color.cyan)
                            .frame(width: geo.size.width * CGFloat(player.experienceProgress))
                    }
                    .cornerRadius(3)
                }
                .frame(height: 8)

                Text(progressText)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundColor(.white.opacity(0.7))
                    .lineLimit(1)
                    .minimumScaleFactor(0.65)
                    .frame(width: 76, alignment: .trailing)
            }
        }
    }

    private var progressText: String {
        player.level >= PlayerData.maxLevel ? "满级" : "\(player.experience)/\(player.experienceToNextLevel)"
    }
}

struct MiniBar: View {
    let label: String
    let value: CGFloat
    let max: CGFloat
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(color)
                .frame(width: 22)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle().fill(Color.gray.opacity(0.2))
                    Rectangle().fill(color)
                        .frame(width: geo.size.width * (max > 0 ? value / max : 0))
                }
                .cornerRadius(3)
            }
            .frame(height: 8)
            Text("\(Int(value))")
                .font(.system(size: 9))
                .foregroundColor(.white.opacity(0.7))
        }
    }
}

struct HomeEquippedItemsView: View {
    @EnvironmentObject var gameState: GameState

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("正在穿戴")
                    .font(.caption.bold())
                    .foregroundColor(.gray)

                Spacer()

                Button(action: { gameState.currentScreen = .inventory }) {
                    HStack(spacing: 3) {
                        Text("\(gameState.equippedItems.count)/\(EquipSlot.allCases.count)")
                        Image(systemName: "chevron.right")
                    }
                    .font(.caption2.bold())
                    .foregroundColor(.cyan)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(EquipSlot.allCases, id: \.self) { slot in
                        HomeEquipChip(slot: slot, item: gameState.equippedItems[slot])
                            .onTapGesture {
                                gameState.currentScreen = .inventory
                            }
                    }
                }
            }
        }
        .padding(.top, 2)
    }
}

struct HomeEquipChip: View {
    let slot: EquipSlot
    let item: Item?

    var body: some View {
        VStack(spacing: 3) {
            RoundedRectangle(cornerRadius: 6)
                .fill(item?.quality.color.opacity(0.18) ?? Color.white.opacity(0.05))
                .frame(width: 46, height: 46)
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(item?.quality.color.opacity(0.7) ?? Color.gray.opacity(0.28), lineWidth: 1)
                )
                .overlay(
                    VStack(spacing: 0) {
                        Text(item?.compactDisplayName ?? "")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(item?.quality.color ?? .gray)
                            .lineLimit(2)
                            .minimumScaleFactor(0.6)
                            .multilineTextAlignment(.center)
                            .frame(width: 40)
                        if let item, item.enhancementLevel > 0 {
                            Text("+\(item.enhancementLevel)")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundColor(.yellow)
                        }
                    }
                )

            Text(slotLabel)
                .font(.system(size: 8))
                .foregroundColor(.gray)
        }
        .frame(width: 52)
    }

    private var slotLabel: String {
        switch slot {
        case .weapon: return "武"
        case .helmet: return "盔"
        case .armor: return "甲"
        case .legs: return "腿"
        case .boots: return "靴"
        case .gloves: return "手"
        case .necklace: return "链"
        case .ring1: return "戒1"
        case .ring2: return "戒2"
        }
    }
}

struct QuickStat: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(icon)
                .font(.system(size: 16))
            Text(value)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
            Text(label)
                .font(.system(size: 9))
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

struct DungeonListView: View {
    @EnvironmentObject var gameState: GameState

    private var dungeons: [DungeonConfig] {
        ConfigLoader.shared.dungeonConfigs
    }

    private var playerLevel: Int {
        gameState.player?.level ?? 1
    }

    private var playerPower: Int {
        InventorySystem.combatPower(gameState: gameState)
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.12), Color(red: 0.08, green: 0.02, blue: 0.15)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                HeaderBar(title: "副本大厅", trailing: "战力 \(playerPower)") {
                    gameState.currentScreen = .home
                }

                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 12) {
                        ForEach(dungeons, id: \.id) { dungeon in
                            DungeonCardView(
                                dungeon: dungeon,
                                playerLevel: playerLevel,
                                playerPower: playerPower
                            ) {
                                gameState.selectDungeon(dungeon)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 32)
                }
            }
        }
    }
}

struct DungeonDetailView: View {
    @EnvironmentObject var gameState: GameState

    private var dungeon: DungeonConfig {
        gameState.selectedDungeon()
    }

    private var playerLevel: Int {
        gameState.player?.level ?? 1
    }

    private var playerPower: Int {
        InventorySystem.combatPower(gameState: gameState)
    }

    private var monsters: [DungeonMonsterSummary] {
        DungeonInfoProvider.monsters(in: dungeon)
    }

    private var drops: [DungeonDropSummary] {
        DungeonInfoProvider.drops(in: dungeon)
    }

    private var isLowLevel: Bool {
        playerLevel < dungeon.recommendedLevel
    }

    private var isLowPower: Bool {
        playerPower < dungeon.recommendedPower
    }

    private var hasCompleted: Bool {
        gameState.hasCompletedDungeon(dungeon)
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.12), Color(red: 0.08, green: 0.02, blue: 0.15)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                HeaderBar(title: dungeon.name, trailing: dungeon.difficulty) {
                    gameState.currentScreen = .dungeonList
                }

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 14) {
                        DungeonHeroView(
                            dungeon: dungeon,
                            playerLevel: playerLevel,
                            playerPower: playerPower
                        )

                        if isLowLevel || isLowPower {
                            RiskWarningView(isLowLevel: isLowLevel, isLowPower: isLowPower)
                        }

                        DetailSection(title: "怪物信息") {
                            VStack(spacing: 8) {
                                ForEach(monsters) { monster in
                                    MonsterSummaryRow(summary: monster)
                                }
                            }
                        }

                        DetailSection(title: "可能掉落") {
                            if drops.isEmpty {
                                Text("暂无掉落")
                                    .font(.subheadline)
                                    .foregroundColor(.gray)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                VStack(spacing: 8) {
                                    ForEach(drops) { drop in
                                        DropSummaryRow(summary: drop)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, hasCompleted ? 156 : 108)
                }
                .safeAreaInset(edge: .bottom) {
                    Color.clear.frame(height: hasCompleted ? 132 : 84)
                }
            }

            VStack {
                Spacer()
                VStack(spacing: 10) {
                    if hasCompleted {
                        Button(action: {
                            gameState.sweepSelectedDungeon(times: 10)
                        }) {
                            Text("扫荡10次")
                                .font(.headline)
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(Color.yellow.opacity(0.9))
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.yellow, lineWidth: 1)
                                )
                        }
                    }

                    Button(action: {
                        gameState.challengeSelectedDungeon()
                    }) {
                        Text(hasCompleted ? "再次挑战" : "挑战")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color.red.opacity(0.75))
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.red.opacity(0.9), lineWidth: 1)
                            )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 34)
                .padding(.top, 12)
                .background(Color.black.opacity(0.55))
            }
        }
    }
}

private struct HeaderBar: View {
    let title: String
    let trailing: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .foregroundColor(.gray)
                    .frame(width: 44, height: 44)
            }

            Text(title)
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)

            Text(trailing)
                .font(.caption.bold())
                .foregroundColor(.yellow)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: 72, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.top, 50)
    }
}

private struct DungeonCardView: View {
    let dungeon: DungeonConfig
    let playerLevel: Int
    let playerPower: Int
    let onSelect: () -> Void

    private var drops: [DungeonDropSummary] {
        Array(DungeonInfoProvider.drops(in: dungeon).prefix(3))
    }

    private var isRisky: Bool {
        playerLevel < dungeon.recommendedLevel || playerPower < dungeon.recommendedPower
    }

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Text("⚔️")
                        .font(.title2)

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 8) {
                            Text(dungeon.name)
                                .font(.headline)
                                .foregroundColor(.white)
                            Text(dungeon.difficulty)
                                .font(.caption.bold())
                                .foregroundColor(.cyan)
                        }
                        Text("推荐 Lv.\(dungeon.recommendedLevel) · 战力 \(dungeon.recommendedPower)")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }

                    Spacer()

                    Text(isRisky ? "高风险" : "适合")
                        .font(.caption.bold())
                        .foregroundColor(isRisky ? .orange : .green)
                }

                Text(dungeon.description)
                    .font(.caption)
                    .foregroundColor(.gray)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                HStack(spacing: 6) {
                    Text("主要掉落")
                        .font(.caption2)
                        .foregroundColor(.gray)

                    ForEach(drops) { drop in
                        Text(drop.template.name)
                            .font(.caption2.bold())
                            .foregroundColor(drop.template.quality.color)
                            .lineLimit(1)
                    }

                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundColor(.gray)
                }
            }
            .padding(14)
            .background(Color.white.opacity(0.05))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isRisky ? Color.orange.opacity(0.45) : Color.white.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct DungeonHeroView: View {
    let dungeon: DungeonConfig
    let playerLevel: Int
    let playerPower: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(dungeon.name)
                        .font(.title2.bold())
                        .foregroundColor(.white)
                    Text(dungeon.description)
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                Spacer()
                Text(dungeon.difficulty)
                    .font(.caption.bold())
                    .foregroundColor(.cyan)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.cyan.opacity(0.12))
                    .cornerRadius(8)
            }

            HStack(spacing: 12) {
                DetailMetric(label: "推荐等级", value: "Lv.\(dungeon.recommendedLevel)", isWarning: playerLevel < dungeon.recommendedLevel)
                DetailMetric(label: "推荐战力", value: "\(dungeon.recommendedPower)", isWarning: playerPower < dungeon.recommendedPower)
                DetailMetric(label: "当前战力", value: "\(playerPower)", isWarning: playerPower < dungeon.recommendedPower)
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.05))
        .cornerRadius(14)
    }
}

private struct DetailMetric: View {
    let label: String
    let value: String
    let isWarning: Bool

    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundColor(isWarning ? .orange : .yellow)
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct RiskWarningView: View {
    let isLowLevel: Bool
    let isLowPower: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if isLowLevel {
                Text("等级低于推荐，怪物伤害压力较高。")
            }
            if isLowPower {
                Text("战力低于推荐，通关时间和死亡次数可能增加。")
            }
        }
        .font(.caption)
        .foregroundColor(.orange)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.orange.opacity(0.1))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.orange.opacity(0.3), lineWidth: 1)
        )
    }
}

private struct DetailSection<Content: View>: View {
    let title: String
    let content: Content

    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(.white)
            content
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.05))
        .cornerRadius(12)
    }
}

private struct MonsterSummaryRow: View {
    let summary: DungeonMonsterSummary

    var body: some View {
        HStack(spacing: 10) {
            Text(summary.config.isBoss ? "B" : "M")
                .font(.caption.bold())
                .foregroundColor(summary.config.isBoss ? .purple : .cyan)
                .frame(width: 28, height: 28)
                .background((summary.config.isBoss ? Color.purple : Color.cyan).opacity(0.15))
                .cornerRadius(6)

            VStack(alignment: .leading, spacing: 2) {
                Text(summary.config.name)
                    .font(.subheadline.bold())
                    .foregroundColor(.white)
                Text("Lv.\(summary.config.level) · HP \(Int(summary.config.maxHP)) · 力量 \(summary.config.strength)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }

            Spacer()

            Text("x\(summary.count)")
                .font(.caption.bold())
                .foregroundColor(.yellow)
        }
    }
}

private struct DropSummaryRow: View {
    let summary: DungeonDropSummary

    var body: some View {
        HStack(spacing: 10) {
            Text(summary.template.compactDisplayName)
                .font(.caption.bold())
                .foregroundColor(summary.template.quality.color)
                .frame(width: 28, height: 28)
                .lineLimit(1)
                .minimumScaleFactor(0.45)
                .background(summary.template.quality.color.opacity(0.15))
                .cornerRadius(6)
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(summary.template.quality.color.opacity(0.6), lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(summary.template.name)
                    .font(.subheadline.bold())
                    .foregroundColor(summary.template.quality.color)
                Text("\(summary.template.quality.displayName) · \(summary.template.type.displayName) · Lv.\(summary.template.requiredLevel)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }

            Spacer()

            Text("\(Int(summary.dropRate * 100))%")
                .font(.caption.bold())
                .foregroundColor(.white.opacity(0.8))
        }
    }
}

private struct DungeonMonsterSummary: Identifiable {
    let config: MonsterConfig
    let count: Int

    var id: String { config.id }
}

private struct DungeonDropSummary: Identifiable {
    let template: ItemTemplate
    let dropRate: Double

    var id: String { template.id }
}

private enum DungeonInfoProvider {
    static func monsters(in dungeon: DungeonConfig) -> [DungeonMonsterSummary] {
        var counts: [String: Int] = [:]
        for room in dungeon.rooms {
            for monster in room.monsters {
                counts[monster.monsterId, default: 0] += monster.count
            }
        }

        return ConfigLoader.shared.monsterConfigs
            .filter { counts[$0.id] != nil }
            .map { DungeonMonsterSummary(config: $0, count: counts[$0.id] ?? 0) }
            .sorted {
                if $0.config.isBoss != $1.config.isBoss {
                    return !$0.config.isBoss && $1.config.isBoss
                }
                return $0.config.level < $1.config.level
            }
    }

    static func drops(in dungeon: DungeonConfig) -> [DungeonDropSummary] {
        let monsterIds = Set(dungeon.rooms.flatMap { room in
            room.monsters.map { $0.monsterId }
        })
        let monsters = ConfigLoader.shared.monsterConfigs.filter { monsterIds.contains($0.id) }
        var dropRates: [String: Double] = [:]

        for monster in monsters {
            for entry in monster.lootTable {
                dropRates[entry.itemId] = max(dropRates[entry.itemId] ?? 0, entry.dropRate)
            }
        }

        return ConfigLoader.shared.itemTemplates
            .filter { dropRates[$0.id] != nil }
            .map { DungeonDropSummary(template: $0, dropRate: dropRates[$0.id] ?? 0) }
            .sorted {
                if qualityRank($0.template.quality) != qualityRank($1.template.quality) {
                    return qualityRank($0.template.quality) > qualityRank($1.template.quality)
                }
                if $0.template.requiredLevel != $1.template.requiredLevel {
                    return $0.template.requiredLevel > $1.template.requiredLevel
                }
                return $0.dropRate > $1.dropRate
            }
    }

    private static func qualityRank(_ quality: ItemQuality) -> Int {
        switch quality {
        case .common: return 1
        case .uncommon: return 2
        case .rare: return 3
        case .epic: return 4
        case .legendary: return 5
        }
    }
}
