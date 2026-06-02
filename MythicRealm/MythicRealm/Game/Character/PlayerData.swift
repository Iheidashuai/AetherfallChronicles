import Foundation

enum Profession: String, Codable {
    case warrior
    case ranger
    case mage

    var displayName: String {
        switch self {
        case .warrior: return "战士"
        case .ranger: return "射手"
        case .mage: return "法师"
        }
    }
}

enum EquipSlot: String, Codable, CaseIterable {
    case weapon
    case helmet
    case armor
    case legs
    case boots
    case gloves
    case necklace
    case ring1
    case ring2

    var displayName: String {
        switch self {
        case .weapon: return "武器"
        case .helmet: return "头盔"
        case .armor: return "护甲"
        case .legs: return "护腿"
        case .boots: return "靴子"
        case .gloves: return "护手"
        case .necklace: return "项链"
        case .ring1: return "戒指 1"
        case .ring2: return "戒指 2"
        }
    }

    var sortOrder: Int {
        Self.allCases.firstIndex(of: self) ?? Int.max
    }
}

struct PlayerData: Codable {
    static let maxLevel = 60

    var name: String
    var profession: Profession
    var level: Int = 1
    var experience: Int = 0
    var gold: Int = 100

    var strength: Int = 10
    var agility: Int = 5
    var constitution: Int = 8
    var intelligence: Int = 3
    var spirit: Int = 4
    var freePoints: Int = 0

    var maxHP: CGFloat {
        100 + CGFloat(constitution) * 10 * (1 + CGFloat(level) * 0.1)
    }

    var maxMP: CGFloat {
        50 + CGFloat(intelligence) * 8 * (1 + CGFloat(level) * 0.08)
    }

    var attack: CGFloat {
        CGFloat(strength) * 2.0 + CGFloat(level) * 3.0
    }

    var defense: CGFloat {
        CGFloat(constitution) * 2.0 + CGFloat(level) * 1.5
    }

    var experienceToNextLevel: Int {
        Self.experienceRequired(for: level)
    }

    var experienceProgress: Double {
        guard experienceToNextLevel > 0 else { return 1 }
        return min(1, Double(experience) / Double(experienceToNextLevel))
    }

    static func experienceRequired(for level: Int) -> Int {
        guard level < maxLevel else { return 0 }
        return Int(100 * pow(Double(level), 1.8))
    }

    mutating func addExperience(_ amount: Int) -> [Int] {
        guard amount > 0, level < Self.maxLevel else { return [] }
        experience += amount

        var leveledTo: [Int] = []
        while level < Self.maxLevel {
            let needed = Self.experienceRequired(for: level)
            guard needed > 0, experience >= needed else { break }
            experience -= needed
            level += 1
            applyLevelUpGrowth()
            leveledTo.append(level)
        }

        if level >= Self.maxLevel {
            experience = 0
        }

        return leveledTo
    }

    private mutating func applyLevelUpGrowth() {
        strength += 1
        agility += 1
        constitution += 1
        intelligence += 1
        spirit += 1
        freePoints += 3

        switch profession {
        case .warrior:
            strength += 1
            constitution += 1
        case .ranger:
            agility += 1
            strength += 1
        case .mage:
            intelligence += 1
            spirit += 1
        }
    }

    init(name: String, profession: Profession) {
        self.name = name
        self.profession = profession
        switch profession {
        case .warrior:
            strength = 10; agility = 5; constitution = 8; intelligence = 3; spirit = 4
        case .ranger:
            strength = 6; agility = 10; constitution = 5; intelligence = 4; spirit = 5
        case .mage:
            strength = 3; agility = 4; constitution = 4; intelligence = 10; spirit = 9
        }
    }
}
