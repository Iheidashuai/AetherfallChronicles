import SwiftUI

struct MarketView: View {
    @EnvironmentObject var gameState: GameState
    @State private var selectedTab: MarketTab = .market
    @State private var selectedListingItem: Item?
    @State private var statusMessage: String?
    private let livePulse = Timer.publish(every: 12, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.06, green: 0.06, blue: 0.10), Color(red: 0.09, green: 0.04, blue: 0.13)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 10) {
                    MarketHeaderView()

                    MarketSummaryView()
                        .padding(.horizontal, 16)

                    MarketTabsView(selectedTab: $selectedTab)
                        .padding(.horizontal, 16)

                    MarketLiveStrip()
                        .padding(.horizontal, 16)

                    if let statusMessage {
                        Text(statusMessage)
                            .font(.caption2)
                            .foregroundColor(.cyan)
                            .lineLimit(2)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 18)
                    }

                    Group {
                        switch selectedTab {
                        case .market:
                            MarketBrowseTab(statusMessage: $statusMessage)
                        case .sell:
                            MarketSellTab(selectedListingItem: $selectedListingItem, statusMessage: $statusMessage)
                        case .demand:
                            MarketDemandTab()
                        case .records:
                            MarketRecordsTab()
                        }
                    }
                    .layoutPriority(1)
                }
                .padding(.bottom, 30)
            }
        }
        .onAppear {
            let result = MarketSystem.refreshMarket(gameState: gameState)
            QuestSystem.record(.marketViewed, gameState: gameState)
            if statusMessage == nil && !result.success {
                statusMessage = result.message
            }
        }
        .onReceive(livePulse) { now in
            let result = MarketSystem.pulseMarket(gameState: gameState, now: now)
            if !result.success {
                statusMessage = result.message
            }
        }
        .sheet(item: $selectedListingItem) { item in
            MarketListItemSheet(item: item, statusMessage: $statusMessage)
                .environmentObject(gameState)
        }
    }
}

private enum MarketTab: CaseIterable, Hashable {
    case market
    case sell
    case demand
    case records

    var title: String {
        switch self {
        case .market: return "市场"
        case .sell: return "出售"
        case .demand: return "求购"
        case .records: return "成交"
        }
    }

    var icon: String {
        switch self {
        case .market: return "storefront"
        case .sell: return "tag"
        case .demand: return "person.2"
        case .records: return "list.bullet.rectangle"
        }
    }
}

private struct MarketHeaderView: View {
    @EnvironmentObject var gameState: GameState

    var body: some View {
        HStack(spacing: 8) {
            Button(action: { gameState.currentScreen = .home }) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .foregroundColor(.gray)
                    .frame(width: 44, height: 44)
            }

            VStack(spacing: 2) {
                Text("冒险者商会")
                    .font(.headline)
                    .foregroundColor(.white)
                Text("热度 \(marketHeat)")
                    .font(.caption2)
                    .foregroundColor(.cyan)
            }
            .frame(maxWidth: .infinity)

            Text("\(gameState.player?.gold ?? 0) 金")
                .font(.caption.bold())
                .foregroundColor(.yellow)
                .lineLimit(1)
                .minimumScaleFactor(0.65)
                .frame(width: 72, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.top, 50)
    }

    private var marketHeat: String {
        let active = gameState.marketState.robotListings.filter(\.isActive).count
        if active >= 28 { return "沸腾" }
        if active >= 18 { return "活跃" }
        return "平稳"
    }
}

private struct MarketSummaryView: View {
    @EnvironmentObject var gameState: GameState

    private var activeRobotListings: Int {
        gameState.marketState.robotListings.filter(\.isActive).count
    }

