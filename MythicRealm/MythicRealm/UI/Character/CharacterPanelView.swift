import SwiftUI

struct CharacterPanelView: View {
    @EnvironmentObject var gameState: GameState

    var player: PlayerData? { gameState.player }
    var totalAttack: Int {
        Int(player?.attack ?? 0) + InventorySystem.totalEquipmentAttack(gameState: gameState)
    }
    var totalDefense: Int {
        Int(player?.defense ?? 0) + InventorySystem.totalEquipmentDefense(gameState: gameState)
    }
    var totalHP: Int {
        Int(player?.maxHP ?? 0) + InventorySystem.totalEquipmentHP(gameState: gameState)
    }
    var totalMP: Int {
        Int(player?.maxMP ?? 0) + InventorySystem.totalEquipmentMP(gameState: gameState)
    }
    var combatPower: Int {
        InventorySystem.combatPower(gameState: gameState)
    }

    var body: some View {
        ZStack {
            Color(red: 0.08, green: 0.08, blue: 0.12)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                // Header
                HStack {
                    Button(action: { gameState.currentScreen = .home }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    Text("角色信息")
                        .font(.headline)
                        .foregroundColor(.white)
                    Spacer()
                    Color.clear.frame(width: 30)
                }
                .padding(.horizontal)
                .padding(.top, 50)

                if let player = player {
                    // Character info
                    VStack(spacing: 8) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(player.name)
                                    .font(.title2.bold())
                                    .foregroundColor(.white)
                                Text("Lv.\(player.level) \(player.profession.displayName)")
                                    .font(.subheadline)
                                    .foregroundColor(.cyan)
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text("战力")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                Text("\(combatPower)")
                                    .font(.title.bold())
                                    .foregroundColor(.yellow)
                            }
                        }

                        // EXP bar
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text("经验")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                Spacer()
                                Text(player.level >= PlayerData.maxLevel ? "满级" : "\(player.experience)/\(player.experienceToNextLevel)")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Rectangle().fill(Color.gray.opacity(0.3))
                                    Rectangle()
                                        .fill(Color.cyan)
                                        .frame(width: geo.size.width * CGFloat(player.experienceProgress))
                                }
                                .cornerRadius(3)
                            }
                            .frame(height: 6)
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(10)
                    .padding(.horizontal)

                    // Stats
                    VStack(spacing: 0) {
                        Text("基础属性")
                            .font(.subheadline.bold())
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.bottom, 8)

                        CharStatRow(label: "力量", value: player.strength, color: .red, canAllocate: player.freePoints > 0) {
                            allocatePoint(.strength)
                        }
                        CharStatRow(label: "敏捷", value: player.agility, color: .green, canAllocate: player.freePoints > 0) {
                            allocatePoint(.agility)
                        }
                        CharStatRow(label: "体质", value: player.constitution, color: .orange, canAllocate: player.freePoints > 0) {
                            allocatePoint(.constitution)
                        }
                        CharStatRow(label: "智力", value: player.intelligence, color: .blue, canAllocate: player.freePoints > 0) {
                            allocatePoint(.intelligence)
                        }
                        CharStatRow(label: "精神", value: player.spirit, color: .purple, canAllocate: player.freePoints > 0) {
                            allocatePoint(.spirit)
                        }

                        if player.freePoints > 0 {
                            Text("可分配点数: \(player.freePoints)")
                                .font(.caption)
                                .foregroundColor(.yellow)
                                .padding(.top, 8)
                        }

                        Divider().background(Color.gray).padding(.vertical, 8)

                        Text("战斗属性")
                            .font(.subheadline.bold())
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.bottom, 8)

                        CombatStatRow(label: "攻击力", value: "\(totalAttack)")
                        CombatStatRow(label: "防御力", value: "\(totalDefense)")
                        CombatStatRow(label: "生命值", value: "\(totalHP)")
                        CombatStatRow(label: "法力值", value: "\(totalMP)")
                        CombatStatRow(label: "暴击率", value: String(format: "%.1f%%", (Double(player.agility) * 0.001 + InventorySystem.totalEquipmentCrit(gameState: gameState)) * 100))
                    }
                    .padding()
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(10)
                    .padding(.horizontal)

                    // Gold
                    HStack {
                        Text("金币")
                            .foregroundColor(.gray)
                        Spacer()
                        Text("\(player.gold)")
                            .foregroundColor(.yellow)
                            .fontWeight(.bold)
                    }
                    .padding()
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(10)
                    .padding(.horizontal)
                }

                Spacer()
            }
        }
    }

    private func allocatePoint(_ stat: CharacterStat) {
        guard var player = gameState.player, player.freePoints > 0 else { return }
        switch stat {
        case .strength:
            player.strength += 1
        case .agility:
            player.agility += 1
        case .constitution:
            player.constitution += 1
        case .intelligence:
            player.intelligence += 1
        case .spirit:
            player.spirit += 1
        }
        player.freePoints -= 1
        gameState.player = player
        gameState.saveProgress()
    }
}

enum CharacterStat {
    case strength
    case agility
    case constitution
    case intelligence
    case spirit
}

struct CharStatRow: View {
    let label: String
    let value: Int
    let color: Color
    let canAllocate: Bool
    let onAllocate: () -> Void

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.gray)
                .frame(width: 50, alignment: .leading)
            Rectangle()
                .fill(color.opacity(0.5))
                .frame(width: CGFloat(value) * 8, height: 8)
                .cornerRadius(2)
            Spacer()
            Text("\(value)")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(.white)
            Button(action: onAllocate) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(canAllocate ? .yellow : .gray.opacity(0.35))
            }
            .disabled(!canAllocate)
        }
        .padding(.vertical, 2)
    }
}

struct CombatStatRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.gray)
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(.white)
        }
        .padding(.vertical, 2)
    }
}
