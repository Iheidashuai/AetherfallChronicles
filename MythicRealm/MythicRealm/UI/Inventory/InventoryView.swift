import SwiftUI

struct InventoryView: View {
    @EnvironmentObject var gameState: GameState
    @State private var selectedItem: Item?
    @State private var showDetail: Bool = false
    @State private var selectedCategory: InventoryCategory = .equipment
    @State private var selectedLevel: Int?
    @State private var statusMessage: String?

    private let columns = [GridItem(.adaptive(minimum: 56, maximum: 64), spacing: 10)]

    private var categoryItems: [Item] {
        gameState.inventory.filter { $0.type.inventoryCategory == selectedCategory }
    }

    private var visibleItems: [Item] {
        categoryItems.filter { item in
            guard let selectedLevel else { return true }
            return item.requiredLevel == selectedLevel
        }
    }

    private var availableLevels: [Int] {
        Array(Set(categoryItems.map(\.requiredLevel))).sorted(by: >)
    }

    var body: some View {
        ZStack {
            Color(red: 0.08, green: 0.08, blue: 0.12)
                .ignoresSafeArea()

            VStack(spacing: 10) {
                // Header
                ZStack {
                    Text("背包 (\(gameState.inventory.count)/\(InventorySystem.maxSlots))")
                        .font(.headline)
                        .foregroundColor(.white)
                        .lineLimit(1)

                    HStack {
                        Button(action: { gameState.currentScreen = .home }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title2)
                                .foregroundColor(.gray)
                        }
                        .frame(width: 44, height: 44, alignment: .leading)

                        Spacer()

                        InventoryGoldBadge(gold: gameState.player?.gold ?? 0)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 42)

                // Equipment slots
                EquipmentSlotsView(selectedItem: $selectedItem, showDetail: $showDetail)

                Divider()
                    .background(Color.gray.opacity(0.6))
                    .padding(.top, 2)

                InventoryTabsView(selectedCategory: $selectedCategory)
                    .padding(.horizontal, 20)
                    .onChange(of: selectedCategory) { _, _ in
                        selectedLevel = nil
                        statusMessage = nil
                    }

                InventoryToolbarView(
                    selectedCategory: selectedCategory,
                    selectedLevel: $selectedLevel,
                    availableLevels: availableLevels,
                    onSort: {
                        InventorySystem.sortInventory(gameState: gameState)
                        statusMessage = "已整理：等级高的装备会排在前面"
                    },
                    onSellQuality: { quality in
                        let result = InventorySystem.sellItems(
                            quality: quality,
                            category: selectedCategory,
                            gameState: gameState
                        )
                        statusMessage = result.count > 0
                            ? "已出售 \(result.count) 件\(quality.displayName)\(selectedCategory.displayName)，获得 \(result.gold) 金"
                            : "没有可出售的\(quality.displayName)\(selectedCategory.displayName)"
                    }
                )
                .padding(.horizontal, 20)

                if let statusMessage {
                    Text(statusMessage)
                        .font(.caption2)
                        .foregroundColor(.cyan)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                }

                // Inventory grid
                ScrollView {
                    if visibleItems.isEmpty {
                        EmptyInventoryStateView(category: selectedCategory, selectedLevel: selectedLevel)
                            .padding(.top, 36)
                    } else {
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(visibleItems) { item in
                                ItemCellView(item: item)
                                    .onTapGesture {
                                        selectedItem = item
                                        showDetail = true
                                    }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
                .layoutPriority(1)
            }
            .padding(.bottom, 16)

            // Item detail overlay
            if showDetail, let item = selectedItem {
                ItemDetailOverlay(item: item, showDetail: $showDetail)
            }
        }
    }
}

struct InventoryGoldBadge: View {
    let gold: Int

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "dollarsign.circle.fill")
                .font(.caption.bold())
            Text("\(gold)")
                .font(.system(size: 14, weight: .bold))
                .lineLimit(1)
                .minimumScaleFactor(0.55)
        }
        .foregroundColor(.yellow)
        .frame(width: 116, height: 34, alignment: .trailing)
    }
}