    private var activePlayerListings: Int {
        gameState.marketState.playerListings.filter { $0.status == .listed }.count
    }

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                MarketMetric(label: "货架", value: "\(activeRobotListings)")
                MarketMetric(label: "我的寄售", value: "\(activePlayerListings)/\(MarketSystem.playerListingLimit(playerLevel: gameState.player?.level ?? 1))")
                MarketMetric(label: "今日成交", value: "\(gameState.marketState.tradeRecords.count)")
                MarketMetric(label: "今日收益", value: "\(gameState.marketState.playerDailyMarketIncome)")
            }

            HStack(spacing: 8) {
                Image(systemName: "flame")
                    .foregroundColor(.orange)
                Text("热门 \(hotTypesText)")
                    .font(.caption.bold())
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)

                Spacer()

                Text("常驻货架")
                    .font(.caption.bold())
                    .foregroundColor(.cyan)
            }

            HStack(spacing: 8) {
                Image(systemName: "arrow.left.arrow.right")
                    .foregroundColor(.green)
                Text(latestTradeText)
                    .font(.caption2.bold())
                    .foregroundColor(.white.opacity(0.82))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Spacer()
            }
            .padding(.top, 2)
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var hotTypesText: String {
        gameState.marketState.hotTypes.map(\.displayName).joined(separator: " / ")
    }

    private var latestTradeText: String {
        guard let record = gameState.marketState.tradeRecords.first else {
            return "商会大厅正在撮合买家和卖家"
        }
        switch record.recordType {
        case .listing:
            return "\(record.sellerName) 刚上架 \(record.itemName)"
        case .sale:
            return "\(record.buyerName) 买下了你的 \(record.itemName)"
        case .purchase:
            return "你刚买下 \(record.itemName)"
        case .npcTrade:
            return "\(record.buyerName) 刚买走 \(record.itemName)"
        case .notice:
            return record.itemName
        }
    }
}

private struct MarketMetric: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundColor(.yellow)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct MarketTabsView: View {
    @Binding var selectedTab: MarketTab

    var body: some View {
        HStack(spacing: 8) {
            ForEach(MarketTab.allCases, id: \.self) { tab in
                Button(action: { selectedTab = tab }) {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 13, weight: .semibold))
                        Text(tab.title)
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundColor(selectedTab == tab ? .black : .white)
                    .frame(maxWidth: .infinity, minHeight: 42)
                    .background(selectedTab == tab ? Color.cyan : Color.white.opacity(0.08))
                    .cornerRadius(8)
                }
            }
        }
    }
}

private struct MarketLiveStrip: View {
    @EnvironmentObject var gameState: GameState

    private var activities: [MarketActivity] {
        Array(gameState.marketState.activities.prefix(2))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Circle()
                    .fill(Color.green)
                    .frame(width: 7, height: 7)
                Text("实时动态")
                    .font(.caption2.bold())
                    .foregroundColor(.green)
                Spacer()
                Text("自动撮合中")
                    .font(.caption2.bold())
                    .foregroundColor(.gray)
            }

            if activities.isEmpty {
                Text("商会大厅正在寻找合适买家")
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.75))
                    .lineLimit(1)
            } else {
                ForEach(activities) { activity in
                    HStack(spacing: 6) {
                        Image(systemName: activity.isImportant ? "bolt.fill" : "arrow.left.arrow.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(activity.isImportant ? .yellow : .cyan)
                            .frame(width: 14)
                        Text(activity.text)
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.82))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                        Spacer(minLength: 6)
                        Text(MarketTimeFormatter.relative(activity.time))
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundColor(.gray)
                            .lineLimit(1)
                    }
                }
            }
        }
        .padding(10)
        .background(Color.white.opacity(0.045))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.green.opacity(0.18), lineWidth: 1)
        )
    }
}

private struct MarketFilterBar: View {
    @Binding var selectedType: ItemType?
    @Binding var selectedQuality: ItemQuality?
    let filterTypes: [ItemType]
    let resultCount: Int

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Menu {
                    Button("全部部位") { selectedType = nil }
                    ForEach(filterTypes, id: \.self) { type in
                        Button(type.displayName) { selectedType = type }
                    }
                } label: {
                    MarketFilterChip(
                        icon: "square.grid.2x2",
                        title: selectedType?.displayName ?? "全部部位",
                        isActive: selectedType != nil
                    )
                }

                Menu {
                    Button("全部品质") { selectedQuality = nil }
                    ForEach(ItemQuality.allCases, id: \.self) { quality in
                        Button(quality.displayName) { selectedQuality = quality }
                    }
                } label: {
                    MarketFilterChip(
                        icon: "sparkles",
                        title: selectedQuality?.displayName ?? "全部品质",
                        isActive: selectedQuality != nil
                    )
                }

