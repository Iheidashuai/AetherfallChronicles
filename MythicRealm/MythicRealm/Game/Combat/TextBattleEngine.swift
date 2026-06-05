import Foundation
import SwiftUI

struct BattleLog: Identifiable {
    let id = UUID()
    let text: String
    let type: LogType
    let timestamp: Date = Date()

    enum LogType {
        case system      // 系统提示（进入房间、怪物出现）
        case playerAttack // 玩家攻击
        case playerSkill  // 玩家使用技能
        case monsterAttack // 怪物攻击
        case damage       // 伤害信息
        case loot         // 掉落
        case heal         // 回复
        case critical     // 暴击
        case dodge        // 闪避
        case death        // 死亡
        case boss         // Boss 相关
        case levelUp      // 升级
    }

    var color: Color {
        switch type {
        case .system: return .gray
        case .playerAttack: return .white
        case .playerSkill: return .cyan
        case .monsterAttack: return .orange
        case .damage: return .red
        case .loot: return .yellow
        case .heal: return .green
        case .critical: return .yellow
        case .dodge: return .blue
        case .death: return .red
        case .boss: return .purple
        case .levelUp: return .yellow
        }
    }
}

class TextBattleEngine: ObservableObject {
    @Published var logs: [BattleLog] = []
    @Published var isRunning: Bool = false
    @Published var isComplete: Bool = false
    @Published var currentRoomIndex: Int = 0
    @Published var playerHP: CGFloat = 0
    @Published var playerMaxHP: CGFloat = 0
    @Published var playerMP: CGFloat = 0
    @Published var playerMaxMP: CGFloat = 0
    @Published var monstersAlive: Int = 0
    @Published var result: DungeonResult?
    @Published var speedMultiplier: Int = 1

    private var dungeonConfig: DungeonConfig
    private var playerData: PlayerData
    private var equipped: [EquipSlot: Item]
    private var timer: Timer?
    private let baseTickInterval: TimeInterval = 1.2
    private var currentMonsters: [BattleMonster] = []
    private var totalMonstersKilled: Int = 0
    private var totalDeaths: Int = 0
    private var collectedLoot: [Item] = []
    private var totalExp: Int = 0
    private var totalGold: Int = 0
    private var startTime: Date = Date()
    private var skillCooldowns: [String: Int] = [:]
    private var tickCount: Int = 0

    struct BattleMonster {
        let config: MonsterConfig
        var currentHP: CGFloat
        var maxHP: CGFloat
        var bossPhase: Int = 1

        var isDead: Bool { currentHP <= 0 }
        var hpPercent: CGFloat { currentHP / maxHP }
    }

    init(dungeonConfig: DungeonConfig, playerData: PlayerData, equipped: [EquipSlot: Item]) {
        self.dungeonConfig = dungeonConfig
        self.playerData = playerData
        self.equipped = equipped
        self.playerMaxHP = playerData.maxHP + CGFloat(equipped.values.reduce(0) { $0 + $1.enhancedHPBonus })
        self.playerHP = playerMaxHP
        self.playerMaxMP = playerData.maxMP + CGFloat(equipped.values.reduce(0) { $0 + $1.enhancedMPBonus })
        self.playerMP = playerMaxMP
    }

    var totalAttack: CGFloat {
        playerData.attack + CGFloat(equipped.values.reduce(0) { $0 + $1.enhancedAttackBonus })
    }

    var totalDefense: CGFloat {
        playerData.defense + CGFloat(equipped.values.reduce(0) { $0 + $1.enhancedDefenseBonus })
    }

    var critRate: Double {
        min(0.45, Double(playerData.agility) * 0.001 + equipped.values.reduce(0.0) { $0 + $1.enhancedCritBonus })
    }

    var totalRooms: Int {
        dungeonConfig.rooms.count
    }

    private var currentTickInterval: TimeInterval {
        baseTickInterval / Double(speedMultiplier)
    }