struct InventoryTabsView: View {
    @Binding var selectedCategory: InventoryCategory

    var body: some View {
        HStack(spacing: 8) {
            ForEach(InventoryCategory.allCases, id: \.self) { category in
                Button(action: { selectedCategory = category }) {
                    Text(category.displayName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(selectedCategory == category ? .black : .white)
                        .frame(maxWidth: .infinity, minHeight: 34)
                        .background(selectedCategory == category ? Color.cyan : Color.white.opacity(0.08))
                        .cornerRadius(8)
                }
            }
        }
    }
}

struct InventoryToolbarView: View {
    let selectedCategory: InventoryCategory
    @Binding var selectedLevel: Int?
    let availableLevels: [Int]
    let onSort: () -> Void
    let onSellQuality: (ItemQuality) -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onSort) {
                Label("整理", systemImage: "arrow.up.arrow.down")
                    .labelStyle(.titleAndIcon)
            }
            .buttonStyle(InventoryToolButtonStyle(color: .blue))

            Menu {
                Button("全部等级") { selectedLevel = nil }
                ForEach(availableLevels, id: \.self) { level in
                    Button("Lv\(level)") { selectedLevel = level }
                }
            } label: {
                Label(selectedLevel.map { "Lv\($0)" } ?? "全部等级", systemImage: "line.3.horizontal.decrease.circle")
                    .labelStyle(.titleAndIcon)
            }
            .buttonStyle(InventoryToolButtonStyle(color: .purple))

            Menu {
                ForEach(ItemQuality.allCases, id: \.self) { quality in
                    Button("出售\(quality.displayName)\(selectedCategory.displayName)") {
                        onSellQuality(quality)
                    }
                }
            } label: {
                Label("出售", systemImage: "banknote")
                    .labelStyle(.titleAndIcon)
            }
            .buttonStyle(InventoryToolButtonStyle(color: .orange))
        }
    }
}

struct InventoryToolButtonStyle: ButtonStyle {
    let color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 12, weight: .semibold))
            .foregroundColor(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .frame(maxWidth: .infinity, minHeight: 34)
            .background(color.opacity(configuration.isPressed ? 0.35 : 0.22))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(color.opacity(0.55), lineWidth: 1)
            )
    }
}

struct EmptyInventoryStateView: View {
    let category: InventoryCategory
    let selectedLevel: Int?

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "tray")
                .font(.title2)
                .foregroundColor(.gray)
            Text(emptyText)
                .font(.caption)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyText: String {
        if let selectedLevel {
            return "没有 Lv\(selectedLevel) 的\(category.displayName)"
        }
        return "\(category.displayName)栏为空"
    }
}