                Button(action: {
                    selectedType = nil
                    selectedQuality = nil
                }) {
                    Image(systemName: "xmark.circle")
                        .font(.headline)
                        .foregroundColor(hasFilter ? .orange : .gray)
                        .frame(width: 36, height: 36)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(8)
                }
                .disabled(!hasFilter)
                .opacity(hasFilter ? 1 : 0.45)
            }

            HStack {
                Text(filterSummary)
                    .font(.caption2.bold())
                    .foregroundColor(.gray)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)

                Spacer()

                Text("\(resultCount) 件")
                    .font(.caption2.bold())
                    .foregroundColor(.yellow)
            }
        }
        .padding(10)
        .background(Color.white.opacity(0.045))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var hasFilter: Bool {
        selectedType != nil || selectedQuality != nil
    }

    private var filterSummary: String {
        if !hasFilter {
            return "全部商品"
        }
        let typeText = selectedType?.displayName ?? "全部部位"
        let qualityText = selectedQuality?.displayName ?? "全部品质"
        return "\(typeText) · \(qualityText)"
    }
}

private struct MarketFilterChip: View {
    let icon: String
    let title: String
    let isActive: Bool

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption.bold())
            Text(title)
                .font(.caption.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Image(systemName: "chevron.down")
                .font(.system(size: 9, weight: .bold))
        }
        .foregroundColor(isActive ? .black : .white)
        .frame(maxWidth: .infinity, minHeight: 36)
        .background(isActive ? Color.cyan : Color.white.opacity(0.08))
        .cornerRadius(8)
    }
}

private struct MarketBrowseTab: View {
    @EnvironmentObject var gameState: GameState
    @Binding var statusMessage: String?
    @State private var selectedType: ItemType?
    @State private var selectedQuality: ItemQuality?

    private let filterTypes: [ItemType] = [
        .weapon, .helmet, .armor, .legs, .boots, .gloves, .necklace, .ring
    ]

    private var listings: [MarketListing] {
        gameState.marketState.robotListings
            .filter(\.isActive)
            .filter { listing in
                if let selectedType, listing.item.type != selectedType { return false }
                if let selectedQuality, listing.item.quality != selectedQuality { return false }
                return true
            }
            .sorted { lhs, rhs in
                if lhs.item.quality != rhs.item.quality {
                    return qualityRank(lhs.item.quality) > qualityRank(rhs.item.quality)
                }
                return lhs.listPrice < rhs.listPrice
            }
    }

