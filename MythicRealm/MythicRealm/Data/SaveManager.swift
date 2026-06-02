import Foundation

struct SaveData: Codable {
    let player: PlayerData
    let inventory: [Item]
    let equippedItems: [String: Item]
    let marketState: MarketState?
    let robotLeaderboardState: RobotLeaderboardState?
    let gameDay: Int
    let timestamp: Date
}

class SaveManager {
    static let shared = SaveManager()

    private var saveURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("save.json")
    }

    private init() {}

    func save(gameState: GameState) {
        guard let player = gameState.player else { return }

        var equippedDict: [String: Item] = [:]
        for (slot, item) in gameState.equippedItems {
            equippedDict[slot.rawValue] = item
        }

        let saveData = SaveData(
            player: player,
            inventory: gameState.inventory,
            equippedItems: equippedDict,
            marketState: gameState.marketState,
            robotLeaderboardState: gameState.robotLeaderboardState,
            gameDay: 1,
            timestamp: Date()
        )

        do {
            let data = try JSONEncoder().encode(saveData)
            try data.write(to: saveURL)
        } catch {
            print("Save failed: \(error)")
        }
    }

    func load() -> (player: PlayerData, inventory: [Item], equippedItems: [EquipSlot: Item])? {
        guard let data = try? Data(contentsOf: saveURL),
              let saveData = try? JSONDecoder().decode(SaveData.self, from: data) else {
            return nil
        }

        var equipped: [EquipSlot: Item] = [:]
        for (key, item) in saveData.equippedItems {
            if let slot = EquipSlot(rawValue: key) {
                equipped[slot] = item
            }
        }

        return (saveData.player, saveData.inventory, equipped)
    }

    func hasSave() -> Bool {
        FileManager.default.fileExists(atPath: saveURL.path)
    }

    func deleteSave() {
        try? FileManager.default.removeItem(at: saveURL)
    }
}
