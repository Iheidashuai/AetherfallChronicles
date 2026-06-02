import Foundation
import SwiftUI

struct DungeonConfig: Codable {
    let id: String
    let name: String
    let description: String
    let difficulty: String
    let rooms: [RoomConfig]
    let recommendedLevel: Int
    let recommendedPower: Int
}

struct RoomConfig: Codable {
    let id: String
    let monsters: [RoomMonster]
    let isBossRoom: Bool
}

struct RoomMonster: Codable {
    let monsterId: String
    let count: Int
    let positions: [[CGFloat]]?
}

struct DungeonResult {
    let dungeonName: String
    let rating: Rating
    let isSuccess: Bool
    let timeTaken: TimeInterval
    let monstersKilled: Int
    let deaths: Int
    let loot: [Item]
    let expGained: Int
    let goldGained: Int

    enum Rating: String {
        case S, A, B, C
        case failed = "失败"

        var displayColor: Color {
            switch self {
            case .S: return .yellow
            case .A: return .green
            case .B: return .blue
            case .C: return .gray
            case .failed: return .red
            }
        }
    }
}