    var body: some View {
        LazyVStack(spacing: 10) {
            MarketFilterBar(
                selectedType: $selectedType,
                selectedQuality: $selectedQuality,
                filterTypes: filterTypes,
                resultCount: listings.count
            )

            if listings.isEmpty {
                MarketEmptyState(icon: "shippingbox", text: "没有符合筛选的装备")
                    .padding(.top, 36)
            } else {
                ForEach(listings) { listing in
                    MarketListingCard(listing: listing, actionTitle: "购买", actionIcon: "cart") {
                        let result = MarketSystem.buyRobotListing(listing, gameState: gameState)
                        statusMessage = result.message
                    }
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

private struct MarketSellTab: View {
    @EnvironmentObject var gameState: GameState
    @Binding var selectedListingItem: Item?
    @Binding var statusMessage: String?

    private var playerListings: [MarketListing] {
        gameState.marketState.playerListings.sorted { $0.listTime > $1.listTime }
    }

    private var sellableItems: [Item] {
        gameState.inventory
            .filter { $0.type.equipSlot != nil }
            .sorted { lhs, rhs in
                if lhs.requiredLevel != rhs.requiredLevel { return lhs.requiredLevel > rhs.requiredLevel }
                if lhs.quality != rhs.quality { return qualityRank(lhs.quality) > qualityRank(rhs.quality) }
                return lhs.powerScore > rhs.powerScore
            }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !playerListings.isEmpty {
                HStack {
                    Text("我的寄售")
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                    Spacer()
                    Button("清理已成交") {
                        MarketSystem.clearCompletedPlayerListings(gameState: gameState)
                        statusMessage = "已清理成交记录"
                    }
                    .font(.caption.bold())
                    .foregroundColor(.cyan)
                }

                ForEach(playerListings) { listing in
                    MarketPlayerListingCard(listing: listing, statusMessage: $statusMessage)
                }
            }

            Text("可寄售装备")
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .padding(.top, playerListings.isEmpty ? 0 : 6)

            if sellableItems.isEmpty {
                MarketEmptyState(icon: "tray", text: "背包里暂无可寄售装备")
                    .padding(.top, 20)
            } else {
                ForEach(sellableItems) { item in
                    MarketInventorySellRow(item: item) {
                        selectedListingItem = item
                    }
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

private struct MarketDemandTab: View {
    @EnvironmentObject var gameState: GameState

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("今日求购")
                .font(.subheadline.bold())
                .foregroundColor(.white)

            ForEach(Array(MarketRobotProfile.all.prefix(5))) { robot in
                MarketDemandRow(robot: robot)
            }

            Text("商会动态")
                .font(.subheadline.bold())
                .foregroundColor(.white)
                .padding(.top, 8)

            if gameState.marketState.activities.isEmpty {
                MarketEmptyState(icon: "bubble.left.and.bubble.right", text: "商会大厅暂时安静")
                    .padding(.top, 16)
            } else {
                ForEach(gameState.marketState.activities) { activity in
                    MarketActivityRow(activity: activity)
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

private struct MarketRecordsTab: View {
    @EnvironmentObject var gameState: GameState

    var body: some View {
        LazyVStack(spacing: 10) {
            if gameState.marketState.tradeRecords.isEmpty {
                MarketEmptyState(icon: "list.bullet.rectangle", text: "暂无成交记录")
                    .padding(.top, 36)
            } else {
                ForEach(gameState.marketState.tradeRecords) { record in
                    MarketRecordRow(record: record)
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

private struct MarketListingCard: View {
    let listing: MarketListing
    let actionTitle: String
    let actionIcon: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                MarketItemIcon(item: listing.item)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(listing.item.displayName)
                            .font(.subheadline.bold())
                            .foregroundColor(listing.item.quality.color)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)

                        Text(listing.priceTag.displayName)
                            .font(.caption2.bold())
                            .foregroundColor(listing.priceTag.color)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(listing.priceTag.color.opacity(0.12))
                            .cornerRadius(6)
                    }

                    Text("\(listing.item.quality.displayName) \(listing.item.type.displayName) · Lv\(listing.item.requiredLevel)")
                        .font(.caption)
                        .foregroundColor(.gray)

                    Text(statLine(for: listing.item))
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.75))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }

                Spacer()
            }

            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(listing.sellerName)
                        .font(.caption2)
                        .foregroundColor(.gray)
                    Text("\(listing.listPrice) 金")
                        .font(.headline.bold())
                        .foregroundColor(.yellow)
                }

                Spacer()

                Button(action: action) {
                    Label(actionTitle, systemImage: actionIcon)
                        .labelStyle(.titleAndIcon)
                        .font(.caption.bold())
                        .foregroundColor(.black)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.yellow)
                        .cornerRadius(8)
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.055))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(listing.item.quality.color.opacity(0.35), lineWidth: 1)
        )
    }
}

private struct MarketPlayerListingCard: View {
    @EnvironmentObject var gameState: GameState
    let listing: MarketListing
    @Binding var statusMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 10) {
                MarketItemIcon(item: listing.item)

                VStack(alignment: .leading, spacing: 3) {
                    Text(listing.item.displayName)
                        .font(.subheadline.bold())
                        .foregroundColor(listing.item.quality.color)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text(statusText)
                        .font(.caption)
                        .foregroundColor(statusColor)
                    Text("标价 \(listing.listPrice) 金 · 到手 \(listing.netIncome) 金")
                        .font(.caption2)
                        .foregroundColor(.gray)
                }

                Spacer()
            }

            HStack {
                Text("预计成交 \(MarketSystem.saleExpectation(for: listing))")
                    .font(.caption2)
                    .foregroundColor(.cyan)

                Spacer()

                if listing.status == .listed {
                    Button("下架") {
                        let result = MarketSystem.cancelPlayerListing(listing, gameState: gameState)
                        statusMessage = result.message
                    }
                    .buttonStyle(MarketInlineButtonStyle(color: .orange))
                } else if listing.status == .expired {
                    Button("取回") {
                        let result = MarketSystem.retrieveExpiredListing(listing, gameState: gameState)
                        statusMessage = result.message
                    }
                    .buttonStyle(MarketInlineButtonStyle(color: .cyan))
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(statusColor.opacity(0.35), lineWidth: 1)
        )
    }

    private var statusText: String {
        switch listing.status {
        case .listed:
            return "\(listing.priceTag.displayName) · 剩余 \(MarketTimeFormatter.remaining(until: listing.expireTime))"
        case .sold:
            return "\(listing.buyerName ?? "冒险者") 已买下"
        case .expired:
            return "已到期"
        case .canceled:
            return "已下架"
        }
    }

    private var statusColor: Color {
        switch listing.status {
        case .listed: return .cyan
        case .sold: return .green
        case .expired: return .orange
        case .canceled: return .gray
        }
    }
}

private struct MarketInventorySellRow: View {
    @EnvironmentObject var gameState: GameState
    let item: Item
    let onSell: () -> Void

    private var window: MarketPriceWindow {
        MarketPricing.priceWindow(for: item, state: gameState.marketState)
    }

    var body: some View {
        HStack(spacing: 10) {
            MarketItemIcon(item: item)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.displayName)
                    .font(.subheadline.bold())
                    .foregroundColor(item.quality.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text("\(item.quality.displayName) \(item.type.displayName) · Lv\(item.requiredLevel)")
                    .font(.caption)
                    .foregroundColor(.gray)
                Text(item.isMarketLocked() ? "来源核验中" : "推荐 \(window.recommended) 金")
                    .font(.caption2)
                    .foregroundColor(item.isMarketLocked() ? .orange : .yellow)
            }

            Spacer()

            Button(action: onSell) {
                Label("寄售", systemImage: "tag")
                    .labelStyle(.iconOnly)
                    .font(.headline)
                    .foregroundColor(.black)
                    .frame(width: 38, height: 38)
                    .background(item.isMarketLocked() ? Color.gray : Color.cyan)
                    .cornerRadius(8)
            }
            .disabled(item.isMarketLocked())
            .opacity(item.isMarketLocked() ? 0.45 : 1)
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
    }
}

private struct MarketDemandRow: View {
    let robot: MarketRobotProfile

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .font(.headline)
                .foregroundColor(.cyan)
                .frame(width: 34, height: 34)
                .background(Color.cyan.opacity(0.12))
                .cornerRadius(8)

            VStack(alignment: .leading, spacing: 3) {
                Text(robot.name)
                    .font(.subheadline.bold())
                    .foregroundColor(.white)
                Text("求购 \(robot.preferredTypes.prefix(3).map(\.displayName).joined(separator: "、"))")
                    .font(.caption)
                    .foregroundColor(.gray)
            }

            Spacer()

            Text(robot.personality == "merchant" ? "频繁出货" : "正在看货")
                .font(.caption2.bold())
                .foregroundColor(.yellow)
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
    }

    private var iconName: String {
        switch robot.profession {
        case .warrior: return "shield"
        case .ranger: return "scope"
        case .mage: return "sparkles"
        }
    }
}

private struct MarketActivityRow: View {
    let activity: MarketActivity

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Circle()
                .fill(activity.isImportant ? Color.yellow : Color.cyan)
                .frame(width: 7, height: 7)
                .padding(.top, 6)

            VStack(alignment: .leading, spacing: 2) {
                Text(activity.text)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
                Text(MarketTimeFormatter.relative(activity.time))
                    .font(.caption2)
                    .foregroundColor(.gray)
            }

            Spacer()
        }
        .padding(10)
        .background(Color.white.opacity(0.04))
        .cornerRadius(8)
    }
}

private struct MarketRecordRow: View {
    let record: MarketTradeRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 6) {
                Image(systemName: recordIcon)
                    .foregroundColor(record.isPlayerRelated ? .yellow : .cyan)
                Text(recordTitle)
                    .font(.caption.bold())
                    .foregroundColor(record.isPlayerRelated ? .yellow : .white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Spacer()
                Text("\(record.price) 金")
                    .font(.caption.bold())
                    .foregroundColor(.yellow)
            }

            Text(recordDescription)
                .font(.caption2)
                .foregroundColor(.gray)
                .lineLimit(2)
                .minimumScaleFactor(0.75)

            Text(MarketTimeFormatter.relative(record.time))
                .font(.caption2)
                .foregroundColor(.gray.opacity(0.8))
        }
        .padding(12)
        .background(Color.white.opacity(record.isPlayerRelated ? 0.075 : 0.045))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(record.isPlayerRelated ? Color.yellow.opacity(0.25) : Color.white.opacity(0.06), lineWidth: 1)
        )
    }

    private var recordTitle: String {
        switch record.recordType {
        case .sale: return "你的装备成交"
        case .purchase: return "你购买了装备"
        case .npcTrade: return "冒险者交易"
        case .listing: return "新货上架"
        case .notice: return "商会公告"
        }
    }

    private var recordIcon: String {
        switch record.recordType {
        case .sale: return "banknote"
        case .purchase: return "cart"
        case .npcTrade: return "arrow.left.arrow.right"
        case .listing: return "tag"
        case .notice: return "megaphone"
        }
    }

    private var recordDescription: String {
        switch record.recordType {
        case .listing:
            return "\(record.sellerName) 上架了 \(record.itemName)"
        case .notice:
            return record.itemName
        case .sale, .purchase, .npcTrade:
            return "\(record.buyerName) 从 \(record.sellerName) 手中获得 \(record.itemName)"
        }
    }
}

