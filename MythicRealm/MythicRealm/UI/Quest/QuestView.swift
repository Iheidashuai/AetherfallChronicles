import SwiftUI

struct QuestView: View {
    @EnvironmentObject var gameState: GameState
    @State private var selectedCategory: QuestCategory = .main
    @State private var statusMessage: String?

    private var recommendedRows: [(QuestConfig, QuestProgress)] {
        QuestSystem.recommendedQuests(gameState: gameState, limit: 3)
    }

    private var categoryRows: [(QuestConfig, QuestProgress)] {
        QuestSystem.questRows(gameState: gameState, category: selectedCategory)
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.11), Color(red: 0.10, green: 0.05, blue: 0.10)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                QuestHeaderView(
                    hasClaimable: QuestSystem.hasClaimableRewards(gameState.questState),
                    onBack: { gameState.currentScreen = .home },
                    onClaimAll: claimAll
                )

                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 12) {
                        if let statusMessage {
                            QuestStatusNotice(text: statusMessage, color: .cyan)
                                .padding(.horizontal, 16)
                        }

                        if QuestSystem.isClockProtected(gameState.questState) {
                            QuestStatusNotice(text: "公会沙漏正在校验时间，每日刷新与每日领奖暂缓。", color: .orange)
                                .padding(.horizontal, 16)
                        }

                        QuestRecommendedSection(
                            rows: recommendedRows,
                            onClaim: claim,
                            onNavigate: navigate
                        )
                        .padding(.horizontal, 16)

                        QuestCategoryTabs(selectedCategory: $selectedCategory)
                            .padding(.horizontal, 16)

                        if selectedCategory == .daily {
                            QuestStatusNotice(text: "每日委托刷新：\(QuestSystem.dailyCountdownText())", color: .yellow)
                                .padding(.horizontal, 16)
                        }

                        if categoryRows.isEmpty {
                            QuestEmptyState(category: selectedCategory)
                                .padding(.top, 30)
                        } else {
                            LazyVStack(spacing: 10) {
                                ForEach(categoryRows, id: \.0.id) { config, progress in
                                    QuestCardView(
                                        config: config,
                                        progress: progress,
                                        onClaim: { claim(config.id) },
                                        onNavigate: { navigate(config.navigationTarget) }
                                    )
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 30)
                        }
                    }
                    .padding(.bottom, 28)
                }
            }
        }
        .onAppear {
            QuestSystem.bootstrap(gameState: gameState)
        }
    }

    private func claim(_ questId: String) {
        let result = QuestSystem.claim(questId: questId, gameState: gameState)
        statusMessage = result.message
    }

    private func claimAll() {
        let result = QuestSystem.claimAll(gameState: gameState)
        statusMessage = result.message
    }

    private func navigate(_ target: QuestNavigationTarget) {
        switch target {
        case .none:
            return
        case .dungeonList:
            gameState.currentScreen = .dungeonList
        case .inventory:
            gameState.currentScreen = .inventory
        case .market:
            gameState.currentScreen = .market
        case .leaderboard:
            gameState.currentScreen = .leaderboard
        case .worldChat:
            WorldChatSystem.markOpened(gameState: gameState)
            gameState.currentScreen = .worldChat
        }
    }
}

private struct QuestHeaderView: View {
    let hasClaimable: Bool
    let onBack: () -> Void
    let onClaimAll: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .foregroundColor(.gray)
                    .frame(width: 44, height: 44)
            }

            VStack(spacing: 2) {
                Text("银冠公会委托")
                    .font(.headline)
                    .foregroundColor(.white)
                Text("主线引导 · 每日目标 · 长期成就")
                    .font(.caption2)
                    .foregroundColor(.cyan)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)

            Button(action: onClaimAll) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "tray.and.arrow.down.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(hasClaimable ? .black : .gray)
                        .frame(width: 42, height: 34)
                        .background(hasClaimable ? Color.yellow : Color.white.opacity(0.07))
                        .cornerRadius(8)
                    if hasClaimable {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 8, height: 8)
                            .offset(x: 2, y: -2)
                    }
                }
            }
            .disabled(!hasClaimable)
            .opacity(hasClaimable ? 1 : 0.55)
            .frame(width: 54, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.top, 50)
        .padding(.bottom, 8)
    }
}

