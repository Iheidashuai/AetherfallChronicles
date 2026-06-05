import Foundation

enum QuestCategory: String, Codable, CaseIterable {
    case main
    case daily
    case achievement
    case weekly
    case bounty
    case event

    var displayName: String {
        switch self {
        case .main: return "主线"
        case .daily: return "每日"
        case .achievement: return "成就"
        case .weekly: return "周常"
        case .bounty: return "悬赏"
        case .event: return "事件"
        }
    }
}

enum QuestStatus: String, Codable {
    case locked
    case active
    case completed
    case claimed
}

enum QuestConditionType: String, Codable {
    case dungeonCompleted
    case dungeonClears
    case monsterKills
    case itemQualityObtained
    case equipmentEquipped
    case enhancementAttempts
    case enhancementSuccesses
    case combatPowerReached
    case marketViewed
    case marketListed
    case marketPurchased
    case marketSold
    case leaderboardViewed
    case leaderboardRankReached
    case chatOpened
    case chatQuickMessageSent
    case chatActionUsed
}

enum QuestRewardType: String, Codable {
    case gold
    case experience
    case itemTemplate
}

enum QuestNavigationTarget: String, Codable {
    case none
    case dungeonList
    case inventory
    case market
    case leaderboard
    case worldChat

    var displayName: String {
        switch self {
        case .none: return "查看"
        case .dungeonList: return "去副本"
        case .inventory: return "去背包"
        case .market: return "去商会"
        case .leaderboard: return "看榜单"
        case .worldChat: return "看水晶"
        }
    }
}

struct QuestConfig: Codable, Identifiable {
    let id: String
    let title: String
    let category: QuestCategory
    let description: String
    let lore: String
    let priority: Int
    let prerequisiteIds: [String]
    let navigationTarget: QuestNavigationTarget
    let conditions: [QuestCondition]
    let rewards: [QuestReward]

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case category
        case description
        case lore
        case priority
        case prerequisiteIds
        case navigationTarget
        case conditions
        case rewards
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        title = try container.decode(String.self, forKey: .title)
        category = try container.decode(QuestCategory.self, forKey: .category)
        description = try container.decode(String.self, forKey: .description)
        lore = try container.decode(String.self, forKey: .lore)
        priority = try container.decodeIfPresent(Int.self, forKey: .priority) ?? 0
        prerequisiteIds = try container.decodeIfPresent([String].self, forKey: .prerequisiteIds) ?? []
        navigationTarget = try container.decodeIfPresent(QuestNavigationTarget.self, forKey: .navigationTarget) ?? .none
        conditions = try container.decode([QuestCondition].self, forKey: .conditions)
        rewards = try container.decode([QuestReward].self, forKey: .rewards)
    }
}

struct QuestCondition: Codable, Identifiable {
    let id: String
    let type: QuestConditionType
    let targetId: String?
    let targetValue: Int

    enum CodingKeys: String, CodingKey {
        case id
        case type
        case targetId
        case targetValue
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        type = try container.decode(QuestConditionType.self, forKey: .type)
        targetId = try container.decodeIfPresent(String.self, forKey: .targetId)
        targetValue = try container.decodeIfPresent(Int.self, forKey: .targetValue) ?? 1
    }
}

struct QuestReward: Codable, Identifiable {
    var id: String { "\(type.rawValue)-\(targetId ?? "none")-\(amount)" }

    let type: QuestRewardType
    let amount: Int
    let targetId: String?

    enum CodingKeys: String, CodingKey {
        case type
        case amount
        case targetId
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(QuestRewardType.self, forKey: .type)
        amount = try container.decodeIfPresent(Int.self, forKey: .amount) ?? 1
        targetId = try container.decodeIfPresent(String.self, forKey: .targetId)
    }
}

struct QuestProgress: Codable {
    var questId: String
    var status: QuestStatus
    var conditionValues: [String: Int]
    var cycleKey: String?
    var completedAt: Date?
    var claimedAt: Date?

    init(questId: String, status: QuestStatus, cycleKey: String? = nil) {
        self.questId = questId
        self.status = status
        self.conditionValues = [:]
        self.cycleKey = cycleKey
        self.completedAt = nil
        self.claimedAt = nil
    }
}

struct QuestState: Codable {
    var progressById: [String: QuestProgress]
    var lastDailyRefreshAt: Date?
    var lastSeenAt: Date?
    var dailyCycleKey: String
    var clockProtectionUntil: Date?

    init(now: Date = Date()) {
        progressById = [:]
        lastDailyRefreshAt = nil
        lastSeenAt = now
        dailyCycleKey = QuestDateHelper.dailyCycleKey(for: now)
        clockProtectionUntil = nil
    }
}

enum QuestEvent {
    case dungeonCompleted(dungeonId: String, monstersKilled: Int, lootQualities: [ItemQuality])
    case equipmentEquipped(itemQuality: ItemQuality, slot: EquipSlot)
    case enhancementAttempt(success: Bool)
    case combatPowerChanged(power: Int)
    case marketViewed
    case marketListed(itemQuality: ItemQuality, price: Int)
    case marketPurchased(itemQuality: ItemQuality, price: Int)
    case marketSold(itemQuality: ItemQuality, price: Int, buyerName: String)
    case leaderboardViewed(rank: Int?)
    case chatOpened
    case chatQuickMessageSent
    case chatActionUsed(action: WorldChatAction)
}

struct QuestClaimResult {
    let success: Bool
    let message: String
    let claimedCount: Int

    static func success(_ message: String, claimedCount: Int = 1) -> QuestClaimResult {
        QuestClaimResult(success: true, message: message, claimedCount: claimedCount)
    }

    static func failure(_ message: String) -> QuestClaimResult {
        QuestClaimResult(success: false, message: message, claimedCount: 0)
    }
}

enum QuestDateHelper {
    static func dailyCycleKey(for date: Date, calendar: Calendar = .current) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        let year = components.year ?? 0
        let month = components.month ?? 0
        let day = components.day ?? 0
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    static func nextDailyRefreshDate(after date: Date, calendar: Calendar = .current) -> Date {
        let start = calendar.startOfDay(for: date)
        return calendar.date(byAdding: .day, value: 1, to: start) ?? date.addingTimeInterval(24 * 60 * 60)
    }
}