private struct MarketItemIcon: View {
    let item: Item

    var body: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(item.quality.color.opacity(0.18))
            .frame(width: 52, height: 52)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(item.quality.color.opacity(0.65), lineWidth: 1)
            )
            .overlay(
                VStack(spacing: 1) {
                    Image(systemName: iconName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(item.quality.color)
                    Text(item.compactDisplayName)
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(item.quality.color)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                        .frame(width: 44)
                    if item.enhancementLevel > 0 {
                        Text("+\(item.enhancementLevel)")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(.yellow)
                    }
                }
            )
    }

    private var iconName: String {
        switch item.type {
        case .weapon: return "burst"
        case .helmet: return "person.crop.circle"
        case .armor, .legs, .gloves, .boots: return "shield"
        case .necklace, .ring: return "diamond"
        case .consumable: return "cross.case"
        case .material: return "cube"
        case .quest: return "scroll"
        }
    }
}

private struct MarketListItemSheet: View {
    @EnvironmentObject var gameState: GameState
    @Environment(\.dismiss) private var dismiss
    let item: Item
    @Binding var statusMessage: String?
    @State private var selectedPrice: Double = 0

    private var window: MarketPriceWindow {
        MarketPricing.priceWindow(for: item, state: gameState.marketState)
    }

    private var price: Int {
        max(window.minimum, min(window.maximum, Int(selectedPrice.rounded())))
    }