private struct QuestRecommendedSection: View {
    let rows: [(QuestConfig, QuestProgress)]
    let onClaim: (String) -> Void
    let onNavigate: (QuestNavigationTarget) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .font(.caption.bold())
                    .foregroundColor(.yellow)
                Text("当前推荐")
                    .font(.caption.bold())
                    .foregroundColor(.yellow)
                Spacer()
                Text("\(rows.count)")
                    .font(.caption2.bold())
                    .foregroundColor(.gray)
            }

            if rows.isEmpty {
                Text("暂无推荐委托")
                    .font(.caption)
                    .foregroundColor(.gray)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(8)
            } else {
                ForEach(rows, id: \.0.id) { config, progress in
                    QuestCompactRow(
                        config: config,
                        progress: progress,
                        onClaim: { onClaim(config.id) },
                        onNavigate: { onNavigate(config.navigationTarget) }
                    )
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.yellow.opacity(0.18), lineWidth: 1)
        )
    }
}

private struct QuestCategoryTabs: View {
    @Binding var selectedCategory: QuestCategory

    private let categories: [QuestCategory] = [.main, .daily, .achievement]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(categories, id: \.self) { category in
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

private struct QuestCompactRow: View {
    let config: QuestConfig
    let progress: QuestProgress
    let onClaim: () -> Void
    let onNavigate: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            QuestStatusIcon(status: progress.status, category: config.category)

            VStack(alignment: .leading, spacing: 3) {
                Text(config.title)
                    .font(.caption.bold())
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Text(QuestSystem.progressText(config: config, progress: progress))
                    .font(.caption2.bold())
                    .foregroundColor(.cyan)
            }

            Spacer()

            QuestActionButton(
                status: progress.status,
                title: compactActionTitle,
                onClaim: onClaim,
                onNavigate: onNavigate
            )
        }
        .padding(10)
        .background(Color.white.opacity(0.045))
        .cornerRadius(8)
    }

    private var compactActionTitle: String {
        progress.status == .completed ? "领取" : config.navigationTarget.displayName
    }
}

private struct QuestCardView: View {
    let config: QuestConfig
    let progress: QuestProgress
    let onClaim: () -> Void
    let onNavigate: () -> Void

    private var progressRatio: CGFloat {
        guard !config.conditions.isEmpty else { return progress.status == .claimed ? 1 : 0 }
        let total = config.conditions.reduce(0.0) { sum, condition in
            let current = Double(min(progress.conditionValues[condition.id] ?? 0, condition.targetValue))
            return sum + current / Double(max(1, condition.targetValue))
        }
        return CGFloat(total / Double(config.conditions.count))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                QuestStatusIcon(status: progress.status, category: config.category)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(config.title)
                            .font(.subheadline.bold())
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)
                        QuestCategoryPill(category: config.category)
                    }

                    Text(config.description)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 6)

                Text(statusText)
                    .font(.caption2.bold())
                    .foregroundColor(statusColor)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(statusColor.opacity(0.12))
                    .cornerRadius(6)
            }

            Text(config.lore)
                .font(.caption2)
                .foregroundColor(.gray)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text("进度")
                        .font(.caption2.bold())
                        .foregroundColor(.gray)
                    Spacer()
                    Text(QuestSystem.progressText(config: config, progress: progress))
                        .font(.caption2.bold())
                        .foregroundColor(.cyan)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color.white.opacity(0.08))
                        RoundedRectangle(cornerRadius: 3)
                            .fill(statusColor)
                            .frame(width: geo.size.width * progressRatio)
                    }
                }
                .frame(height: 7)
            }

            HStack(spacing: 8) {
                Image(systemName: "gift.fill")
                    .font(.caption.bold())
                    .foregroundColor(.yellow)
                Text(QuestSystem.rewardSummary(config.rewards))
                    .font(.caption2.bold())
                    .foregroundColor(.yellow)
                    .lineLimit(2)
                    .minimumScaleFactor(0.75)

                Spacer()

                QuestActionButton(
                    status: progress.status,
                    title: actionTitle,
                    onClaim: onClaim,
                    onNavigate: onNavigate
                )
            }
        }
        .padding(12)
        .background(Color.white.opacity(progress.status == .completed ? 0.075 : 0.05))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(statusColor.opacity(progress.status == .completed ? 0.42 : 0.16), lineWidth: 1)
        )
        .opacity(progress.status == .locked ? 0.58 : 1)
    }

    private var actionTitle: String {
        progress.status == .completed ? "领取" : config.navigationTarget.displayName
    }

    private var statusText: String {
        switch progress.status {
        case .locked: return "未解锁"
        case .active: return "进行中"
        case .completed: return "可领取"
        case .claimed: return "已领取"
        }
    }

    private var statusColor: Color {
        switch progress.status {
        case .locked: return .gray
        case .active: return .cyan
        case .completed: return .yellow
        case .claimed: return .green
        }
    }
}

