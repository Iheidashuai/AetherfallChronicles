import SwiftUI

struct GameContainerView: View {
    @EnvironmentObject var gameState: GameState
    @StateObject private var battleEngine: TextBattleEngine = {
        let config = GameState.shared.selectedDungeon()
        let player = GameState.shared.player ?? PlayerData(name: "勇者", profession: .warrior)
        let equipped = GameState.shared.equippedItems
        return TextBattleEngine(dungeonConfig: config, playerData: player, equipped: equipped)
    }()

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top: Player status bar
                PlayerStatusBar(
                    playerName: gameState.player?.name ?? "勇者",
                    level: gameState.player?.level ?? 1,
                    hp: battleEngine.playerHP,
                    maxHP: battleEngine.playerMaxHP,
                    mp: battleEngine.playerMP,
                    maxMP: battleEngine.playerMaxMP,
                    room: battleEngine.currentRoomIndex + 1,
                    totalRooms: battleEngine.totalRooms,
                    monstersAlive: battleEngine.monstersAlive
                )

                Divider().background(Color.gray.opacity(0.3))

                // Middle: Battle log
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 3) {
                            ForEach(battleEngine.logs) { log in
                                if log.text.isEmpty {
                                    Spacer().frame(height: 8).id(log.id)
                                } else {
                                    Text(log.text)
                                        .font(.system(size: 13, design: .monospaced))
                                        .foregroundColor(log.color)
                                        .id(log.id)
                                }
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                    .onChange(of: battleEngine.logs.count) { _ in
                        if let last = battleEngine.logs.last {
                            withAnimation(.easeOut(duration: 0.2)) {
                                proxy.scrollTo(last.id, anchor: .bottom)
                            }
                        }
                    }
                }

                Divider().background(Color.gray.opacity(0.3))

                // Bottom: Action buttons
                BottomActionBar(battleEngine: battleEngine)
            }
        }
        .onAppear {
            if !battleEngine.isRunning && !battleEngine.isComplete {
                battleEngine.start()
            }
        }
        .onDisappear {
            battleEngine.stop()
        }
    }
}

struct PlayerStatusBar: View {
    let playerName: String
    let level: Int
    let hp: CGFloat
    let maxHP: CGFloat
    let mp: CGFloat
    let maxMP: CGFloat
    let room: Int
    let totalRooms: Int
    let monstersAlive: Int

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Text("\(playerName) Lv.\(level)")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                Spacer()
                Text("房间 \(room)/\(totalRooms)")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.gray)
                Text("怪物×\(monstersAlive)")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.orange)
            }

            HStack(spacing: 12) {
                // HP bar
                HStack(spacing: 4) {
                    Text("HP")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(.red)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(Color.gray.opacity(0.3))
                            Rectangle().fill(Color.red)
                                .frame(width: geo.size.width * max(0, min(1, hp / max(1, maxHP))))
                        }
                        .cornerRadius(3)
                    }
                    .frame(height: 10)
                    Text("\(Int(hp))/\(Int(maxHP))")
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.white)
                        .frame(width: 70, alignment: .trailing)
                }

                // MP bar
                HStack(spacing: 4) {
                    Text("MP")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(.blue)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(Color.gray.opacity(0.3))
                            Rectangle().fill(Color.blue)
                                .frame(width: geo.size.width * max(0, min(1, mp / max(1, maxMP))))
                        }
                        .cornerRadius(3)
                    }
                    .frame(height: 10)
                    Text("\(Int(mp))/\(Int(maxMP))")
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.white)
                        .frame(width: 70, alignment: .trailing)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .padding(.top, 44)
        .background(Color(white: 0.08))
    }
}

struct BottomActionBar: View {
    @EnvironmentObject var gameState: GameState
    @ObservedObject var battleEngine: TextBattleEngine

    var body: some View {
        HStack(spacing: 8) {
            if battleEngine.isComplete {
                Button(battleEngine.result?.isSuccess == true ? "查看掉落" : "查看结果") {
                    if let result = battleEngine.result {
                        gameState.dungeonResult = result
                        gameState.currentScreen = .dungeonResult
                    }
                }
                .buttonStyle(ActionButtonStyle(color: .yellow))

                Button("返回") {
                    gameState.saveProgress()
                    gameState.currentScreen = .home
                }
                .buttonStyle(ActionButtonStyle(color: .blue))
            } else {
                Button("背包") {
                    gameState.currentScreen = .inventory
                }
                .buttonStyle(ActionButtonStyle(color: .blue))

                Button("角色") {
                    gameState.currentScreen = .characterPanel
                }
                .buttonStyle(ActionButtonStyle(color: .purple))

                Button("速度 \(battleEngine.speedMultiplier)x") {
                    battleEngine.cycleSpeed()
                }
                .buttonStyle(ActionButtonStyle(color: .green))

                Button("逃跑") {
                    battleEngine.stop()
                    gameState.currentScreen = .home
                }
                .buttonStyle(ActionButtonStyle(color: .red))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .padding(.bottom, 20)
        .background(Color(white: 0.08))
    }
}

struct ActionButtonStyle: ButtonStyle {
    let color: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(color.opacity(configuration.isPressed ? 0.4 : 0.6))
            .cornerRadius(8)
    }
}
