import Foundation

enum WorldChatMessageKind: String, Codable, CaseIterable {
    case adventurer
    case player
    case system
    case market
    case leaderboard
    case dungeon
    case loot
    case enhancement
    case event

    var displayName: String {
        switch self {
        case .adventurer: return "闲谈"
        case .player: return "我"
        case .system: return "书记"
        case .market: return "商会"
        case .leaderboard: return "战力榜"
        case .dungeon: return "副本"
        case .loot: return "掉落"
        case .enhancement: return "工坊"
        case .event: return "传闻"
        }
    }
}

enum WorldChatAction: String, Codable {
    case none
    case market
    case leaderboard
    case inventory
    case dungeonList
}

struct WorldChatMessage: Identifiable, Codable {
    let id: UUID
    let time: Date
    let channelId: String
    let senderId: String?
    let senderName: String
    let senderSubtitle: String
    let senderIcon: String
    let kind: WorldChatMessageKind
    let text: String
    let action: WorldChatAction
    let referenceName: String?
    let isPlayer: Bool
    let importance: Int
}

struct ChatRobotProfile: Identifiable, Codable {
    let id: String
    let name: String
    let race: String
    let profession: String
    let level: Int
    let power: Int
    let rank: Int
    let personality: String
    let voiceStyle: String
    let activeWindow: String
    let chatFrequency: Double
    let focusSystems: [String]
    let favoriteTopics: [String]
    let dislikedTopics: [String]
    let tradePreference: String
    let dungeonPreference: String
    let leaderboardGoal: String
    let likesShowOff: Bool
    let likesComplaining: Bool
    let likesBuying: Bool

    var subtitle: String {
        "Lv.\(level) \(race) \(profession)"
    }
}

struct WorldChatState: Codable {
    var messages: [WorldChatMessage]
    var lastGenerateTime: Date
    var lastOpenedTime: Date
    var lastPlayerMessageTime: Date?
    var lastRobotId: String?
    var templateCooldowns: [String: Date]
    var importedMarketRecordIds: [UUID]
    var importedMarketActivityIds: [UUID]
    var importedLeaderboardLogIds: [UUID]
    var deliveredEventKeys: [String]
    var isMuted: Bool

    init(now: Date = Date()) {
        messages = []
        lastGenerateTime = now.addingTimeInterval(-60)
        lastOpenedTime = now
        lastPlayerMessageTime = nil
        lastRobotId = nil
        templateCooldowns = [:]
        importedMarketRecordIds = []
        importedMarketActivityIds = []
        importedLeaderboardLogIds = []
        deliveredEventKeys = []
        isMuted = false
    }
}

struct WorldChatDelayedReply {
    let message: WorldChatMessage
    let delay: TimeInterval
}

struct WorldChatSendResult {
    let success: Bool
    let message: String
    let replies: [WorldChatDelayedReply]
}