    var body: some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.4))
                .frame(width: 42, height: 4)
                .padding(.top, 10)

            MarketItemIcon(item: item)

            VStack(spacing: 4) {
                Text(item.displayName)
                    .font(.headline.bold())
                    .foregroundColor(item.quality.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text("\(item.quality.displayName) \(item.type.displayName) · Lv\(item.requiredLevel)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }

            VStack(spacing: 8) {
                HStack {
                    Text("推荐价")
                    Spacer()
                    Text("\(window.recommended) 金")
                        .foregroundColor(.yellow)
                }
                HStack {
                    Text("可定价")
                    Spacer()
                    Text("\(window.minimum)-\(window.maximum) 金")
                        .foregroundColor(.cyan)
                }
                HStack {
                    Text("上架费")
                    Spacer()
                    Text("\(MarketPricing.listingFee(for: item, price: price, recommended: window.recommended)) 金")
                        .foregroundColor(.orange)
                }
                HStack {
                    Text("成交到手")
                    Spacer()
                    Text("\(Int(Double(price) * (1 - window.taxRate))) 金")
                        .foregroundColor(.green)
                }
            }
            .font(.caption.bold())
            .foregroundColor(.white)
            .padding(12)
            .background(Color.white.opacity(0.06))
            .cornerRadius(8)

            VStack(spacing: 10) {
                Text("\(price) 金 · \(MarketPricing.priceTag(price: price, recommended: window.recommended).displayName)")
                    .font(.headline.bold())
                    .foregroundColor(.yellow)

                Slider(
                    value: $selectedPrice,
                    in: Double(window.minimum)...Double(window.maximum),
                    step: Double(max(1, window.recommended / 30))
                )
                .tint(.cyan)

                HStack(spacing: 8) {
                    Button("快售") { selectedPrice = Double(window.quickSale) }
                        .buttonStyle(MarketInlineButtonStyle(color: .green))
                    Button("推荐") { selectedPrice = Double(window.recommended) }
                        .buttonStyle(MarketInlineButtonStyle(color: .cyan))
                    Button("高价") { selectedPrice = Double(window.highSale) }
                        .buttonStyle(MarketInlineButtonStyle(color: .orange))
                }
            }

            Button(action: {
                let result = MarketSystem.listItem(item, price: price, gameState: gameState)
                statusMessage = result.message
                if result.success { dismiss() }
            }) {
                Label(item.isMarketLocked() ? "来源核验中" : "确认寄售", systemImage: "tag")
                    .labelStyle(.titleAndIcon)
                    .font(.headline)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(item.isMarketLocked() ? Color.gray : Color.yellow)
                    .cornerRadius(10)
            }
            .disabled(item.isMarketLocked())

            Button("取消") { dismiss() }
                .font(.caption.bold())
                .foregroundColor(.gray)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .background(Color(red: 0.09, green: 0.09, blue: 0.13))
        .presentationDetents([.height(520)])
        .onAppear {
            selectedPrice = Double(window.recommended)
        }
    }
}

private struct MarketInlineButtonStyle: ButtonStyle {
    let color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.caption.bold())
            .foregroundColor(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(color.opacity(configuration.isPressed ? 0.35 : 0.22))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(color.opacity(0.45), lineWidth: 1)
            )
    }
}

