import Foundation
import SwiftUI

enum MarketSellerType: String, Codable {
    case player
    case robot
}

enum MarketListingStatus: String, Codable {
    case listed
    case sold
    case expired
    case canceled
}

enum MarketRecordType: String, Codable {
    case sale
    case purchase
    case npcTrade
    case listing
    case notice
}

enum MarketPriceTag: String, Codable {
    case cheap
    case fair
    case pricey
    case luxury

    var displayName: String {
        switch self {
        case .cheap: return "便宜"
        case .fair: return "合理"
        case .pricey: return "偏贵"
        case .luxury: return "收藏价"
        }
    }

    var color: Color {
        switch self {
        case .cheap: return .green
        case .fair: return .cyan
        case .pricey: return .orange
        case .luxury: return .purple
        }
    }
}

enum MarketStatFocus: String, Codable {
    case attack
    case defense
    case hp
    case mp
    case crit
}

struct MarketPriceWindow {
    let recommended: Int
    let minimum: Int
    let maximum: Int
    let quickSale: Int
    let highSale: Int
    let taxRate: Double
}

struct MarketActionResult {
    let success: Bool
    let message: String

    static func success(_ message: String) -> MarketActionResult {
        MarketActionResult(success: true, message: message)
    }

    static func failure(_ message: String) -> MarketActionResult {
        MarketActionResult(success: false, message: message)
    }
}

struct MarketListing: Identifiable, Codable {
    let id: UUID
    let sellerType: MarketSellerType
    let sellerId: String
    let sellerName: String
    var item: Item
    let baseValue: Int
    var listPrice: Int
    let taxRate: Double
    let listingFee: Int
    let listTime: Date
    let expireTime: Date
    var status: MarketListingStatus
    var buyerName: String?
    var soldTime: Date?
    var attentionScore: Double
    var failCount: Int
    var priceTag: MarketPriceTag

    var isActive: Bool {
        status == .listed
    }

    var isPlayerListing: Bool {
        sellerType == .player
    }

    var netIncome: Int {
        Int((Double(listPrice) * (1 - taxRate)).rounded(.down))
    }
}

struct MarketTradeRecord: Identifiable, Codable {
    let id: UUID
    let time: Date
    let buyerName: String
    let sellerName: String
    let itemName: String
    let price: Int
    let isPlayerRelated: Bool
    let recordType: MarketRecordType
}

struct MarketActivity: Identifiable, Codable {
    let id: UUID
    let time: Date
    let text: String
    let isImportant: Bool
}

struct MarketRobotProfile: Identifiable {
    let id: String
    let name: String
    let profession: Profession
    let levelOffset: Int
    let dailyBudget: Int
    let activity: Double
    let priceSensitivity: Double
    let buyIntent: Double
    let sellIntent: Double
    let preferredTypes: [ItemType]
    let preferredStats: [MarketStatFocus]
    let personality: String
}

extension MarketRobotProfile {
    static let all: [MarketRobotProfile] = [
        MarketRobotProfile(
            id: "robot_warrior_ren",
            name: "铁靴雷恩",
            profession: .warrior,
            levelOffset: 1,
            dailyBudget: 4600,
            activity: 0.82,
            priceSensitivity: 0.75,
            buyIntent: 0.78,
            sellIntent: 0.42,
            preferredTypes: [.weapon, .armor, .boots, .gloves],
            preferredStats: [.attack, .defense, .hp],
            personality: "practical"
        ),
        MarketRobotProfile(
            id: "robot_mage_mia",
            name: "旅法师米娅",
            profession: .mage,
            levelOffset: 0,
            dailyBudget: 5200,
            activity: 0.76,
            priceSensitivity: 0.88,
            buyIntent: 0.72,
            sellIntent: 0.55,
            preferredTypes: [.weapon, .necklace, .ring, .gloves],
            preferredStats: [.mp, .crit, .attack],
            personality: "curious"
        ),
        MarketRobotProfile(
            id: "robot_ranger_ash",
            name: "灰鹰艾什",
            profession: .ranger,
            levelOffset: 2,
            dailyBudget: 5900,
            activity: 0.9,
            priceSensitivity: 0.8,
            buyIntent: 0.74,
            sellIntent: 0.64,
            preferredTypes: [.weapon, .boots, .ring, .necklace],
            preferredStats: [.attack, .crit, .hp],
            personality: "decisive"
        ),
        MarketRobotProfile(
            id: "robot_merchant_blackwood",
            name: "黑杉商队",
            profession: .ranger,
            levelOffset: 3,
            dailyBudget: 9600,
            activity: 0.96,
            priceSensitivity: 0.95,
            buyIntent: 0.65,
            sellIntent: 0.95,
            preferredTypes: [.weapon, .helmet, .armor, .necklace, .ring],
            preferredStats: [.attack, .defense, .crit],
            personality: "merchant"
        ),
        MarketRobotProfile(
            id: "robot_guild_morningstar",
            name: "晨星公会",
            profession: .warrior,
            levelOffset: 1,
            dailyBudget: 12800,
            activity: 0.85,
            priceSensitivity: 0.7,
            buyIntent: 0.88,
            sellIntent: 0.28,
            preferredTypes: [.weapon, .helmet, .armor, .legs, .boots, .gloves],
            preferredStats: [.defense, .hp, .attack],
            personality: "guild"
        ),
        MarketRobotProfile(
            id: "robot_collector_orin",
            name: "藏品师奥林",
            profession: .mage,
            levelOffset: 4,
            dailyBudget: 15000,
            activity: 0.6,
            priceSensitivity: 0.55,
            buyIntent: 0.58,
            sellIntent: 0.36,
            preferredTypes: [.necklace, .ring, .weapon],
            preferredStats: [.crit, .mp, .attack],
            personality: "collector"
        )
    ]
}

struct MarketState: Codable {
    var playerListings: [MarketListing]
    var robotListings: [MarketListing]
    var tradeRecords: [MarketTradeRecord]
    var activities: [MarketActivity]
    var hotTypes: [ItemType]
    var demandMap: [String: Double]
    var lastRefreshTime: Date
    var lastDailyReset: Date
    var daySeed: Int
    var freeRefreshCount: Int
    var paidRefreshCount: Int
    var playerDailyMarketIncome: Int
    var dailyIncomeSoftCap: Int
    var discountedFindsToday: Int
    var suspiciousClockUntil: Date?

    init(now: Date = Date()) {
        playerListings = []
        robotListings = []
        tradeRecords = []
        activities = []
        hotTypes = [.weapon, .ring]
        demandMap = [
            ItemType.weapon.rawValue: 1.15,
            ItemType.ring.rawValue: 1.1,
            ItemType.armor.rawValue: 0.95
        ]
        lastRefreshTime = now
        lastDailyReset = now
        daySeed = Int(now.timeIntervalSince1970) % 1_000_000
        freeRefreshCount = 3
        paidRefreshCount = 0
        playerDailyMarketIncome = 0
        dailyIncomeSoftCap = 1200
        discountedFindsToday = 0
        suspiciousClockUntil = nil
    }
}
