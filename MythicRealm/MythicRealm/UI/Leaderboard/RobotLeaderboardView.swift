import SwiftUI

struct RobotLeaderboardView: View {
    @EnvironmentObject var gameState: GameState
    @State private var selectedRobot: RobotAdventure?
    @State private var statusText: String?

    private let livePulse = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var entries: [LeaderboardEntry] {
        RobotLeaderboardSystem.rankedEntries(gameState: gameState)
    }

    private var playerEntry: LeaderboardEntry? {
        entries.first(where: \.isPlayer)
    }

    private var logs: [RobotActivityLog] {
        Array(gameState.robotLeaderboardState.logs.prefix(8))
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.11), Color(red: 0.10, green: 0.04, blue: 0.12)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 12) {
                    LeaderboardHeaderView()

                    LeaderboardPlayerSummary(entry: playerEntry, gapText: RobotLeaderboardSystem.playerGapText(gameState: gameState))
                        .padding(.horizontal, 16)

                    LeaderboardLiveLogView(logs: logs)
                        .padding(.horizontal, 16)

                    if let statusText {
                        Text(statusText)
                            .font(.caption2)
                            .foregroundColor(.cyan)
                            .lineLimit(2)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 18)
                    }

                    LazyVStack(spacing: 8) {
                        ForEach(Array(entries.prefix(100))) { entry in
                            LeaderboardRow(entry: entry) {
                                if let robot = entry.robot {
                                    selectedRobot = robot
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 34)
                }
            }
        }
        .onAppear {
            RobotLeaderboardSystem.refresh(gameState: gameState)
            let rank = RobotLeaderboardSystem.rankedEntries(gameState: gameState).first(where: \.isPlayer)?.rank
            QuestSystem.record(.leaderboardViewed(rank: rank), gameState: gameState)
            statusText = "公会档案已同步"
        }
        .onReceive(livePulse) { now in
            RobotLeaderboardSystem.refresh(gameState: gameState, now: now, forceMinute: true)
            let rank = RobotLeaderboardSystem.rankedEntries(gameState: gameState).first(where: \.isPlayer)?.rank
            QuestSystem.record(.leaderboardViewed(rank: rank), gameState: gameState, now: now)
            statusText = "排行榜已刷新"
        }
        .sheet(item: $selectedRobot) { robot in
            RobotAdventureDetailView(robot: robot)
        }
    }
}

private struct LeaderboardHeaderView: View {
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
                Text("银冠战力榜")
                    .font(.headline)
                    .foregroundColor(.white)
                Text("冒险者档案每分钟同步")
                    .font(.caption2)
                    .foregroundColor(.cyan)
            }
            .frame(maxWidth: .infinity)

            Text("TOP 100")
                .font(.caption.bold())
                .foregroundColor(.yellow)
                .frame(width: 72, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.top, 50)
    }
}

private struct LeaderboardPlayerSummary: View {
    let entry: LeaderboardEntry?
    let gapText: String

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                LeaderboardMetric(label: "我的排名", value: entry.map { "#\($0.rank)" } ?? "未入榜", color: .yellow)
                LeaderboardMetric(label: "当前战力", value: entry.map { "\($0.power)" } ?? "0", color: .cyan)
                LeaderboardMetric(label: "排名变化", value: entry?.rankChangeText ?? "—", color: entry?.rankChangeColor ?? .gray)
            }

            HStack(spacing: 8) {
                Image(systemName: "target")
                    .foregroundColor(.green)
                Text(gapText)
                    .font(.caption.bold())
                    .foregroundColor(.white.opacity(0.86))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Spacer()
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }
}

