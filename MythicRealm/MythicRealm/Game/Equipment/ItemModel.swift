import Foundation
import SwiftUI

enum ItemQuality: String, Codable, CaseIterable {
    case common     // 白
    case uncommon   // 绿
    case rare       // 蓝
    case epic       // 紫
    case legendary  // 橙

    var color: Color {
        switch self {
        case .common: return .gray
        case .uncommon: return .green
        case .rare: return .blue
        case .epic: return .purple
        case .legendary: return .orange
        }
    }

    var displayName: String {
        switch self {
        case .common: return "普通"
        case .uncommon: return "优秀"
        case .rare: return "稀有"
        case .epic: return "史诗"
        case .legendary: return "传说"
        }
    }
}

enum ItemType: String, Codable {
    case weapon
    case helmet
    case armor
    case legs
    case boots
    case gloves
    case necklace
    case ring
    case consumable
    case material
    case quest

    var equipSlot: EquipSlot? {
        switch self {
        case .weapon: return .weapon
        case .helmet: return .helmet
        case .armor: return .armor
        case .legs: return .legs
        case .boots: return .boots
        case .gloves: return .gloves
        case .necklace: return .necklace
        case .ring: return .ring1
        default: return nil
        }
    }

    var inventoryCategory: InventoryCategory {
        switch self {
        case .weapon, .helmet, .armor, .legs, .boots, .gloves, .necklace, .ring:
            return .equipment
        case .consumable, .material:
            return .item
        case .quest:
            return .quest
        }
    }

    var displayName: String {
        switch self {
        case .weapon: return "武器"
        case .helmet: return "头盔"
        case .armor: return "护甲"
        case .legs: return "护腿"
        case .boots: return "靴子"
        case .gloves: return "护手"
        case .necklace: return "项链"
        case .ring: return "戒指"
        case .consumable: return "消耗品"
        case .material: return "材料"
        case .quest: return "任务"
        }
    }

    var wearPositionName: String {
        switch self {
        case .weapon: return "武器"
        case .helmet: return "头盔"
        case .armor: return "护甲"
        case .legs: return "护腿"
        case .boots: return "靴子"
        case .gloves: return "护手"
        case .necklace: return "项链"
        case .ring: return "戒指（双槽）"
        case .consumable, .material, .quest: return "不可穿戴"
        }
    }

}

enum ItemMarketOrigin: String, Codable {
    case loot
    case robotMarket
}

enum InventoryCategory: String, CaseIterable {
    case equipment
    case item
    case quest

    var displayName: String {
        switch self {
        case .equipment: return "装备"
        case .item: return "道具"
        case .quest: return "任务"
        }
    }
}

private enum ItemNameFormatter {
    static func compactName(_ name: String, type: ItemType) -> String {
        var core = name
        let prefixes = ["旧制", "精制", "秘纹", "史诗", "传说", "普通", "优秀", "稀有"]

        for prefix in prefixes where core.hasPrefix(prefix) {
            core.removeFirst(prefix.count)
            break
        }

        let trimmed = core.trimmingCharacters(in: .whitespacesAndNewlines)
        let characters = Array(trimmed.isEmpty ? name : trimmed)
        if characters.isEmpty {
            return type.displayName
        }

        return String(characters.prefix(6))
    }
}

struct Item: Identifiable, Codable {
    let id: UUID
    let templateId: String
    let name: String
    let type: ItemType
    let quality: ItemQuality
    let requiredLevel: Int
    let attackBonus: Int
    let defenseBonus: Int
    let hpBonus: Int
    let mpBonus: Int
    let critBonus: Double
    let description: String
    let sellPrice: Int
    var marketOrigin: ItemMarketOrigin
    var marketLockUntil: Date?
    var enhancementLevel: Int
    var enhancementLuck: Int

    init(template: ItemTemplate) {
        self.id = UUID()
        self.templateId = template.id
        self.name = template.name
        self.type = template.type
        self.quality = template.quality
        self.requiredLevel = template.requiredLevel
        self.attackBonus = template.attackBonus + Int.random(in: 0...template.randomRange)
        self.defenseBonus = template.defenseBonus + Int.random(in: 0...template.randomRange)
        self.hpBonus = template.hpBonus
        self.mpBonus = template.mpBonus
        self.critBonus = template.critBonus
        self.description = template.description
        self.sellPrice = template.sellPrice
        self.marketOrigin = .loot
        self.marketLockUntil = nil
        self.enhancementLevel = 0
        self.enhancementLuck = 0
    }

