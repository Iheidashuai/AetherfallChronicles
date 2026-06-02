import Foundation
import SwiftUI

enum RobotPersonalityKind: String, Codable, CaseIterable {
    case climber
    case grinder
    case trader
    case gambler
    case casual

    var displayName: String {
        switch self {
        case .climber: return "冲榜者"
        case .grinder: return "刷本者"
        case .trader: return "商会客"
        case .gambler: return "强化赌徒"
        case .casual: return "休闲者"
        }
    }

    var shortTag: String {
        switch self {
        case .climber: return "HOT"
        case .grinder: return "稳"
        case .trader: return "商"
        case .gambler: return "炼"
        case .casual: return "闲"
        }
    }
}

struct RobotPersonality: Codable {
    let kind: RobotPersonalityKind
    var activity: Double
    var risk: Double
    var marketBias: Double
    var enhanceBias: Double
    var ambition: Double

    static func preset(_ kind: RobotPersonalityKind, rng: inout LeaderboardRandom) -> RobotPersonality {
        func spread(_ base: Double, _ range: Double) -> Double {
            LeaderboardMath.clamp(base + (rng.nextDouble() - 0.5) * range, to: 0.05...1.35)
        }

        switch kind {
        case .climber:
            return RobotPersonality(kind: kind, activity: spread(1.08, 0.2), risk: spread(0.72, 0.18), marketBias: spread(0.42, 0.2), enhanceBias: spread(0.82, 0.22), ambition: spread(0.92, 0.16))
        case .grinder:
            return RobotPersonality(kind: kind, activity: spread(0.82, 0.22), risk: spread(0.42, 0.2), marketBias: spread(0.24, 0.18), enhanceBias: spread(0.48, 0.2), ambition: spread(0.58, 0.2))
        case .trader:
            return RobotPersonality(kind: kind, activity: spread(0.76, 0.22), risk: spread(0.38, 0.18), marketBias: spread(0.88, 0.18), enhanceBias: spread(0.42, 0.2), ambition: spread(0.62, 0.18))
        case .gambler:
            return RobotPersonality(kind: kind, activity: spread(0.72, 0.24), risk: spread(0.62, 0.2), marketBias: spread(0.35, 0.2), enhanceBias: spread(0.96, 0.18), ambition: spread(0.76, 0.2))
        case .casual:
            return RobotPersonality(kind: kind, activity: spread(0.36, 0.18), risk: spread(0.25, 0.16), marketBias: spread(0.2, 0.14), enhanceBias: spread(0.24, 0.14), ambition: spread(0.28, 0.16))
        }
    }
}

enum RobotLogType: String, Codable {
    case dungeonClear
    case dungeonFail
    case levelUp
    case loot
    case equip
    case enhanceSuccess
    case enhanceFail
    case marketBuy
    case rankMove
    case notice
}

struct RobotActivityLog: Identifiable, Codable {
    let id: UUID
    let time: Date
    let robotId: Int?
    let type: RobotLogType
    let text: String
    let importance: Int
}

struct RobotAdventure: Identifiable, Codable {
    let id: Int
    var name: String
    var title: String
    var profession: Profession
    var level: Int
    var experience: Int
    var gold: Int
    var stamina: Double
    var dungeonIndex: Int
    var gear: [String: Item]
    var personality: RobotPersonality
    var lastMajorLogTime: Date
    var previousRank: Int?
    var lastRankChange: Int
    var previousPower: Int

    var gearItems: [Item] {
        gear.values.sorted { lhs, rhs in
            guard let lhsSlot = RobotGearSlot(rawValue: lhs.type.rawValue),
                  let rhsSlot = RobotGearSlot(rawValue: rhs.type.rawValue) else {
                return lhs.powerScore > rhs.powerScore
            }
            return lhsSlot.sortOrder < rhsSlot.sortOrder
        }
    }
}

struct RobotLeaderboardState: Codable {
    var robots: [RobotAdventure]
    var logs: [RobotActivityLog]
    var lastSimulationTime: Date
    var lastRefreshTime: Date
    var lastDailyReset: Date
    var daySeed: Int
    var playerBestPower: Int
    var previousPlayerRank: Int?
    var lastPlayerRankChange: Int
    var suspiciousClockUntil: Date?

    init(now: Date = Date()) {
        robots = []
        logs = []
        lastSimulationTime = now
        lastRefreshTime = now
        lastDailyReset = now
        daySeed = Int(now.timeIntervalSince1970) % 1_000_000
        playerBestPower = 0
        previousPlayerRank = nil
        lastPlayerRankChange = 0
        suspiciousClockUntil = nil
    }
}

struct LeaderboardEntry: Identifiable {
    let id: String
    let rank: Int
    let name: String
    let title: String
    let profession: Profession
    let level: Int
    let power: Int
    let isPlayer: Bool
    let rankChange: Int
    let tag: String
    let highlight: String
    let robot: RobotAdventure?

    var rankChangeText: String {
        if rankChange > 0 { return "↑\(rankChange)" }
        if rankChange < 0 { return "↓\(abs(rankChange))" }
        return "—"
    }

    var rankChangeColor: Color {
        if rankChange > 0 { return .green }
        if rankChange < 0 { return .orange }
        return .gray
    }
}

enum RobotGearSlot: String, CaseIterable {
    case weapon
    case helmet
    case armor
    case legs
    case boots
    case gloves
    case necklace
    case ring

    var sortOrder: Int {
        switch self {
        case .weapon: return 0
        case .helmet: return 1
        case .armor: return 2
        case .legs: return 3
        case .boots: return 4
        case .gloves: return 5
        case .necklace: return 6
        case .ring: return 7
        }
    }
}

struct LeaderboardRandom {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed == 0 ? 0x9E37_79B9_7F4A_7C15 : seed
    }

    mutating func nextDouble() -> Double {
        Double(next() % 10_000) / 10_000.0
    }

    mutating func nextInt(upperBound: Int) -> Int {
        guard upperBound > 0 else { return 0 }
        return Int(next() % UInt64(upperBound))
    }

    mutating func nextBool(probability: Double) -> Bool {
        nextDouble() <= LeaderboardMath.clamp(probability, to: 0...1)
    }

    private mutating func next() -> UInt64 {
        state ^= state >> 12
        state ^= state << 25
        state ^= state >> 27
        return state &* 2_685_821_657_736_338_717
    }
}

enum LeaderboardMath {
    static func clamp(_ value: Double, to range: ClosedRange<Double>) -> Double {
        min(max(value, range.lowerBound), range.upperBound)
    }
}
