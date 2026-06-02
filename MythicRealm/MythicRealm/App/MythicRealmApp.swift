import SwiftUI

@main
struct MythicRealmApp: App {
    @StateObject private var gameState = GameState.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(gameState)
                .preferredColorScheme(.dark)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var gameState: GameState

    var body: some View {
        ZStack {
            switch gameState.currentScreen {
            case .login:
                LoginView()
            case .createCharacter:
                CreateCharacterView()
            case .home:
                HomeView()
            case .dungeonList:
                DungeonListView()
            case .dungeonDetail:
                DungeonDetailView()
            case .game:
                GameContainerView()
            case .inventory:
                InventoryView()
            case .market:
                MarketView()
            case .leaderboard:
                RobotLeaderboardView()
            case .worldChat:
                WorldChatView()
            case .characterPanel:
                CharacterPanelView()
            case .dungeonResult:
                DungeonResultView()
            }
        }
        .ignoresSafeArea()
    }
}

enum GameScreen {
    case login
    case createCharacter
    case home
    case dungeonList
    case dungeonDetail
    case game
    case inventory
    case market
    case leaderboard
    case worldChat
    case characterPanel
    case dungeonResult
}

class GameState: ObservableObject {
    static let shared = GameState()

    @Published var currentScreen: GameScreen = .login
    @Published var player: PlayerData?
    @Published var inventory: [Item] = []
    @Published var equippedItems: [EquipSlot: Item] = [:]
    @Published var completedDungeonIds: Set<String> = []
    @Published var marketState: MarketState = MarketState()
    @Published var robotLeaderboardState: RobotLeaderboardState = RobotLeaderboardState()
    @Published var chatState: WorldChatState = WorldChatState()
    @Published var dungeonResult: DungeonResult?
    @Published var selectedDungeonId: String?
    @Published var isPaused: Bool = false

    private init() {
        if AccountManager.shared.isLoggedIn {
            if AccountManager.shared.hasCharacter() {
                AccountManager.shared.loadGameState(into: self)
                currentScreen = .home
            } else {
                currentScreen = .createCharacter
            }
        }
    }

    func logout() {
        AccountManager.shared.saveGameState(self)
        AccountManager.shared.logout()
        player = nil
        inventory = []
        equippedItems = [:]
        completedDungeonIds = []
        marketState = MarketState()
        robotLeaderboardState = RobotLeaderboardState()
        chatState = WorldChatState()
        selectedDungeonId = nil
        currentScreen = .login
    }

    func saveProgress() {
        AccountManager.shared.saveGameState(self)
    }

    func selectDungeon(_ dungeon: DungeonConfig) {
        selectedDungeonId = dungeon.id
        currentScreen = .dungeonDetail
    }

    func challengeSelectedDungeon() {
        if selectedDungeonId == nil {
            selectedDungeonId = ConfigLoader.shared.dungeonConfigs.first?.id
        }
        currentScreen = .game
    }

    func hasCompletedDungeon(_ dungeon: DungeonConfig) -> Bool {
        completedDungeonIds.contains(dungeon.id)
    }

    func markDungeonCompleted(_ dungeon: DungeonConfig) {
        completedDungeonIds.insert(dungeon.id)
        saveProgress()
    }

    func sweepSelectedDungeon(times: Int = 10) {
        let dungeon = selectedDungeon()
        guard hasCompletedDungeon(dungeon), var player else { return }

        var totalExp = 0
        var totalGold = 0
        var monstersKilled = 0
        var collectedLoot: [Item] = []
        let monsters = ConfigLoader.shared.monsterConfigs

        for _ in 0..<times {
            for room in dungeon.rooms {
                for roomMonster in room.monsters {
                    guard let monster = monsters.first(where: { $0.id == roomMonster.monsterId }) else { continue }
                    for _ in 0..<roomMonster.count {
                        monstersKilled += 1
                        totalExp += monster.expReward
                        totalGold += monster.goldReward
                        collectedLoot.append(contentsOf: LootSystem.generateLoot(from: monster.lootTable))
                    }
                }
            }
        }

        _ = player.addExperience(totalExp)
        player.gold += totalGold
        self.player = player

        var addedLoot: [Item] = []
        for item in collectedLoot {
            if InventorySystem.addItem(item, to: self) {
                addedLoot.append(item)
            }
        }

        let sweepResult = DungeonResult(
            dungeonName: "\(dungeon.name) 扫荡 x\(times)",
            rating: .S,
            isSuccess: true,
            timeTaken: 0,
            monstersKilled: monstersKilled,
            deaths: 0,
            loot: addedLoot,
            expGained: totalExp,
            goldGained: totalGold
        )
        dungeonResult = sweepResult
        WorldChatSystem.recordDungeonResult(sweepResult, gameState: self)
        saveProgress()
        currentScreen = .dungeonResult
    }

    func selectedDungeon() -> DungeonConfig {
        let configs = ConfigLoader.shared.dungeonConfigs
        if let selectedDungeonId,
           let dungeon = configs.first(where: { $0.id == selectedDungeonId }) {
            return dungeon
        }
        return configs.first!
    }
}
