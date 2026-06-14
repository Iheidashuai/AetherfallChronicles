package com.mythicrealm.api.gameplay.gameconfig;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final GameConfigService gameConfigService;

    public ConfigController(GameConfigService gameConfigService) {
        this.gameConfigService = gameConfigService;
    }

    @GetMapping("/bootstrap")
    GameConfigService.ConfigSummary bootstrap() {
        return gameConfigService.summary();
    }

    @GetMapping("/items")
    List<ItemCatalogItem> items() {
        return gameConfigService.itemTemplates().stream()
            .map(ItemCatalogItem::from)
            .toList();
    }

    public record ItemCatalogItem(
        String templateId,
        String name,
        String itemType,
        String itemCategory,
        String quality,
        int requiredLevel,
        int attackBonus,
        int defenseBonus,
        int resistanceBonus,
        int hpBonus,
        int mpBonus,
        BigDecimal critBonus,
        int randomRange,
        String description,
        int sellPrice,
        boolean stackable,
        int maxStack,
        String effectType,
        String effectValueJson,
        double enhanceBonusRate,
        int minEnhanceLevel,
        int maxEnhanceLevel
    ) {
        static ItemCatalogItem from(ItemTemplate item) {
            return new ItemCatalogItem(
                item.id(),
                item.name(),
                item.type(),
                item.category(),
                item.quality(),
                item.requiredLevel(),
                item.attackBonus(),
                item.defenseBonus(),
                item.resistanceBonus(),
                item.hpBonus(),
                item.mpBonus(),
                item.critBonus(),
                item.randomRange(),
                item.description(),
                item.sellPrice(),
                item.stackable(),
                item.maxStack(),
                item.effectType(),
                item.effectValueJson(),
                item.enhanceBonusRate(),
                item.minEnhanceLevel(),
                item.maxEnhanceLevel()
            );
        }
    }
}