struct EquipmentSlotsView: View {
    @EnvironmentObject var gameState: GameState
    @Binding var selectedItem: Item?
    @Binding var showDetail: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("已穿戴")
                    .font(.caption.bold())
                    .foregroundColor(.gray)
                Spacer()
                Text("\(gameState.equippedItems.count)/\(EquipSlot.allCases.count)")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }
            .padding(.horizontal, 20)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(EquipSlot.allCases, id: \.self) { slot in
                        EquipSlotView(slot: slot, item: gameState.equippedItems[slot])
                            .onTapGesture {
                                if let item = gameState.equippedItems[slot] {
                                    selectedItem = item
                                    showDetail = true
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

struct EquipSlotView: View {
    let slot: EquipSlot
    let item: Item?

    var slotLabel: String {
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

    var body: some View {
        VStack(spacing: 2) {
            RoundedRectangle(cornerRadius: 4)
                .fill(item != nil ? item!.quality.color.opacity(0.2) : Color.white.opacity(0.05))
                .frame(width: 48, height: 48)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(item?.quality.color ?? Color.gray.opacity(0.3), lineWidth: 1)
                )
                .overlay(
                    VStack(spacing: 0) {
                        Text(item?.compactDisplayName ?? "")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(item?.quality.color ?? .gray)
                            .lineLimit(2)
                            .minimumScaleFactor(0.6)
                            .multilineTextAlignment(.center)
                            .frame(width: 42)
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
        .frame(width: 54)
    }
}

struct ItemCellView: View {
    let item: Item

    private var metaText: String {
        if item.type.equipSlot != nil {
            return "\(item.type.displayName) Lv\(item.requiredLevel)"
        }
        return "Lv\(item.requiredLevel)"
    }

    var body: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(item.quality.color.opacity(0.15))
            .frame(width: 56, height: 56)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(item.quality.color.opacity(0.6), lineWidth: 1.5)
            )
            .overlay(
                VStack(spacing: 2) {
                    Text(item.compactDisplayName)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(item.quality.color)
                        .lineLimit(2)
                        .minimumScaleFactor(0.55)
                        .multilineTextAlignment(.center)
                        .frame(width: 50)
                    Text(metaText)
                        .font(.system(size: 8))
                        .foregroundColor(.gray)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                        .frame(width: 50)
                    if item.enhancementLevel > 0 {
                        Text("+\(item.enhancementLevel)")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(.yellow)
                    }
                }
            )
    }
}

struct EquipmentComparisonTarget: Identifiable {
    let slot: EquipSlot
    let item: Item

    var id: String {
        "\(slot.rawValue)-\(item.id.uuidString)"
    }

    static func samePosition(for candidate: Item, equippedItems: [EquipSlot: Item]) -> [EquipmentComparisonTarget] {
        guard candidate.type.equipSlot != nil else { return [] }

        return equippedItems.compactMap { slot, equippedItem in
            guard equippedItem.type == candidate.type, equippedItem.id != candidate.id else {
                return nil
            }
            return EquipmentComparisonTarget(slot: slot, item: equippedItem)
        }
        .sorted { $0.slot.sortOrder < $1.slot.sortOrder }
    }
}

private struct EquipmentComparisonRow: Identifiable {
    let id: String
    let title: String
    let candidateValue: String
    let equippedValue: String
    let candidateScore: Double
    let equippedScore: Double
    let deltaValue: Double
    let deltaText: String

    var deltaColor: Color {
        if deltaValue > 0 { return .green }
        if deltaValue < 0 { return .red }
        return .gray
    }

    static func integer(_ title: String, candidate: Int, equipped: Int) -> EquipmentComparisonRow {
        let delta = candidate - equipped
        return EquipmentComparisonRow(
            id: title,
            title: title,
            candidateValue: "\(candidate)",
            equippedValue: "\(equipped)",
            candidateScore: Double(candidate),
            equippedScore: Double(equipped),
            deltaValue: Double(delta),
            deltaText: delta == 0 ? "持平" : String(format: "%+d", delta)
        )
    }

    static func percent(_ title: String, candidate: Double, equipped: Double) -> EquipmentComparisonRow {
        let delta = candidate - equipped
        return EquipmentComparisonRow(
            id: title,
            title: title,
            candidateValue: String(format: "%.1f%%", candidate * 100),
            equippedValue: String(format: "%.1f%%", equipped * 100),
            candidateScore: candidate,
            equippedScore: equipped,
            deltaValue: delta,
            deltaText: abs(delta) < 0.0001 ? "持平" : String(format: "%+.1f%%", delta * 100)
        )
    }
}

struct EquipmentComparisonPanel: View {
    let candidate: Item
    let targets: [EquipmentComparisonTarget]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("同部位已穿戴对比")
                .font(.caption.bold())
                .foregroundColor(.white)

            if targets.isEmpty {
                Text("该部位当前未穿戴装备")
                    .font(.caption2)
                    .foregroundColor(.gray)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(8)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(8)
            } else {
                ForEach(targets) { target in
                    EquipmentComparisonCard(candidate: candidate, target: target)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct EquipmentComparisonCard: View {
    let candidate: Item
    let target: EquipmentComparisonTarget

    private var rows: [EquipmentComparisonRow] {
        [
            .integer("战力", candidate: candidate.powerScore, equipped: target.item.powerScore),
            .integer("攻击", candidate: candidate.enhancedAttackBonus, equipped: target.item.enhancedAttackBonus),
            .integer("防御", candidate: candidate.enhancedDefenseBonus, equipped: target.item.enhancedDefenseBonus),
            .integer("生命", candidate: candidate.enhancedHPBonus, equipped: target.item.enhancedHPBonus),
            .integer("法力", candidate: candidate.enhancedMPBonus, equipped: target.item.enhancedMPBonus),
            .percent("暴击", candidate: candidate.enhancedCritBonus, equipped: target.item.enhancedCritBonus)
        ]
        .filter { row in
            row.title == "战力" || abs(row.candidateScore) > 0.0001 || abs(row.equippedScore) > 0.0001
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(target.item.displayName)
                        .font(.caption.bold())
                        .foregroundColor(target.item.quality.color)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Text(target.slot.displayName)
                        .font(.caption2)
                        .foregroundColor(.gray)
                }

                Spacer(minLength: 8)

                Text("已穿戴")
                    .font(.caption2.bold())
                    .foregroundColor(.gray)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.white.opacity(0.06))
                    .cornerRadius(6)
            }

            VStack(spacing: 4) {
                HStack(spacing: 6) {
                    Text("属性")
                        .frame(width: 32, alignment: .leading)
                    Text("此装备")
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    Text("已穿戴")
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    Text("变化")
                        .frame(width: 48, alignment: .trailing)
                }
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.gray)

                ForEach(rows) { row in
                    HStack(spacing: 6) {
                        Text(row.title)
                            .frame(width: 32, alignment: .leading)
                            .foregroundColor(.gray)
                        Text(row.candidateValue)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .foregroundColor(candidate.quality.color)
                        Text(row.equippedValue)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .foregroundColor(.white.opacity(0.75))
                        Text(row.deltaText)
                            .frame(width: 48, alignment: .trailing)
                            .foregroundColor(row.deltaColor)
                    }
                    .font(.system(size: 11, weight: row.title == "战力" ? .bold : .regular))
                }
            }
        }
        .padding(10)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(target.item.quality.color.opacity(0.35), lineWidth: 1)
        )
    }
}

struct ItemDetailOverlay: View {
    let item: Item
    @Binding var showDetail: Bool
    @EnvironmentObject var gameState: GameState
    @State private var enhanceMessage: String?

    var isEquipped: Bool {
        gameState.equippedItems.values.contains(where: { $0.id == item.id })
    }

    var canEquip: Bool {
        item.type.equipSlot != nil && (gameState.player?.level ?? 0) >= item.requiredLevel
    }

    var canWearLatestItem: Bool {
        latestItem.type.equipSlot != nil && (gameState.player?.level ?? 0) >= latestItem.requiredLevel
    }

    var latestItem: Item {
        if let inventoryItem = gameState.inventory.first(where: { $0.id == item.id }) {
            return inventoryItem
        }
        if let equippedItem = gameState.equippedItems.values.first(where: { $0.id == item.id }) {
            return equippedItem
        }
        return item
    }

    var comparisonTargets: [EquipmentComparisonTarget] {
        EquipmentComparisonTarget.samePosition(for: latestItem, equippedItems: gameState.equippedItems)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.7)
                .onTapGesture { showDetail = false }

            ScrollView {
                VStack(spacing: 12) {
                    // Item name
                    let displayItem = latestItem

                    Text(displayItem.displayName)
                        .font(.headline.bold())
                        .foregroundColor(displayItem.quality.color)

                    Text("\(displayItem.quality.displayName) \(displayItem.type.displayName)")
                        .font(.caption)
                        .foregroundColor(.gray)

                    if displayItem.type.equipSlot != nil {
                        Text("穿戴部位: \(displayItem.type.wearPositionName)")
                            .font(.caption)
                            .foregroundColor(.cyan)
                    }

                    Text("需要等级: \(displayItem.requiredLevel)")
                        .font(.caption)
                        .foregroundColor(.gray)

                    Divider().background(Color.gray)

                    // Stats
                    VStack(alignment: .leading, spacing: 4) {
                        if displayItem.enhancedAttackBonus > 0 {
                            Text("+\(displayItem.enhancedAttackBonus) 攻击力")
                                .foregroundColor(.red)
                        }
                        if displayItem.enhancedDefenseBonus > 0 {
                            Text("+\(displayItem.enhancedDefenseBonus) 防御力")
                                .foregroundColor(.blue)
                        }
                        if displayItem.enhancedHPBonus > 0 {
                            Text("+\(displayItem.enhancedHPBonus) 生命值")
                                .foregroundColor(.green)
                        }
                        if displayItem.enhancedMPBonus > 0 {
                            Text("+\(displayItem.enhancedMPBonus) 法力值")
                                .foregroundColor(.cyan)
                        }
                        if displayItem.enhancedCritBonus > 0 {
                            Text("+\(String(format: "%.1f", displayItem.enhancedCritBonus * 100))% 暴击率")
                                .foregroundColor(.yellow)
                        }
                    }
                    .font(.system(size: 13))

                    if displayItem.type.equipSlot != nil {
                        EquipmentComparisonPanel(candidate: displayItem, targets: comparisonTargets)
                    }

                    if displayItem.type.equipSlot != nil {
                        VStack(spacing: 3) {
                            Text("强化 +\(displayItem.enhancementLevel)/\(EnhancementRule.maxLevel)")
                                .font(.caption.bold())
                                .foregroundColor(.yellow)
                            if displayItem.enhancementLevel < EnhancementRule.maxLevel {
                                Text("下级费用 \(displayItem.nextEnhancementCost) 金 · 成功率 \(Int(displayItem.nextEnhancementSuccessRate * 100))%")
                                    .font(.caption2)
                                    .foregroundColor(.gray)
                            } else {
                                Text("已达到强化上限")
                                    .font(.caption2)
                                    .foregroundColor(.gray)
                            }
                            if let enhanceMessage {
                                Text(enhanceMessage)
                                    .font(.caption2)
                                    .foregroundColor(.cyan)
                                    .multilineTextAlignment(.center)
                            }
                        }
                    }

                    Divider().background(Color.gray)

                    Text(displayItem.description)
                        .font(.caption)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)

                    Text("出售价格: \(InventorySystem.vendorSellPrice(for: displayItem)) 金")
                        .font(.caption)
                        .foregroundColor(.yellow)

                    // Actions
                    VStack(spacing: 10) {
                        if isEquipped {
                            Button("卸下") {
                                if let slot = gameState.equippedItems.first(where: { $0.value.id == displayItem.id })?.key {
                                    InventorySystem.unequipItem(slot: slot, gameState: gameState)
                                }
                                showDetail = false
                            }
                            .buttonStyle(InventoryActionButtonStyle(color: .orange))
                        } else {
                            if displayItem.type.equipSlot != nil {
                                Button(canWearLatestItem ? "穿戴" : "Lv\(displayItem.requiredLevel)可穿戴") {
                                    InventorySystem.equipItem(displayItem, gameState: gameState)
                                    showDetail = false
                                }
                                .buttonStyle(InventoryActionButtonStyle(color: .green))
                                .disabled(!canWearLatestItem)
                                .opacity(canWearLatestItem ? 1 : 0.45)
                            }

                            HStack(spacing: 12) {
                                Button("出售") {
                                    InventorySystem.sellItem(displayItem, gameState: gameState)
                                    showDetail = false
                                }
                                .buttonStyle(InventoryActionButtonStyle(color: .red))

                                if displayItem.type.equipSlot != nil && displayItem.enhancementLevel < EnhancementRule.maxLevel {
                                    Button("强化") {
                                        let result = InventorySystem.enhanceItem(displayItem, gameState: gameState)
                                        enhanceMessage = result.message
                                    }
                                    .buttonStyle(InventoryActionButtonStyle(color: .blue))
                                }
                            }
                        }
                    }
                }
                .padding(24)
            }
            .frame(maxWidth: 360, maxHeight: 680)
            .background(Color(red: 0.12, green: 0.12, blue: 0.18))
            .cornerRadius(16)
            .padding(.horizontal, 28)
        }
    }
}

struct InventoryActionButtonStyle: ButtonStyle {
    let color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(color.opacity(configuration.isPressed ? 0.45 : 0.7))
            .cornerRadius(10)
    }
}
