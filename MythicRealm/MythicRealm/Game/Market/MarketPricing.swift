import Foundation

enum MarketPricing {
    static let baseTaxRate = 0.08

    static func priceWindow(for item: Item, state: MarketState) -> MarketPriceWindow {
        let recommended = estimateValue(for: item, state: state)
        let range = priceRangeMultiplier(for: item.quality)
        let minimum = max(1, Int(Double(recommended) * range.minimum))
        let maximum = max(minimum, Int(Double(recommended) * range.maximum))
        return MarketPriceWindow(
            recommended: recommended,
            minimum: minimum,
            maximum: maximum,
            quickSale: max(minimum, Int(Double(recommended) * 0.9)),
            highSale: min(maximum, Int(Double(recommended) * 1.2)),
            taxRate: baseTaxRate
        )
    }

    static func estimateValue(for item: Item, state: MarketState?) -> Int {
        let level = max(1, item.requiredLevel)
        let levelValue = 12 * pow(Double(level), 1.45)
        let vendorFloor = Double(item.sellPrice) * 2.1
        let baseValue = max(levelValue, vendorFloor)
        let value = baseValue
            * qualityMultiplier(for: item.quality)
            * typeMultiplier(for: item.type)
            * statMultiplier(for: item)
            * enhancementMultiplier(for: item.enhancementLevel)
            * demandMultiplier(for: item, state: state)
            * resaleMultiplier(for: item)

        return max(1, Int(value.rounded()))
    }

    static func listingFee(for item: Item, price: Int, recommended: Int) -> Int {
        let baseRate: Double
        switch item.quality {
        case .common, .uncommon:
            baseRate = 0.01
        case .rare:
            baseRate = 0.02
        case .epic, .legendary:
            baseRate = 0.03
        }

        let highPriceSurcharge = price > Int(Double(recommended) * 1.25) ? 0.02 : 0
        return max(1, Int(Double(price) * (baseRate + highPriceSurcharge)))
    }

    static func priceTag(price: Int, recommended: Int) -> MarketPriceTag {
        guard recommended > 0 else { return .fair }
        let ratio = Double(price) / Double(recommended)
        if ratio <= 0.85 { return .cheap }
        if ratio <= 1.1 { return .fair }
        if ratio <= 1.35 { return .pricey }
        return .luxury
    }

    static func purchaseAttractiveness(item: Item, robot: MarketRobotProfile, state: MarketState) -> Double {
        let quality = normalizedQualityScore(item.quality)
        let slot = robot.preferredTypes.contains(item.type) ? 1.0 : 0.35
        let stats = statMatchScore(item: item, robot: robot)
        let enhance = min(1.0, Double(item.enhancementLevel) / 8.0)
        let scarcity = demandMultiplier(for: item, state: state).clamped(to: 0.85...1.25) - 0.25

        return (quality * 0.30 + slot * 0.20 + stats * 0.25 + enhance * 0.15 + scarcity * 0.10)
            .clamped(to: 0.05...1.0)
    }

    static func isPriceAllowed(_ price: Int, for item: Item, state: MarketState) -> Bool {
        let window = priceWindow(for: item, state: state)
        return price >= window.minimum && price <= window.maximum
    }

    static func demandMultiplier(for item: Item, state: MarketState?) -> Double {
        guard let state else { return 1.0 }
        let demand = state.demandMap[item.type.rawValue] ?? 1.0
        return demand.clamped(to: 0.85...1.25)
    }

    private static func priceRangeMultiplier(for quality: ItemQuality) -> (minimum: Double, maximum: Double) {
        switch quality {
        case .common: return (0.8, 1.2)
        case .uncommon: return (0.75, 1.3)
        case .rare: return (0.75, 1.35)
        case .epic: return (0.7, 1.5)
        case .legendary: return (0.65, 1.8)
        }
    }

    private static func qualityMultiplier(for quality: ItemQuality) -> Double {
        switch quality {
        case .common: return 0.75
        case .uncommon: return 1.0
        case .rare: return 1.55
        case .epic: return 2.8
        case .legendary: return 5.2
        }
    }

    private static func normalizedQualityScore(_ quality: ItemQuality) -> Double {
        switch quality {
        case .common: return 0.25
        case .uncommon: return 0.45
        case .rare: return 0.68
        case .epic: return 0.86
        case .legendary: return 1.0
        }
    }

    private static func typeMultiplier(for type: ItemType) -> Double {
        switch type {
        case .weapon: return 1.25
        case .armor: return 1.1
        case .necklace, .ring: return 1.22
        case .helmet, .gloves: return 0.96
        case .legs: return 0.92
        case .boots: return 0.9
        case .consumable, .material, .quest: return 0.6
        }
    }

    private static func statMultiplier(for item: Item) -> Double {
        let level = max(1, item.requiredLevel)
        let expectedScore = Double(level * 115 + 160)
        let score = Double(max(0, item.powerScore))
        return (0.85 + min(0.5, score / expectedScore)).clamped(to: 0.85...1.35)
    }

    private static func enhancementMultiplier(for enhancementLevel: Int) -> Double {
        let level = Double(max(0, enhancementLevel))
        return 1 + level * 0.08 + level * level * 0.005
    }

    private static func resaleMultiplier(for item: Item) -> Double {
        item.marketOrigin == .robotMarket ? 0.75 : 1.0
    }

    private static func statMatchScore(item: Item, robot: MarketRobotProfile) -> Double {
        var total = 0.0
        var matched = 0.0

        func add(_ focus: MarketStatFocus, value: Double) {
            guard value > 0 else { return }
            total += 1
            if robot.preferredStats.contains(focus) {
                matched += 1
            }
        }

        add(.attack, value: Double(item.enhancedAttackBonus))
        add(.defense, value: Double(item.enhancedDefenseBonus))
        add(.hp, value: Double(item.enhancedHPBonus))
        add(.mp, value: Double(item.enhancedMPBonus))
        add(.crit, value: item.enhancedCritBonus)

        guard total > 0 else { return 0.35 }
        return (matched / total).clamped(to: 0.2...1.0)
    }
}

private extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
