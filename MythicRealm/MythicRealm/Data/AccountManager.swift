import Foundation

struct Account: Codable {
    let username: String
    let passwordHash: String
    var playerData: PlayerData?
    var inventory: [Item]
    var equippedItems: [String: Item]
    var completedDungeonIds: [String]
    var marketState: MarketState
    var robotLeaderboardState: RobotLeaderboardState
    var chatState: WorldChatState
    var questState: QuestState
    var createdAt: Date

    enum CodingKeys: String, CodingKey {
        case username
        case passwordHash
        case playerData
        case inventory
        case equippedItems
        case completedDungeonIds
        case marketState
        case robotLeaderboardState
        case chatState
        case questState
        case createdAt
    }

    init(
        username: String,
        passwordHash: String,
        playerData: PlayerData?,
        inventory: [Item],
        equippedItems: [String: Item],
        completedDungeonIds: [String],
        marketState: MarketState = MarketState(),
        robotLeaderboardState: RobotLeaderboardState = RobotLeaderboardState(),
        chatState: WorldChatState = WorldChatState(),
        questState: QuestState = QuestState(),
        createdAt: Date
    ) {
        self.username = username
        self.passwordHash = passwordHash
        self.playerData = playerData
        self.inventory = inventory
        self.equippedItems = equippedItems
        self.completedDungeonIds = completedDungeonIds
        self.marketState = marketState
        self.robotLeaderboardState = robotLeaderboardState
        self.chatState = chatState
        self.questState = questState
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        username = try container.decode(String.self, forKey: .username)
        passwordHash = try container.decode(String.self, forKey: .passwordHash)
        playerData = try container.decodeIfPresent(PlayerData.self, forKey: .playerData)
        inventory = try container.decodeIfPresent([Item].self, forKey: .inventory) ?? []
        equippedItems = try container.decodeIfPresent([String: Item].self, forKey: .equippedItems) ?? [:]
        completedDungeonIds = try container.decodeIfPresent([String].self, forKey: .completedDungeonIds) ?? []
        marketState = try container.decodeIfPresent(MarketState.self, forKey: .marketState) ?? MarketState()
        robotLeaderboardState = try container.decodeIfPresent(RobotLeaderboardState.self, forKey: .robotLeaderboardState) ?? RobotLeaderboardState()
        chatState = try container.decodeIfPresent(WorldChatState.self, forKey: .chatState) ?? WorldChatState()
        questState = try container.decodeIfPresent(QuestState.self, forKey: .questState) ?? QuestState()
        createdAt = try container.decode(Date.self, forKey: .createdAt)
    }
}

class AccountManager {
    static let shared = AccountManager()

    private let accountsKey = "mythicrealm_accounts"
    private let sessionKey = "mythicrealm_session"
    private let starterEquipmentTemplateIds = [
        "eq_t01_weapon_03",
        "eq_t01_helmet_02",
        "eq_t01_armor_03",
        "eq_t01_boots_02",
        "eq_t01_gloves_02"
    ]