private struct LeaderboardMetric: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundColor(color)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct LeaderboardLiveLogView: View {
    let logs: [RobotActivityLog]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Circle()
                    .fill(Color.green)
                    .frame(width: 7, height: 7)
                Text("冒险者动态")
                    .font(.caption2.bold())
                    .foregroundColor(.green)
                Spacer()
                Text("\(logs.count) 条")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }

            if logs.isEmpty {
                Text("公会书记正在整理远征记录")
                    .font(.caption2)
                    .foregroundColor(.gray)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ForEach(logs.prefix(4)) { log in
                    HStack(spacing: 8) {
                        Image(systemName: icon(for: log.type))
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(color(for: log.importance))
                            .frame(width: 16)
                        Text(log.text)
                            .font(.caption2.bold())
                            .foregroundColor(.white.opacity(0.82))
                            .lineLimit(1)
                            .minimumScaleFactor(0.68)
                        Spacer()
                        Text(relativeTime(log.time))
                            .font(.system(size: 9, weight: .medium))
                            .foregroundColor(.gray)
                            .frame(width: 42, alignment: .trailing)
                    }
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private func icon(for type: RobotLogType) -> String {
        switch type {
        case .dungeonClear: return "checkmark.seal"
        case .dungeonFail: return "exclamationmark.triangle"
        case .levelUp: return "arrow.up.circle"
        case .loot: return "sparkles"
        case .equip: return "shield.lefthalf.filled"
        case .enhanceSuccess: return "hammer"
        case .enhanceFail: return "xmark.circle"
        case .marketBuy: return "bag"
        case .rankMove: return "chart.line.uptrend.xyaxis"
        case .notice: return "megaphone"
        }
    }

    private func color(for importance: Int) -> Color {
        if importance >= 3 { return .orange }
        if importance >= 2 { return .yellow }
        if importance >= 1 { return .cyan }
        return .gray
    }

    private func relativeTime(_ time: Date) -> String {
        let seconds = max(0, Int(Date().timeIntervalSince(time)))
        if seconds < 60 { return "刚刚" }
        if seconds < 3600 { return "\(seconds / 60)分" }
        return "\(seconds / 3600)时"
    }
}

private struct LeaderboardRow: View {
    let entry: LeaderboardEntry
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                rankBadge

                Image(systemName: professionIcon(entry.profession))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(entry.isPlayer ? .yellow : .cyan)
                    .frame(width: 28, height: 28)
                    .background((entry.isPlayer ? Color.yellow : Color.cyan).opacity(0.12))
                    .cornerRadius(6)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 5) {
                        Text(entry.name)
                            .font(.subheadline.bold())
                            .foregroundColor(entry.isPlayer ? .yellow : .white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)

                        Text(entry.tag)
                            .font(.system(size: 8, weight: .black))
                            .foregroundColor(entry.isPlayer ? .black : .cyan)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(entry.isPlayer ? Color.yellow : Color.cyan.opacity(0.14))
                            .cornerRadius(4)
                    }

                    Text("Lv.\(entry.level) \(entry.profession.displayName) · \(entry.highlight)")
                        .font(.caption2)
                        .foregroundColor(.gray)
                        .lineLimit(1)
                        .minimumScaleFactor(0.68)
                }

                Spacer(minLength: 8)

                VStack(alignment: .trailing, spacing: 3) {
                    Text("\(entry.power)")
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.72)
                    Text(entry.rankChangeText)
                        .font(.caption2.bold())
                        .foregroundColor(entry.rankChangeColor)
                        .frame(width: 38, alignment: .trailing)
                }

                if !entry.isPlayer {
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }
            .padding(10)
            .background(entry.isPlayer ? Color.yellow.opacity(0.11) : Color.white.opacity(0.05))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(entry.isPlayer ? Color.yellow.opacity(0.35) : Color.white.opacity(0.07), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(entry.isPlayer)
    }

    private var rankBadge: some View {
        Text("#\(entry.rank)")
            .font(.system(size: 12, weight: .black))
            .foregroundColor(rankColor)
            .frame(width: 42, alignment: .leading)
    }

    private var rankColor: Color {
        switch entry.rank {
        case 1: return .yellow
        case 2, 3: return .orange
        default: return .gray
        }
    }

    private func professionIcon(_ profession: Profession) -> String {
        switch profession {
        case .warrior: return "shield.fill"
        case .ranger: return "scope"
        case .mage: return "sparkles"
        }
    }
}