private struct MarketEmptyState: View {
    let icon: String
    let text: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(.gray)
            Text(text)
                .font(.caption)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
    }
}

private enum MarketTimeFormatter {
    static func relative(_ date: Date) -> String {
        let seconds = max(0, Int(Date().timeIntervalSince(date)))
        if seconds < 60 { return "刚刚" }
        if seconds < 3600 { return "\(seconds / 60) 分钟前" }
        if seconds < 86_400 { return "\(seconds / 3600) 小时前" }
        return "\(seconds / 86_400) 天前"
    }

    static func remaining(until date: Date) -> String {
        let seconds = max(0, Int(date.timeIntervalSince(Date())))
        if seconds < 60 { return "\(seconds) 秒" }
        if seconds < 3600 { return "\(seconds / 60) 分钟" }
        return "\(seconds / 3600) 小时"
    }
}

private func statLine(for item: Item) -> String {
    var parts: [String] = []
    if item.enhancedAttackBonus > 0 { parts.append("攻 \(item.enhancedAttackBonus)") }
    if item.enhancedDefenseBonus > 0 { parts.append("防 \(item.enhancedDefenseBonus)") }
    if item.enhancedHPBonus > 0 { parts.append("生 \(item.enhancedHPBonus)") }
    if item.enhancedMPBonus > 0 { parts.append("法 \(item.enhancedMPBonus)") }
    if item.enhancedCritBonus > 0 { parts.append(String(format: "暴 %.1f%%", item.enhancedCritBonus * 100)) }
    return parts.isEmpty ? "无额外属性" : parts.joined(separator: " · ")
}

private func qualityRank(_ quality: ItemQuality) -> Int {
    switch quality {
    case .common: return 0
    case .uncommon: return 1
    case .rare: return 2
    case .epic: return 3
    case .legendary: return 4
    }
}
