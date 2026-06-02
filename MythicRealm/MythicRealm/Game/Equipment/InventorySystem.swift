import Foundation

struct EnhancementResult {
    let success: Bool
    let message: String
}

struct InventorySaleResult {
    let count: Int
    let gold: Int
}

class InventorySystem {
    static let maxSlots = 160

    static func addItem(_ item: Item, to gameState: GameState) -> Bool {
        guard gameState.inventory.count < maxSlots else { return false }
        gameState.inventory.append(item)
        return true
    }

    static func removeItem(_ item: Item, from gameState: GameState) {
        gameState.inventory.removeAll { $0.id == item.id }
    }

    static func equipItem(_ item: Item, gameState: GameState) {
        guard let slot = item.type.equipSlot else { return }
        guard let player = gameState.player,
              item.requiredLevel <= player.level else { return }

        // Handle ring slots
        let actualSlot: EquipSlot
        if item.type == .ring {
            if gameState.equippedItems[.ring1] == nil {
                actualSlot = .ring1
            } else if gameState.equippedItems[.ring2] == nil {
                actualSlot = .ring2
            } else {
                actualSlot = .ring1
            }
        } else {
            actualSlot = slot
        }

        if let existing = gameState.equippedItems[actualSlot] {
            gameState.inventory.append(existing)
        }

        gameState.equippedItems[actualSlot] = item
        removeItem(item, from: gameState)
        gameState.saveProgress()
    }

    static func unequipItem(slot: EquipSlot, gameState: GameState) {
        guard let item = gameState.equippedItems[slot] else { return }
        guard gameState.inventory.count < maxSlots else { return }
        gameState.inventory.append(item)
        gameState.equippedItems.removeValue(forKey: slot)
        gameState.saveProgress()
    }

    static func sellItem(_ item: Item, gameState: GameState) {
        removeItem(item, from: gameState)
        gameState.player?.gold += vendorSellPrice(for: item)
        gameState.saveProgress()
    }

    static func vendorSellPrice(for item: Item) -> Int {
        item.marketOrigin == .robotMarket
            ? max(1, Int(Double(item.sellPrice) * 0.6))
            : item.sellPrice
    }

    static func sortInventory(gameState: GameState) {
        gameState.inventory.sort { lhs, rhs in
            if lhs.type.inventoryCategory != rhs.type.inventoryCategory {
                return categoryRank(lhs.type.inventoryCategory) < categoryRank(rhs.type.inventoryCategory)
            }
            if lhs.requiredLevel != rhs.requiredLevel {
                return lhs.requiredLevel > rhs.requiredLevel
            }
            if qualityRank(lhs.quality) != qualityRank(rhs.quality) {
                return qualityRank(lhs.quality) > qualityRank(rhs.quality)
            }
            if lhs.enhancementLevel != rhs.enhancementLevel {
                return lhs.enhancementLevel > rhs.enhancementLevel
            }
            return lhs.powerScore > rhs.powerScore
        }
        gameState.saveProgress()
    }

    @discardableResult
    static func sellItems(
        quality: ItemQuality,
        category: InventoryCategory,
        gameState: GameState
    ) -> InventorySaleResult {
        let soldItems = gameState.inventory.filter { item in
            item.quality == quality && item.type.inventoryCategory == category
        }
        let income = soldItems.reduce(0) { total, item in
            total + vendorSellPrice(for: item)
        }

        let before = gameState.inventory.count
        gameState.inventory.removeAll { item in
            item.quality == quality && item.type.inventoryCategory == category
        }
        let removed = before - gameState.inventory.count
        if removed > 0 {
            gameState.player?.gold += income
            gameState.saveProgress()
        }
        return InventorySaleResult(count: removed, gold: income)
    }

