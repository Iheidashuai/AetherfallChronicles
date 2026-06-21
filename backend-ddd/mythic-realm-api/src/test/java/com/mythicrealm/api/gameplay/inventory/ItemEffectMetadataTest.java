package com.mythicrealm.api.gameplay.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mythicrealm.api.gameplay.gameconfig.ConfigModels.ItemTemplate;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemEffectMetadataTest {

    @Test
    void configuredMetadataComesFromUnifiedEffectJson() {
        ItemTemplate item = template(
            "potion_test",
            "configured",
            """
            {"version":1,"tags":["leveling","shopOnly"],"ui":{"typeLabel":"成长药水","actionLabel":"使用","summary":"直升 Lv.90","usageHint":"立即生效"},"use":{"consumeSelf":1,"effects":[{"op":"setLevel","targetLevel":90}]},"market":{"tradeable":false},"shop":{"purchaseLimit":1},"robot":{"policy":"never"}}
            """
        );

        ItemEffectMetadata metadata = ItemEffectMetadata.fromTemplate(item);

        assertThat(metadata.usable()).isTrue();
        assertThat(metadata.typeLabel()).isEqualTo("成长药水");
        assertThat(metadata.effectSummary()).isEqualTo("直升 Lv.90");
        assertThat(metadata.shopPurchaseLimit()).isEqualTo(1);
        assertThat(metadata.marketable()).isFalse();
        assertThat(metadata.robotPolicy()).isEqualTo("never");
    }

    @Test
    void validationRejectsUnknownPrimitive() {
        ItemTemplate item = template(
            "bad_item",
            "configured",
            "{\"version\":1,\"use\":{\"effects\":[{\"op\":\"runSql\"}]}}"
        );

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> ItemEffectMetadata.validateTemplate(item, Set.of("bad_item"))
        );

        assertThat(error.getMessage()).contains("unknown effect op runSql");
    }

    @Test
    void validationRejectsMissingItemReferences() {
        ItemTemplate item = template(
            "box",
            "configured",
            "{\"version\":1,\"use\":{\"effects\":[{\"op\":\"grantItems\",\"items\":[{\"templateId\":\"missing\",\"quantity\":1}]}]}}"
        );

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> ItemEffectMetadata.validateTemplate(item, Set.of("box"))
        );

        assertThat(error.getMessage()).contains("references missing item missing");
    }

    @Test
    void validationAllowsMultiAttributePotions() {
        ItemTemplate item = template(
            "multi_attr",
            "configured",
            "{\"version\":1,\"use\":{\"effects\":[{\"op\":\"addAttributes\",\"attributes\":{\"strength\":10,\"constitution\":10}}]}}"
        );

        ItemEffectMetadata.validateTemplate(item, Set.of("multi_attr"));
    }

    private static ItemTemplate template(String id, String effectType, String effectValueJson) {
        return new ItemTemplate(
            id,
            id,
            "potion",
            "consumable",
            "rare",
            1,
            0,
            0,
            0,
            0,
            0,
            BigDecimal.ZERO,
            0,
            "",
            1,
            true,
            99,
            effectType,
            effectValueJson,
            false,
            "consumable",
            0,
            0,
            0,
            1,
            15
        );
    }
}