    private var accountsURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("accounts.json")
    }

    private init() {}

    // MARK: - Session

    var currentUsername: String? {
        get { UserDefaults.standard.string(forKey: sessionKey) }
        set { UserDefaults.standard.set(newValue, forKey: sessionKey) }
    }

    var isLoggedIn: Bool {
        currentUsername != nil
    }

    // MARK: - Register

    enum AuthError: Error {
        case usernameExists
        case usernameTooShort
        case passwordTooShort
        case invalidCredentials
        case accountNotFound
    }

    func register(username: String, password: String) throws {
        guard username.count >= 2 else { throw AuthError.usernameTooShort }
        guard password.count >= 4 else { throw AuthError.passwordTooShort }

        var accounts = loadAccounts()
        guard !accounts.contains(where: { $0.username == username }) else {
            throw AuthError.usernameExists
        }

        let account = Account(
            username: username,
            passwordHash: hashPassword(password),
            playerData: nil,
            inventory: [],
            equippedItems: [:],
            completedDungeonIds: [],
            marketState: MarketState(),
            robotLeaderboardState: RobotLeaderboardState(),
            chatState: WorldChatState(),
            questState: QuestState(),
            createdAt: Date()
        )
        accounts.append(account)
        saveAccounts(accounts)
        currentUsername = username
    }

    // MARK: - Login

    func login(username: String, password: String) throws {
        let accounts = loadAccounts()
        guard let account = accounts.first(where: { $0.username == username }) else {
            throw AuthError.invalidCredentials
        }
        guard account.passwordHash == hashPassword(password) else {
            throw AuthError.invalidCredentials
        }
        currentUsername = username
    }

    // MARK: - Logout

    func logout() {
        currentUsername = nil
    }

    // MARK: - Account Data

    func currentAccount() -> Account? {
        guard let username = currentUsername else { return nil }
        let accounts = loadAccounts()
        return accounts.first(where: { $0.username == username })
    }

    func hasCharacter() -> Bool {
        currentAccount()?.playerData != nil
    }

    func saveGameState(_ gameState: GameState) {
        guard let username = currentUsername else { return }
        var accounts = loadAccounts()
        guard let index = accounts.firstIndex(where: { $0.username == username }) else { return }

        accounts[index].playerData = gameState.player

        var equippedDict: [String: Item] = [:]
        for (slot, item) in gameState.equippedItems {
            equippedDict[slot.rawValue] = item
        }
        accounts[index].equippedItems = equippedDict
        accounts[index].inventory = gameState.inventory
        accounts[index].completedDungeonIds = Array(gameState.completedDungeonIds)
        accounts[index].marketState = gameState.marketState
        accounts[index].robotLeaderboardState = gameState.robotLeaderboardState
        accounts[index].chatState = gameState.chatState
        accounts[index].questState = gameState.questState

        saveAccounts(accounts)
    }

    func loadGameState(into gameState: GameState) {
        guard let account = currentAccount() else { return }
        gameState.player = account.playerData
        gameState.inventory = account.inventory

        var equipped: [EquipSlot: Item] = [:]
        for (key, item) in account.equippedItems {
            if let slot = EquipSlot(rawValue: key) {
                equipped[slot] = item
            }
        }
        gameState.equippedItems = equipped
        gameState.completedDungeonIds = Set(account.completedDungeonIds)
        gameState.marketState = account.marketState
        gameState.robotLeaderboardState = account.robotLeaderboardState
        gameState.chatState = account.chatState
        gameState.questState = account.questState
        grantStarterEquipmentIfNeeded(gameState: gameState)
        MarketSystem.bootstrap(gameState: gameState)
        RobotLeaderboardSystem.bootstrap(gameState: gameState)
        WorldChatSystem.bootstrap(gameState: gameState)
        QuestSystem.bootstrap(gameState: gameState)
        saveGameState(gameState)
    }

    func createCharacter(name: String, gameState: GameState) {
        gameState.player = PlayerData(name: name, profession: .warrior)
        gameState.inventory = []
        gameState.equippedItems = [:]
        gameState.completedDungeonIds = []
        gameState.marketState = MarketState()
        gameState.robotLeaderboardState = RobotLeaderboardState()
        gameState.chatState = WorldChatState()
        gameState.questState = QuestState()
        grantStarterEquipmentIfNeeded(gameState: gameState)
        MarketSystem.bootstrap(gameState: gameState)
        RobotLeaderboardSystem.bootstrap(gameState: gameState)
        WorldChatSystem.bootstrap(gameState: gameState)
        QuestSystem.bootstrap(gameState: gameState)
        saveGameState(gameState)
    }

    // MARK: - Private

    private func hashPassword(_ password: String) -> String {
        // Simple hash for local single-player game
        var hash: UInt64 = 5381
        for char in password.utf8 {
            hash = ((hash << 5) &+ hash) &+ UInt64(char)
        }
        return String(hash, radix: 16)
    }

    private func grantStarterEquipmentIfNeeded(gameState: GameState) {
        guard gameState.player != nil,
              gameState.completedDungeonIds.isEmpty,
              gameState.equippedItems.isEmpty else {
            return
        }

        for templateId in starterEquipmentTemplateIds {
            guard let template = ConfigLoader.shared.itemTemplates.first(where: { $0.id == templateId }),
                  let slot = template.type.equipSlot else {
                continue
            }
            gameState.equippedItems[slot] = Item(template: template)
        }
    }

    private func loadAccounts() -> [Account] {
        guard let data = try? Data(contentsOf: accountsURL),
              let accounts = try? JSONDecoder().decode([Account].self, from: data) else {
            return []
        }
        return accounts
    }

    private func saveAccounts(_ accounts: [Account]) {
        guard let data = try? JSONEncoder().encode(accounts) else { return }
        try? data.write(to: accountsURL)
    }
}
