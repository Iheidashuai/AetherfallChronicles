import SwiftUI

struct WorldChatView: View {
    @EnvironmentObject var gameState: GameState
    @State private var draftText = ""
    @State private var statusText: String?
    @State private var selectedRobot: ChatRobotProfile?

    private let livePulse = Timer.publish(every: 10, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.04, green: 0.06, blue: 0.09), Color(red: 0.10, green: 0.05, blue: 0.10)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                WorldChatHeader(statusText: statusText)

                WorldChatChannelStrip()
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)

                Divider().background(Color.white.opacity(0.08))

                messageList

                WorldChatComposer(draftText: $draftText, statusText: $statusText, send: sendText)
            }
        }
        .onAppear {
            WorldChatSystem.bootstrap(gameState: gameState)
            WorldChatSystem.markOpened(gameState: gameState)
            QuestSystem.record(.chatOpened, gameState: gameState)
        }
        .onReceive(livePulse) { now in
            WorldChatSystem.tick(gameState: gameState, now: now)
        }
        .sheet(item: $selectedRobot) { robot in
            ChatRobotDetailView(robot: robot)
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 10) {
                    ForEach(gameState.chatState.messages) { message in
                        WorldChatMessageRow(
                            message: message,
                            onAvatarTap: {
                                if let senderId = message.senderId {
                                    selectedRobot = WorldChatSystem.robots.first(where: { $0.id == senderId })
                                }
                            },
                            onActionTap: {
                                performAction(message.action)
                            }
                        )
                        .id(message.id)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
            }
            .onAppear {
                scrollToBottom(proxy)
            }
            .onChange(of: gameState.chatState.messages.count) { _, _ in
                scrollToBottom(proxy)
            }
        }
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy) {
        guard let last = gameState.chatState.messages.last else { return }
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.2)) {
                proxy.scrollTo(last.id, anchor: .bottom)
            }
        }
    }

    private func sendText() {
        let outgoingText = draftText.trimmingCharacters(in: .whitespacesAndNewlines)
        let result = WorldChatSystem.sendPlayerMessage(draftText, gameState: gameState)
        statusText = result.message
        guard result.success else { return }

        if WorldChatSystem.quickPhrases.contains(outgoingText) {
            QuestSystem.record(.chatQuickMessageSent, gameState: gameState)
        }
        draftText = ""
        for reply in result.replies {
            DispatchQueue.main.asyncAfter(deadline: .now() + reply.delay) {
                WorldChatSystem.appendScheduledReply(reply.message, gameState: gameState)
            }
        }
    }

    private func performAction(_ action: WorldChatAction) {
        switch action {
        case .none:
            return
        case .market:
            QuestSystem.record(.chatActionUsed(action: action), gameState: gameState)
            gameState.currentScreen = .market
        case .leaderboard:
            QuestSystem.record(.chatActionUsed(action: action), gameState: gameState)
            gameState.currentScreen = .leaderboard
        case .inventory:
            QuestSystem.record(.chatActionUsed(action: action), gameState: gameState)
            gameState.currentScreen = .inventory
        case .dungeonList:
            QuestSystem.record(.chatActionUsed(action: action), gameState: gameState)
            gameState.currentScreen = .dungeonList
        }
    }
}

private struct WorldChatHeader: View {
    @EnvironmentObject var gameState: GameState
    let statusText: String?

    var body: some View {
        HStack(spacing: 8) {
            Button(action: {
                WorldChatSystem.markOpened(gameState: gameState)
                gameState.currentScreen = .home
            }) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .foregroundColor(.gray)
                    .frame(width: 44, height: 44)
            }

            VStack(spacing: 2) {
                Text(WorldChatSystem.channelName)
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Text(statusText ?? (gameState.chatState.isMuted ? "水晶低语中" : "公会留言同步中"))
                    .font(.caption2)
                    .foregroundColor(gameState.chatState.isMuted ? .orange : .cyan)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)

            HStack(spacing: 6) {
                Button(action: { WorldChatSystem.toggleMuted(gameState: gameState) }) {
                    Image(systemName: gameState.chatState.isMuted ? "bell.slash.fill" : "bell.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(gameState.chatState.isMuted ? .orange : .cyan)
                        .frame(width: 34, height: 34)
                        .background(Color.white.opacity(0.07))
                        .cornerRadius(8)
                }

                Button(action: { WorldChatSystem.clearMessages(gameState: gameState) }) {
                    Image(systemName: "trash")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.gray)
                        .frame(width: 34, height: 34)
                        .background(Color.white.opacity(0.07))
                        .cornerRadius(8)
                }
            }
            .frame(width: 78, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.top, 50)
        .padding(.bottom, 8)
    }
}