private struct QuestActionButton: View {
    let status: QuestStatus
    let title: String
    let onClaim: () -> Void
    let onNavigate: () -> Void

    var body: some View {
        Button(action: {
            if status == .completed {
                onClaim()
            } else {
                onNavigate()
            }
        }) {
            Text(buttonTitle)
                .font(.caption.bold())
                .foregroundColor(buttonTextColor)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .padding(.horizontal, 10)
                .frame(height: 30)
                .background(buttonColor)
                .cornerRadius(7)
        }
        .disabled(status == .locked || status == .claimed)
        .opacity(status == .locked || status == .claimed ? 0.55 : 1)
    }

    private var buttonTitle: String {
        switch status {
        case .locked: return "未解锁"
        case .claimed: return "已领取"
        case .completed: return "领取"
        case .active: return title
        }
    }

    private var buttonTextColor: Color {
        status == .completed ? .black : .white
    }

    private var buttonColor: Color {
        switch status {
        case .locked, .claimed: return Color.white.opacity(0.08)
        case .completed: return .yellow
        case .active: return .cyan.opacity(0.28)
        }
    }
}

private struct QuestStatusIcon: View {
    let status: QuestStatus
    let category: QuestCategory

    var body: some View {
        Image(systemName: iconName)
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(color)
            .frame(width: 32, height: 32)
            .background(color.opacity(0.13))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(color.opacity(0.35), lineWidth: 1)
            )
    }

    private var iconName: String {
        if status == .completed { return "checkmark.seal.fill" }
        if status == .claimed { return "seal.fill" }
        if status == .locked { return "lock.fill" }
        switch category {
        case .main: return "scroll.fill"
        case .daily: return "sun.max.fill"
        case .achievement: return "trophy.fill"
        case .weekly: return "calendar"
        case .bounty: return "target"
        case .event: return "sparkles"
        }
    }

    private var color: Color {
        switch status {
        case .locked: return .gray
        case .active:
            switch category {
            case .main: return .cyan
            case .daily: return .green
            case .achievement: return .purple
            case .weekly, .bounty, .event: return .orange
            }
        case .completed: return .yellow
        case .claimed: return .green
        }
    }
}

private struct QuestCategoryPill: View {
    let category: QuestCategory

    var body: some View {
        Text(category.displayName)
            .font(.system(size: 9, weight: .black))
            .foregroundColor(.black)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(Color.cyan)
            .cornerRadius(4)
    }
}

private struct QuestStatusNotice: View {
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
            Text(text)
                .font(.caption2.bold())
                .foregroundColor(.white.opacity(0.86))
                .lineLimit(2)
                .minimumScaleFactor(0.75)
            Spacer()
        }
        .padding(10)
        .background(color.opacity(0.10))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(color.opacity(0.22), lineWidth: 1)
        )
    }
}

private struct QuestEmptyState: View {
    let category: QuestCategory

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "tray")
                .font(.title2)
                .foregroundColor(.gray)
            Text("\(category.displayName)委托暂未开放")
                .font(.caption)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}
