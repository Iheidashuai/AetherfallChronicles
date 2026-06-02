import Foundation

class ConfigLoader {
    static let shared = ConfigLoader()

    var itemTemplates: [ItemTemplate] = []
    var monsterConfigs: [MonsterConfig] = []
    var dungeonConfigs: [DungeonConfig] = []

    private init() {
        loadAll()
    }

    func loadAll() {
        itemTemplates = loadItemTemplates()
        monsterConfigs = loadMonsterConfigs()
        dungeonConfigs = loadDungeonConfigs()
    }

    private func loadItemTemplates() -> [ItemTemplate] {
        loadConfig(filename: "items")
    }

    private func loadMonsterConfigs() -> [MonsterConfig] {
        loadConfig(filename: "monsters")
    }

    private func loadDungeonConfigs() -> [DungeonConfig] {
        loadConfig(filename: "dungeons")
    }

    private func loadConfig<T: Decodable>(filename: String) -> [T] {
        guard let url = Bundle.main.url(forResource: filename, withExtension: "json") else {
            assertionFailure("Missing \(filename).json in app bundle")
            return []
        }

        do {
            let data = try Data(contentsOf: url)
            return try JSONDecoder().decode([T].self, from: data)
        } catch {
            assertionFailure("Failed to load \(filename).json: \(error)")
            return []
        }
    }
}
