import Foundation

struct MonsterConfig: Codable {
    let id: String
    let name: String
    let level: Int
    let maxHP: CGFloat
    let strength: Int
    let moveSpeed: CGFloat
    let attackRange: CGFloat
    let attackCooldown: TimeInterval
    let detectionRange: CGFloat
    let isBoss: Bool
    let lootTable: [LootEntry]
    let expReward: Int
    let goldReward: Int
}

struct LootEntry: Codable {
    let itemId: String
    let dropRate: Double // 0.0 ~ 1.0
}