private struct WorldChatChannelStrip: View {
    @EnvironmentObject var gameState: GameState

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "crystal.ball")
                    .font(.title3)
                    .foregroundColor(.cyan)
                    .frame(width: 34, height: 34)
                    .background(Color.cyan.opacity(0.12))
                    .cornerRadius(8)

                VStack(alignment: .leading, spacing: 2) {
                    Text("综合频道")
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                    Text(WorldChatSystem.channelSubtitle)
                        .font(.caption2)
                        .foregroundColor(.gray)
                        .lineLimit(1)
                        .minimumScaleFactor(0.68)
                }

                Spacer()

                Text("\(gameState.chatState.messages.count)")
                    .font(.caption.bold())
                    .foregroundColor(.yellow)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(Color.yellow.opacity(0.12))
                    .cornerRadius(6)
            }

            HStack(spacing: 6) {
                WorldChatMetric(label: "市场", value: "\(gameState.marketState.tradeRecords.count)")
                WorldChatMetric(label: "榜单", value: "\(gameState.robotLeaderboardState.logs.count)")
                WorldChatMetric(label: "保留", value: "\(WorldChatSystem.maxMessages)")
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

private struct WorldChatMetric: View {
    let label: String
    let value: String

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
            Text(value)
                .font(.caption2.bold())
                .foregroundColor(.white.opacity(0.86))
        }
        .frame(maxWidth: .infinity)
    }
}

private struct WorldChatMessageRow: View {
    let message: WorldChatMessage
    let onAvatarTap: () -> Void
    let onActionTap: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if message.isPlayer {
                Spacer(minLength: 34)
                bubble
                avatar
            } else {
                avatar
                bubble
                Spacer(minLength: 22)
            }
        }
    }

    private var avatar: some View {
        Button(action: onAvatarTap) {
            Image(systemName: message.senderIcon)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(color(for: message.kind))
                .frame(width: 34, height: 34)
                .background(color(for: message.kind).opacity(0.13))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(color(for: message.kind).opacity(0.35), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(message.senderId == nil)
    }

    private var bubble: some View {
        VStack(alignment: message.isPlayer ? .trailing : .leading, spacing: 6) {
            HStack(spacing: 6) {
                if message.isPlayer { Spacer(minLength: 0) }

                Text(message.senderName)
                    .font(.caption.bold())
                    .foregroundColor(message.isPlayer ? .yellow : .white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)

                Text(message.kind.displayName)
                    .font(.system(size: 9, weight: .black))
                    .foregroundColor(message.isPlayer ? .black : color(for: message.kind))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(message.isPlayer ? Color.yellow : color(for: message.kind).opacity(0.12))
                    .cornerRadius(4)

                Text(relativeTime(message.time))
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundColor(.gray)

                if !message.isPlayer { Spacer(minLength: 0) }
            }

            if !message.senderSubtitle.isEmpty {
                Text(message.senderSubtitle)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.gray)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }

            Text(message.text)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white.opacity(0.9))
                .lineSpacing(2)
                .multilineTextAlignment(message.isPlayer ? .trailing : .leading)
                .fixedSize(horizontal: false, vertical: true)

            if message.action != .none {
                Button(action: onActionTap) {
                    HStack(spacing: 5) {
                        Text(actionTitle(for: message.action))
                            .font(.caption2.bold())
                        Image(systemName: "chevron.right")
                            .font(.system(size: 9, weight: .black))
                    }
                    .foregroundColor(color(for: message.kind))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(color(for: message.kind).opacity(0.10))
                    .cornerRadius(6)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(10)
        .background(message.isPlayer ? Color.yellow.opacity(0.12) : Color.white.opacity(0.055))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(message.isPlayer ? Color.yellow.opacity(0.28) : color(for: message.kind).opacity(message.importance >= 2 ? 0.35 : 0.12), lineWidth: 1)
        )
    }

    private func color(for kind: WorldChatMessageKind) -> Color {
        switch kind {
        case .player: return .yellow
        case .system: return .cyan
        case .market: return .green
        case .leaderboard: return .orange
        case .dungeon: return .red
        case .loot: return .purple
        case .enhancement: return .yellow
        case .event: return .blue
        case .adventurer: return .cyan
        }
    }

    private func actionTitle(for action: WorldChatAction) -> String {
        switch action {
        case .none: return ""
        case .market: return "去商会"
        case .leaderboard: return "看榜单"
        case .inventory: return "看背包"
        case .dungeonList: return "看副本"
        }
    }

    private func relativeTime(_ time: Date) -> String {
        let seconds = max(0, Int(Date().timeIntervalSince(time)))
        if seconds < 60 { return "刚刚" }
        if seconds < 3600 { return "\(seconds / 60)分" }
        return "\(seconds / 3600)时"
    }
}

private struct WorldChatComposer: View {
    @Binding var draftText: String
    @Binding var statusText: String?
    let send: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(WorldChatSystem.quickPhrases, id: \.self) { phrase in
                        Button(action: {
                            draftText = phrase
                            send()
                        }) {
                            Text(phrase)
                                .font(.caption.bold())
                                .foregroundColor(.cyan)
                                .lineLimit(1)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(Color.cyan.opacity(0.10))
                                .cornerRadius(8)
                        }
                    }
                }
                .padding(.horizontal, 12)
            }

            HStack(spacing: 8) {
                TextField("在水晶上留下短句", text: $draftText)
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .padding(.horizontal, 12)
                    .frame(height: 42)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white.opacity(0.10), lineWidth: 1)
                    )

                Button(action: send) {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .frame(width: 42, height: 42)
                        .background(draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.gray : Color.cyan)
                        .cornerRadius(8)
                }
                .disabled(draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 12)
        }
        .padding(.top, 10)
        .padding(.bottom, 26)
        .background(Color.black.opacity(0.34))
    }
}