    static func enhanceItem(_ item: Item, gameState: GameState) -> EnhancementResult {
        guard var current = findItem(withId: item.id, gameState: gameState) else {
            return EnhancementResult(success: false, message: "装备不存在")
        }
        guard current.type.equipSlot != nil else {
            return EnhancementResult(success: false, message: "只有装备可以强化")
        }
        guard current.enhancementLevel < EnhancementRule.maxLevel else {
            return EnhancementResult(success: false, message: "已经强化到上限")
        }

        let targetLevel = current.enhancementLevel + 1
        let cost = current.nextEnhancementCost
        guard (gameState.player?.gold ?? 0) >= cost else {
            return EnhancementResult(success: false, message: "金币不足，需要 \(cost) 金")
        }

        gameState.player?.gold -= cost
        let successRate = current.nextEnhancementSuccessRate
        let success = Double.random(in: 0...1) <= successRate

        if success {
            current.enhancementLevel = targetLevel
            current.enhancementLuck = 0
            replaceItem(current, gameState: gameState)
            WorldChatSystem.recordEnhancement(item: current, success: true, targetLevel: targetLevel, gameState: gameState)
            gameState.saveProgress()
            return EnhancementResult(success: true, message: "强化成功！\(current.displayName)")
        }

        current.enhancementLuck += 1
        let downgrade = EnhancementRule.downgradeAmount(forTargetLevel: targetLevel)
        if downgrade > 0 {
            current.enhancementLevel = max(3, current.enhancementLevel - downgrade)
        }
        replaceItem(current, gameState: gameState)
        WorldChatSystem.recordEnhancement(item: current, success: false, targetLevel: targetLevel, gameState: gameState)
        gameState.saveProgress()

        if downgrade > 0 {
            return EnhancementResult(success: false, message: "强化失败，等级降至 +\(current.enhancementLevel)，幸运 +5%")
        }
        return EnhancementResult(success: false, message: "强化失败，幸运 +5%")
    }

    static func totalEquipmentAttack(gameState: GameState) -> Int {
        gameState.equippedItems.values.reduce(0) { $0 + $1.enhancedAttackBonus }
    }

    static func totalEquipmentDefense(gameState: GameState) -> Int {
        gameState.equippedItems.values.reduce(0) { $0 + $1.enhancedDefenseBonus }
    }

    static func totalEquipmentHP(gameState: GameState) -> Int {
        gameState.equippedItems.values.reduce(0) { $0 + $1.enhancedHPBonus }
    }

    static func totalEquipmentMP(gameState: GameState) -> Int {
        gameState.equippedItems.values.reduce(0) { $0 + $1.enhancedMPBonus }
    }

    static func totalEquipmentCrit(gameState: GameState) -> Double {
        gameState.equippedItems.values.reduce(0) { $0 + $1.enhancedCritBonus }
    }

    static func combatPower(gameState: GameState) -> Int {
        guard let player = gameState.player else { return 0 }
        let attack = Double(Int(player.attack) + totalEquipmentAttack(gameState: gameState))
        let defense = Double(Int(player.defense) + totalEquipmentDefense(gameState: gameState))
        let hp = Double(Int(player.maxHP) + totalEquipmentHP(gameState: gameState))
        let mp = Double(Int(player.maxMP) + totalEquipmentMP(gameState: gameState))
        let critRate = min(0.45, Double(player.agility) * 0.001 + totalEquipmentCrit(gameState: gameState))
        let equippedSlots = Double(gameState.equippedItems.count)

        let offenseScore = attack * 12
        let defenseScore = defense * 8
        let healthScore = sqrt(max(1, hp)) * 26
        let manaScore = sqrt(max(1, mp)) * 12
        let critScore = offenseScore * critRate * 0.8
        let levelScore = Double(player.level) * 45
        let slotSetBonus = 1 + min(0.10, equippedSlots * 0.008)

        return Int((offenseScore + defenseScore + healthScore + manaScore + critScore + levelScore) * slotSetBonus)
    }

    private static func findItem(withId id: UUID, gameState: GameState) -> Item? {
        if let item = gameState.inventory.first(where: { $0.id == id }) {
            return item
        }
        return gameState.equippedItems.values.first(where: { $0.id == id })
    }

    private static func replaceItem(_ item: Item, gameState: GameState) {
        if let index = gameState.inventory.firstIndex(where: { $0.id == item.id }) {
            gameState.inventory[index] = item
            return
        }

        if let slot = gameState.equippedItems.first(where: { $0.value.id == item.id })?.key {
            gameState.equippedItems[slot] = item
        }
    }

    private static func categoryRank(_ category: InventoryCategory) -> Int {
        switch category {
        case .equipment: return 0
        case .item: return 1
        case .quest: return 2
        }
    }

    private static func qualityRank(_ quality: ItemQuality) -> Int {
        switch quality {
        case .common: return 0
        case .uncommon: return 1
        case .rare: return 2
        case .epic: return 3
        case .legendary: return 4
        }
    }
}
