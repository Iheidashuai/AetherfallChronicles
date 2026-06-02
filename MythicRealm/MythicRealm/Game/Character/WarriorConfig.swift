import Foundation

struct Skill: Codable {
    let id: String
    let name: String
    let description: String
    let damageMultiplier: Double
    let range: Double
    let radius: Double
    let cooldown: TimeInterval
    let castTime: TimeInterval
    let mpCost: Int
    var lastUsedTime: TimeInterval = 0

    enum CodingKeys: String, CodingKey {
        case id, name, description, damageMultiplier, range, radius, cooldown, castTime, mpCost
    }
}

struct WarriorConfig {
    static let skills: [Skill] = [
        Skill(
            id: "whirlwind",
            name: "旋风斩",
            description: "旋转挥击周围敌人，造成150%攻击力伤害",
            damageMultiplier: 1.5,
            range: 10,
            radius: 70,
            cooldown: 4.0,
            castTime: 0.5,
            mpCost: 20
        ),
        Skill(
            id: "smash",
            name: "猛击",
            description: "向前方重击，造成200%攻击力伤害并击退敌人",
            damageMultiplier: 2.0,
            range: 60,
            radius: 40,
            cooldown: 6.0,
            castTime: 0.6,
            mpCost: 30
        )
    ]
}