    enum CodingKeys: String, CodingKey {
        case id
        case templateId
        case name
        case type
        case quality
        case requiredLevel
        case attackBonus
        case defenseBonus
        case hpBonus
        case mpBonus
        case critBonus
        case description
        case sellPrice
        case marketOrigin
        case marketLockUntil
        case enhancementLevel
        case enhancementLuck
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        templateId = try container.decode(String.self, forKey: .templateId)
        name = try container.decode(String.self, forKey: .name)
        type = try container.decode(ItemType.self, forKey: .type)
        quality = try container.decode(ItemQuality.self, forKey: .quality)
        requiredLevel = try container.decode(Int.self, forKey: .requiredLevel)
        attackBonus = try container.decode(Int.self, forKey: .attackBonus)
        defenseBonus = try container.decode(Int.self, forKey: .defenseBonus)
        hpBonus = try container.decode(Int.self, forKey: .hpBonus)
        mpBonus = try container.decode(Int.self, forKey: .mpBonus)
        critBonus = try container.decode(Double.self, forKey: .critBonus)
        description = try container.decode(String.self, forKey: .description)
        sellPrice = try container.decode(Int.self, forKey: .sellPrice)
        marketOrigin = try container.decodeIfPresent(ItemMarketOrigin.self, forKey: .marketOrigin) ?? .loot
        marketLockUntil = try container.decodeIfPresent(Date.self, forKey: .marketLockUntil)
        enhancementLevel = try container.decodeIfPresent(Int.self, forKey: .enhancementLevel) ?? 0
        enhancementLuck = try container.decodeIfPresent(Int.self, forKey: .enhancementLuck) ?? 0
    }

    var displayName: String {
        enhancementLevel > 0 ? "\(name) +\(enhancementLevel)" : name
    }

    var compactDisplayName: String {
        ItemNameFormatter.compactName(name, type: type)
    }

    var enhancementMultiplier: Double {
        1 + Double(enhancementLevel) * 0.03
    }

    var enhancedAttackBonus: Int {
        enhancedValue(attackBonus)
    }

    var enhancedDefenseBonus: Int {
        enhancedValue(defenseBonus)
    }

    var enhancedHPBonus: Int {
        enhancedValue(hpBonus)
    }

    var enhancedMPBonus: Int {
        enhancedValue(mpBonus)
    }

    var enhancedCritBonus: Double {
        critBonus * enhancementMultiplier + milestoneCritBonus
    }

    var nextEnhancementCost: Int {
        guard enhancementLevel < 15 else { return 0 }
        let targetLevel = enhancementLevel + 1
        let itemLevel = max(1, requiredLevel)
        return itemLevel * itemLevel * targetLevel * 10
    }

    var nextEnhancementSuccessRate: Double {
        EnhancementRule.successRate(forTargetLevel: enhancementLevel + 1, luck: enhancementLuck)
    }

    private var milestoneCritBonus: Double {
        var bonus = 0.0
        if enhancementLevel >= 10 { bonus += 0.01 }
        if enhancementLevel >= 15 { bonus += 0.02 }
        return bonus
    }

    private func enhancedValue(_ value: Int) -> Int {
        guard value > 0 else { return 0 }
        var result = Int((Double(value) * enhancementMultiplier).rounded())
        if enhancementLevel >= 5 { result += max(1, value / 10) }
        if enhancementLevel >= 10 { result += max(1, value / 8) }
        if enhancementLevel >= 15 { result += max(1, value / 5) }
        return result
    }

    var powerScore: Int {
        enhancedAttackBonus * 2 + enhancedDefenseBonus * 2 + enhancedHPBonus / 5 + enhancedMPBonus / 5 + Int(enhancedCritBonus * 100)
    }

    func isMarketLocked(at date: Date = Date()) -> Bool {
        guard let marketLockUntil else { return false }
        return marketLockUntil > date
    }
}

enum EnhancementRule {
    static let maxLevel = 15

    static func baseSuccessRate(forTargetLevel targetLevel: Int) -> Double {
        switch targetLevel {
        case 1...3: return 1.0
        case 4...6: return 0.8
        case 7...9: return 0.6
        case 10...12: return 0.4
        case 13...15: return 0.2
        default: return 0
        }
    }

    static func successRate(forTargetLevel targetLevel: Int, luck: Int) -> Double {
        min(1.0, baseSuccessRate(forTargetLevel: targetLevel) + Double(luck) * 0.05)
    }

    static func downgradeAmount(forTargetLevel targetLevel: Int) -> Int {
        switch targetLevel {
        case 7...12: return 1
        case 13...15: return 2
        default: return 0
        }
    }
}

struct ItemTemplate: Codable, Identifiable {
    let id: String
    let name: String
    let type: ItemType
    let quality: ItemQuality
    let requiredLevel: Int
    let attackBonus: Int
    let defenseBonus: Int
    let hpBonus: Int
    let mpBonus: Int
    let critBonus: Double
    let randomRange: Int
    let description: String
    let sellPrice: Int

    var compactDisplayName: String {
        ItemNameFormatter.compactName(name, type: type)
    }
}