private struct RobotAdventureDetailView: View {
    let robot: RobotAdventure

    private var dungeonName: String {
        let dungeons = ConfigLoader.shared.dungeonConfigs
        guard dungeons.indices.contains(robot.dungeonIndex) else { return "边境营地" }
        return dungeons[robot.dungeonIndex].name
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.11), Color(red: 0.10, green: 0.04, blue: 0.12)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 12) {
                    VStack(spacing: 8) {
                        Image(systemName: icon(for: robot.profession))
                            .font(.system(size: 34, weight: .semibold))
                            .foregroundColor(.cyan)
                            .frame(width: 72, height: 72)
                            .background(Color.cyan.opacity(0.12))
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.cyan.opacity(0.42), lineWidth: 1))

                        Text(robot.name)
                            .font(.title3.bold())
                            .foregroundColor(.white)
                        Text(robot.title)
                            .font(.caption.bold())
                            .foregroundColor(.cyan)
                    }
                    .padding(.top, 24)

                    HStack(spacing: 8) {
                        LeaderboardMetric(label: "等级", value: "Lv.\(robot.level)", color: .yellow)
                        LeaderboardMetric(label: "战力", value: "\(RobotLeaderboardSystem.combatPower(for: robot))", color: .cyan)
                        LeaderboardMetric(label: "金币", value: "\(robot.gold)", color: .orange)
                    }
                    .padding(12)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(8)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("远征状态")
                            .font(.subheadline.bold())
                            .foregroundColor(.white)
                        HStack(spacing: 8) {
                            DetailChip(icon: "map", text: dungeonName)
                            DetailChip(icon: "bolt", text: "体力 \(Int(robot.stamina))/100")
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(8)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("正在穿戴")
                            .font(.subheadline.bold())
                            .foregroundColor(.white)

                        LazyVStack(spacing: 8) {
                            ForEach(robot.gearItems, id: \.id) { item in
                                RobotGearRow(item: item)
                            }
                        }
                    }
                    .padding(12)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(8)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func icon(for profession: Profession) -> String {
        switch profession {
        case .warrior: return "shield.fill"
        case .ranger: return "scope"
        case .mage: return "sparkles"
        }
    }
}

private struct DetailChip: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .bold))
            Text(text)
                .font(.caption.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.72)
        }
        .foregroundColor(.white.opacity(0.86))
        .padding(.horizontal, 9)
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.07))
        .cornerRadius(6)
    }
}

private struct RobotGearRow: View {
    let item: Item

    var body: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 6)
                .fill(item.quality.color.opacity(0.16))
                .frame(width: 34, height: 34)
                .overlay(
                    Image(systemName: icon(for: item.type))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(item.quality.color)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(item.displayName)
                    .font(.caption.bold())
                    .foregroundColor(item.quality.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(item.type.wearPositionName)
                    .font(.caption2)
                    .foregroundColor(.gray)
            }

            Spacer()

            Text("评分 \(item.powerScore)")
                .font(.caption2.bold())
                .foregroundColor(.white.opacity(0.76))
                .lineLimit(1)
        }
        .padding(8)
        .background(Color.black.opacity(0.16))
        .cornerRadius(8)
    }

    private func icon(for type: ItemType) -> String {
        switch type {
        case .weapon: return "flame"
        case .helmet: return "person.crop.circle"
        case .armor, .legs: return "shield"
        case .boots: return "figure.run"
        case .gloves: return "hand.raised"
        case .necklace: return "seal"
        case .ring: return "circle.dotted"
        case .consumable: return "cross.case"
        case .material: return "cube"
        case .quest: return "scroll"
        }
    }
}