private struct ChatRobotDetailView: View {
    let robot: ChatRobotProfile

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.06, green: 0.06, blue: 0.10), Color(red: 0.10, green: 0.05, blue: 0.10)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    Image(systemName: icon(for: robot))
                        .font(.system(size: 34, weight: .bold))
                        .foregroundColor(.cyan)
                        .frame(width: 74, height: 74)
                        .background(Color.cyan.opacity(0.12))
                        .cornerRadius(12)

                    Text(robot.name)
                        .font(.title3.bold())
                        .foregroundColor(.white)

                    Text(robot.subtitle)
                        .font(.caption.bold())
                        .foregroundColor(.cyan)

                    HStack(spacing: 8) {
                        ChatRobotMetric(label: "战力", value: "\(robot.power)")
                        ChatRobotMetric(label: "排名", value: "#\(robot.rank)")
                        ChatRobotMetric(label: "频率", value: frequencyText(robot.chatFrequency))
                    }

                    detailBlock(title: "性格", value: robot.personality)
                    detailBlock(title: "语气", value: robot.voiceStyle)
                    detailBlock(title: "常聊", value: robot.favoriteTopics.joined(separator: " / "))
                    detailBlock(title: "交易", value: robot.tradePreference)
                    detailBlock(title: "副本", value: robot.dungeonPreference)
                    detailBlock(title: "目标", value: robot.leaderboardGoal)
                }
                .padding(20)
            }
        }
    }

    private func detailBlock(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .font(.caption.bold())
                .foregroundColor(.gray)
            Text(value)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.88))
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.white.opacity(0.055))
        .cornerRadius(8)
    }

    private func frequencyText(_ value: Double) -> String {
        if value >= 0.8 { return "活跃" }
        if value >= 0.55 { return "稳定" }
        return "偶尔"
    }

    private func icon(for robot: ChatRobotProfile) -> String {
        if robot.profession.contains("法师") { return "wand.and.stars" }
        if robot.profession.contains("商人") || robot.profession.contains("情报") { return "bag.fill" }
        if robot.profession.contains("游侠") || robot.profession.contains("射手") { return "scope" }
        if robot.profession.contains("铁匠") { return "hammer.fill" }
        if robot.profession.contains("吟游") { return "music.note" }
        if robot.profession.contains("骑士") { return "shield.lefthalf.filled" }
        return "person.fill"
    }
}

private struct ChatRobotMetric: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundColor(.yellow)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
        .padding(10)
        .background(Color.white.opacity(0.055))
        .cornerRadius(8)
    }
}
