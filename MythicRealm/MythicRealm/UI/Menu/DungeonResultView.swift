import SwiftUI

struct DungeonResultView: View {
    @EnvironmentObject var gameState: GameState
    @State private var selectedItem: Item?

    var result: DungeonResult? {
        gameState.dungeonResult
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()

                VStack(spacing: 0) {
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 12) {
                            if let result = result {
                                Text(result.isSuccess ? "副本通关!" : "挑战失败")
                                    .font(.title2.bold())
                                    .foregroundColor(result.isSuccess ? .yellow : .red)

                                Text(result.rating.rawValue)
                                    .font(.system(size: 48, weight: .black))
                                    .foregroundColor(result.rating.displayColor)
                                    .shadow(color: result.rating.displayColor, radius: 8)

                                // Stats card - fixed width
                                VStack(alignment: .leading, spacing: 6) {
                                    statLine("副本", result.dungeonName)
                                    statLine("用时", formatTime(result.timeTaken))
                                    statLine("击杀", "\(result.monstersKilled)")
                                    statLine("死亡", "\(result.deaths)")
                                    statLine("经验", "+\(result.expGained)")
                                    statLine("金币", "+\(result.goldGained)")
                                }
                                .padding(12)
                                .frame(width: geo.size.width - 40)
                                .background(Color.white.opacity(0.05))
                                    .cornerRadius(8)

                                // Loot
                                if !result.loot.isEmpty {
                                    Text("获得物品（点击查看）")
                                        .font(.caption)
                                        .foregroundColor(.gray)

                                    let columns = Array(repeating: GridItem(.fixed(70), spacing: 8), count: 4)
                                    LazyVGrid(columns: columns, spacing: 10) {
                                        ForEach(result.loot) { item in
                                            LootItemView(item: item)
                                                .onTapGesture { selectedItem = item }
                                        }
                                    }
                                    .frame(width: geo.size.width - 40)
                                } else if !result.isSuccess {
                                    Text("死亡退出副本，本次未获得奖励")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .frame(width: geo.size.width)
                        .padding(.top, 28)
                        .padding(.bottom, 20)
                    }
                    .safeAreaInset(edge: .bottom) {
                        Color.clear.frame(height: 88)
                    }

                    // Bottom buttons - responsive width
                    HStack(spacing: 12) {
                        Button(action: {
                            gameState.dungeonResult = nil
                            gameState.currentScreen = .inventory
                        }) {
                            Text("背包")
                                .font(.headline)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(Color.purple.opacity(0.7))
                                .cornerRadius(10)
                        }

                        Button(action: {
                            gameState.saveProgress()
                            gameState.dungeonResult = nil
                            gameState.currentScreen = .home
                        }) {
                            Text("继续")
                                .font(.headline)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(Color.green.opacity(0.7))
                                .cornerRadius(10)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 10)
                    .padding(.bottom, 30)
                    .background(Color.black)
                }

                if let item = selectedItem {
                    LootDetailPopup(item: item, onDismiss: { selectedItem = nil })
                }
            }
        }
    }

    private func statLine(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .foregroundColor(.gray)
                .frame(width: 50, alignment: .leading)
            Text(value)
                .foregroundColor(.white)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .font(.system(size: 13))
    }

    private func formatTime(_ time: TimeInterval) -> String {
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

struct LootDetailPopup: View {
    @EnvironmentObject var gameState: GameState
    let item: Item
    let onDismiss: () -> Void

    var comparisonTargets: [EquipmentComparisonTarget] {
        EquipmentComparisonTarget.samePosition(for: item, equippedItems: gameState.equippedItems)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.7)
                .ignoresSafeArea()
                .onTapGesture { onDismiss() }

            ScrollView {
                VStack(spacing: 12) {
                    Text(item.displayName)
                        .font(.title3.bold())
                        .foregroundColor(item.quality.color)

                    Text("\(item.quality.displayName) \(item.type.displayName)")
                        .font(.caption)
                        .foregroundColor(.gray)

                    if item.type.equipSlot != nil {
                        Text("穿戴部位: \(item.type.wearPositionName)")
                            .font(.caption)
                            .foregroundColor(.cyan)
                    }

                    Text("需要等级: \(item.requiredLevel)")
                        .font(.caption)
                        .foregroundColor(.gray)

                    Divider().background(Color.gray.opacity(0.5))

                    VStack(alignment: .leading, spacing: 6) {
                        if item.enhancedAttackBonus > 0 {
                            Text("攻击力 +\(item.enhancedAttackBonus)")
                                .foregroundColor(.red)
                        }
                        if item.enhancedDefenseBonus > 0 {
                            Text("防御力 +\(item.enhancedDefenseBonus)")
                                .foregroundColor(.blue)
                        }
                        if item.enhancedHPBonus > 0 {
                            Text("生命值 +\(item.enhancedHPBonus)")
                                .foregroundColor(.green)
                        }
                        if item.enhancedMPBonus > 0 {
                            Text("法力值 +\(item.enhancedMPBonus)")
                                .foregroundColor(.cyan)
                        }
                        if item.enhancedCritBonus > 0 {
                            Text("暴击率 +\(String(format: "%.1f", item.enhancedCritBonus * 100))%")
                                .foregroundColor(.yellow)
                        }
                    }
                    .font(.system(size: 14))

                    if item.type.equipSlot != nil {
                        EquipmentComparisonPanel(candidate: item, targets: comparisonTargets)
                    }

                    Divider().background(Color.gray.opacity(0.5))

                    Text(item.description)
                        .font(.caption)
                        .foregroundColor(.gray)

                    Text("出售: \(item.sellPrice) 金")
                        .font(.caption)
                        .foregroundColor(.yellow)

                    Button("关闭") { onDismiss() }
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                        .padding(.horizontal, 40)
                        .padding(.vertical, 8)
                        .background(Color.gray.opacity(0.4))
                        .cornerRadius(8)
                        .padding(.top, 4)
                }
                .padding(20)
            }
            .frame(maxWidth: 340, maxHeight: 640)
            .background(Color(red: 0.1, green: 0.1, blue: 0.15))
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(item.quality.color.opacity(0.5), lineWidth: 1)
            )
        }
    }
}

struct ResultRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.gray)
                .frame(width: 50, alignment: .leading)
            Text(value)
                .foregroundColor(.white)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .font(.system(size: 14))
    }
}

struct LootItemView: View {
    let item: Item

    var body: some View {
        VStack(spacing: 3) {
            RoundedRectangle(cornerRadius: 6)
                .fill(item.quality.color.opacity(0.15))
                .frame(width: 50, height: 50)
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(item.quality.color, lineWidth: 1.5)
                )
                .overlay(
                    Text(item.compactDisplayName)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(item.quality.color)
                        .lineLimit(2)
                        .minimumScaleFactor(0.55)
                        .multilineTextAlignment(.center)
                        .frame(width: 44)
                )

            Text(item.compactDisplayName)
                .font(.system(size: 9))
                .foregroundColor(item.quality.color)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
                .multilineTextAlignment(.center)
            if item.enhancementLevel > 0 {
                Text("+\(item.enhancementLevel)")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.yellow)
            }
        }
        .frame(width: 70)
    }
}
