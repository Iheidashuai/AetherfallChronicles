import Foundation

class LootSystem {
    static func generateLoot(from table: [LootEntry]) -> [Item] {
        var items: [Item] = []
        let templates = ConfigLoader.shared.itemTemplates

        for entry in table {
            let roll = Double.random(in: 0...1)
            if roll <= entry.dropRate {
                if let template = templates.first(where: { $0.id == entry.itemId }) {
                    items.append(Item(template: template))
                }
            }
        }

        return items
    }
}