    func start() {
        isRunning = true
        isComplete = false
        startTime = Date()
        logs.removeAll()

        addLog("⚔️ 进入副本【\(dungeonConfig.name)】", type: .system)
        addLog("推荐等级：\(dungeonConfig.recommendedLevel) | 你的等级：\(playerData.level)", type: .system)
        addLog("", type: .system)

        DispatchQueue.main.asyncAfter(deadline: .now() + scaledDelay(0.8)) {
            self.enterRoom(index: 0)
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        isRunning = false
    }

    func cycleSpeed() {
        speedMultiplier = speedMultiplier >= 5 ? 1 : speedMultiplier + 1
        addLog("⏩ 战斗速度：\(speedMultiplier)x", type: .system)

        if isRunning && !isComplete && timer != nil {
            scheduleBattleTimer()
        }
    }

    private func scheduleBattleTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: currentTickInterval, repeats: true) { [weak self] _ in
            self?.battleTick()
        }
    }

    private func scaledDelay(_ delay: TimeInterval) -> TimeInterval {
        delay / Double(speedMultiplier)
    }

    private func enterRoom(index: Int) {
        guard isRunning else { return }
        guard index < dungeonConfig.rooms.count else {
            completeDungeon()
            return
        }

        currentRoomIndex = index
        let room = dungeonConfig.rooms[index]

        addLog("━━━━━━━━━━━━━━━━━━━━", type: .system)
        if room.isBossRoom {
            addLog("💀 【BOSS房间】", type: .boss)
        } else {
            addLog("🚪 进入房间 \(index + 1)/\(dungeonConfig.rooms.count)", type: .system)
        }

        // Spawn monsters
        currentMonsters.removeAll()
        let monsterConfigs = ConfigLoader.shared.monsterConfigs
        for roomMonster in room.monsters {
            guard let config = monsterConfigs.first(where: { $0.id == roomMonster.monsterId }) else { continue }
            for _ in 0..<roomMonster.count {
                currentMonsters.append(BattleMonster(config: config, currentHP: config.maxHP, maxHP: config.maxHP))
            }
        }

        monstersAlive = currentMonsters.count
        let monsterNames = Dictionary(grouping: currentMonsters, by: { $0.config.name })
            .map { "\($0.value.count)只\($0.key)" }
            .joined(separator: "、")
        addLog("出现了 \(monsterNames)！", type: .system)
        addLog("", type: .system)

        // Start combat loop
        tickCount = 0
        scheduleBattleTimer()
    }

    private func battleTick() {
        guard isRunning else { return }
        tickCount += 1

        // Reduce skill cooldowns
        for key in skillCooldowns.keys {
            skillCooldowns[key] = max(0, (skillCooldowns[key] ?? 0) - 1)
        }

        // Player attacks
        playerTurn()

        // Check if all monsters dead
        if currentMonsters.allSatisfy({ $0.isDead }) {
            timer?.invalidate()
            timer = nil
            monstersAlive = 0

            addLog("", type: .system)
            addLog("✅ 房间清除！", type: .system)

            let nextRoom = currentRoomIndex + 1
            if nextRoom < dungeonConfig.rooms.count {
                // Small HP/MP regen between rooms
                let hpRegen = playerMaxHP * 0.1
                let mpRegen = playerMaxMP * 0.2
                playerHP = min(playerMaxHP, playerHP + hpRegen)
                playerMP = min(playerMaxMP, playerMP + mpRegen)
                addLog("💚 休息片刻...恢复了 \(Int(hpRegen)) HP, \(Int(mpRegen)) MP", type: .heal)

                DispatchQueue.main.asyncAfter(deadline: .now() + self.scaledDelay(1.5)) {
                    self.enterRoom(index: nextRoom)
                }
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + self.scaledDelay(1.0)) {
                    self.completeDungeon()
                }
            }
            return
        }

        // Monster attacks
        monsterTurn()

        // Check player death
        if playerHP <= 0 {
            handlePlayerDeath()
        }

        monstersAlive = currentMonsters.filter { !$0.isDead }.count
    }

    private func playerTurn() {
        // Try to use skill first
        if let skillResult = tryUseSkill() {
            addLog(skillResult.logText, type: skillResult.isCrit ? .critical : .playerSkill)
            return
        }

        // Normal attack on first alive monster
        guard let targetIndex = currentMonsters.firstIndex(where: { !$0.isDead }) else { return }
        let target = currentMonsters[targetIndex]

        let baseDamage = totalAttack
        let monsterDef = CGFloat(target.config.strength) * 0.5
        let rawDamage = max(1, baseDamage - monsterDef)

        let isCrit = Double.random(in: 0...1) < critRate
        let finalDamage = isCrit ? rawDamage * 1.5 : rawDamage
        let actualDamage = CGFloat(Int(finalDamage))

        currentMonsters[targetIndex].currentHP -= actualDamage

        if isCrit {
            addLog("⚡ 暴击！普通攻击 → \(target.config.name) 造成 \(Int(actualDamage)) 伤害", type: .critical)
        } else {
            addLog("🗡️ 普通攻击 → \(target.config.name) 造成 \(Int(actualDamage)) 伤害", type: .playerAttack)
        }

        if currentMonsters[targetIndex].isDead {
            handleMonsterDeath(index: targetIndex)
        } else {
            let hpInfo = "\(target.config.name) HP: \(Int(currentMonsters[targetIndex].currentHP))/\(Int(target.maxHP))"
            addLog("   └ \(hpInfo)", type: .damage)
        }

        // Boss phase check
        checkBossPhase(targetIndex: targetIndex)
    }

    private func tryUseSkill() -> (logText: String, isCrit: Bool)? {
        let skills = WarriorConfig.skills
        for skill in skills {
            let cd = skillCooldowns[skill.id] ?? 0
            if cd <= 0 && playerMP >= CGFloat(skill.mpCost) {
                playerMP -= CGFloat(skill.mpCost)
                skillCooldowns[skill.id] = Int(skill.cooldown / baseTickInterval)

                let baseDamage = totalAttack * CGFloat(skill.damageMultiplier)
                let isCrit = Double.random(in: 0...1) < critRate
                let finalDamage = isCrit ? baseDamage * 1.5 : baseDamage

                // AoE: hit all alive monsters for whirlwind, or first for smash
                let targets: [Int]
                if skill.id == "whirlwind" {
                    targets = currentMonsters.indices.filter { !currentMonsters[$0].isDead }
                } else {
                    if let first = currentMonsters.firstIndex(where: { !$0.isDead }) {
                        targets = [first]
                    } else {
                        targets = []
                    }
                }

                var logText = ""
                if isCrit {
                    logText = "⚡💥 暴击！释放【\(skill.name)】"
                } else {
                    logText = "✨ 释放【\(skill.name)】消耗 \(skill.mpCost) MP"
                }

                for i in targets {
                    let monsterDef = CGFloat(currentMonsters[i].config.strength) * 0.3
                    let actualDamage = CGFloat(Int(max(1, finalDamage - monsterDef)))
                    currentMonsters[i].currentHP -= actualDamage
                    logText += "\n   → \(currentMonsters[i].config.name) 受到 \(Int(actualDamage)) 伤害"

                    if currentMonsters[i].isDead {
                        handleMonsterDeath(index: i)
                    }
                }

                return (logText, isCrit)
            }
        }
        return nil
    }

    private func monsterTurn() {
        let aliveMonsters = currentMonsters.enumerated().filter { !$0.element.isDead }
        // Only 1-2 monsters attack per tick
        let attackers = aliveMonsters.prefix(min(2, aliveMonsters.count))

        for (_, monster) in attackers {
            guard playerHP > 0 else { break }

            // Player dodge chance
            let dodgeChance = min(0.35, Double(playerData.agility) * 0.005)
            if Double.random(in: 0...1) < dodgeChance {
                addLog("💨 闪避了 \(monster.config.name) 的攻击！", type: .dodge)
                continue
            }

            let monsterAtk = CGFloat(monster.config.strength) * 2.0
            let defense = totalDefense
            let rawDamage = max(1, monsterAtk - defense * 0.4)
            let damage = CGFloat(Int(rawDamage))

            playerHP -= damage
            addLog("🔴 \(monster.config.name) 攻击你，造成 \(Int(damage)) 伤害", type: .monsterAttack)
        }

        if playerHP > 0 && playerHP < playerMaxHP * 0.3 {
            addLog("   ⚠️ 生命值危险！HP: \(Int(playerHP))/\(Int(playerMaxHP))", type: .damage)
        }
    }

    private func handleMonsterDeath(index: Int) {
        let monster = currentMonsters[index]
        totalMonstersKilled += 1
        totalExp += monster.config.expReward
        totalGold += monster.config.goldReward

        addLog("💀 \(monster.config.name) 被击杀！+\(monster.config.expReward) EXP +\(monster.config.goldReward) 金币", type: .death)

        // Loot
        let items = LootSystem.generateLoot(from: monster.config.lootTable)
        for item in items {
            collectedLoot.append(item)
            addLog("🎁 掉落：[\(item.quality.displayName)] \(item.name)", type: .loot)
        }

        let levelUps = playerData.addExperience(monster.config.expReward)
        for newLevel in levelUps {
            playerMaxHP = playerData.maxHP + CGFloat(equipped.values.reduce(0) { $0 + $1.enhancedHPBonus })
            playerHP = playerMaxHP
            playerMaxMP = playerData.maxMP + CGFloat(equipped.values.reduce(0) { $0 + $1.enhancedMPBonus })
            playerMP = playerMaxMP
            addLog("🎉 升级！达到 Lv.\(newLevel)！获得 3 点自由属性，HP/MP 恢复满！", type: .levelUp)
        }
    }

    private func checkBossPhase(targetIndex: Int) {
        guard targetIndex < currentMonsters.count else { return }
        let monster = currentMonsters[targetIndex]
        guard monster.config.isBoss, !monster.isDead, monster.hpPercent <= 0.5, monster.bossPhase == 1 else { return }

        currentMonsters[targetIndex].bossPhase = 2
        addLog("", type: .boss)
        addLog("💀⚡ \(monster.config.name) 进入狂暴阶段！攻击力增加！", type: .boss)
        addLog("", type: .boss)
    }

    private func handlePlayerDeath() {
        totalDeaths += 1
        timer?.invalidate()
        timer = nil

        addLog("", type: .death)
        addLog("☠️ 你倒下了...", type: .death)
        addLog("挑战失败，已退出副本。", type: .death)
        playerHP = 0

        failDungeon()
    }

    private func completeDungeon() {
        guard isRunning else { return }
        isRunning = false
        isComplete = true
        let elapsed = Date().timeIntervalSince(startTime)

        addLog("", type: .system)
        addLog("━━━━━━━━━━━━━━━━━━━━", type: .system)
        addLog("🏆 副本通关！", type: .system)
        addLog("用时：\(formatTime(elapsed))", type: .system)
        addLog("击杀：\(totalMonstersKilled) 只怪物", type: .system)
        addLog("获得：\(totalExp) 经验 | \(totalGold) 金币", type: .system)
        addLog("掉落：\(collectedLoot.count) 件物品", type: .loot)

        let rating = calculateRating(time: elapsed, deaths: totalDeaths)
        addLog("评价：\(rating.rawValue)", type: .system)

        // Apply rewards to game state
        playerData.gold += totalGold
        GameState.shared.player = playerData
        GameState.shared.completedDungeonIds.insert(dungeonConfig.id)
        for item in collectedLoot {
            _ = InventorySystem.addItem(item, to: GameState.shared)
        }
        QuestSystem.record(
            .dungeonCompleted(
                dungeonId: dungeonConfig.id,
                monstersKilled: totalMonstersKilled,
                lootQualities: collectedLoot.map(\.quality)
            ),
            gameState: GameState.shared
        )
        GameState.shared.saveProgress()

        let completedResult = DungeonResult(
            dungeonName: dungeonConfig.name,
            rating: rating,
            isSuccess: true,
            timeTaken: elapsed,
            monstersKilled: totalMonstersKilled,
            deaths: totalDeaths,
            loot: collectedLoot,
            expGained: totalExp,
            goldGained: totalGold
        )
        result = completedResult
        WorldChatSystem.recordDungeonResult(completedResult, gameState: GameState.shared)
    }

    private func failDungeon() {
        isRunning = false
        isComplete = true
        let elapsed = Date().timeIntervalSince(startTime)

        let failedResult = DungeonResult(
            dungeonName: dungeonConfig.name,
            rating: .failed,
            isSuccess: false,
            timeTaken: elapsed,
            monstersKilled: totalMonstersKilled,
            deaths: totalDeaths,
            loot: [],
            expGained: 0,
            goldGained: 0
        )
        result = failedResult
        WorldChatSystem.recordDungeonResult(failedResult, gameState: GameState.shared)

        DispatchQueue.main.asyncAfter(deadline: .now() + self.scaledDelay(0.8)) {
            GameState.shared.dungeonResult = failedResult
            GameState.shared.currentScreen = .dungeonResult
        }
    }

    private func calculateRating(time: TimeInterval, deaths: Int) -> DungeonResult.Rating {
        if deaths == 0 && time < 60 { return .S }
        else if deaths == 0 || time < 90 { return .A }
        else if deaths <= 1 { return .B }
        else { return .C }
    }

    private func addLog(_ text: String, type: BattleLog.LogType) {
        DispatchQueue.main.async {
            self.logs.append(BattleLog(text: text, type: type))
        }
    }

    private func formatTime(_ time: TimeInterval) -> String {
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}
