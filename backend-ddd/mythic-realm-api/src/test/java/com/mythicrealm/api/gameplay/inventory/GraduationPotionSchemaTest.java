package com.mythicrealm.api.gameplay.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraduationPotionSchemaTest {
    @Test
    void shopSellsRankSixThroughNineGems() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertThat(schema).contains("'gem_ruby_rank_6'");
        assertThat(schema).contains("'gem_sapphire_rank_7'");
        assertThat(schema).contains("'gem_emerald_rank_8'");
        assertThat(schema).contains("'gem_topaz_rank_9'");
        assertThat(schema).contains("'gem', 5000, 'gem_topaz_9', 1, 0, 1");
    }

    @Test
    void graduationPotionDefinesFullEndgamePackage() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));
        String inventoryService = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/inventory/InventoryService.java"));
        String itemEffectEngine = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/inventory/ItemEffectEngine.java"));

        assertThat(schema).contains("'potion_level_90_graduation', '一键90级毕业药水'");
        assertThat(schema).contains("'growth_level_90_graduation'");
        assertThat(schema).contains("\"equipmentTemplatePrefix\":\"eq_bloodmoon_l90\"");
        assertThat(schema).contains("\"equipmentQuality\":\"legendary\"");
        assertThat(schema).contains("\"enhancementLevel\":15");
        assertThat(schema).contains("\"ascensionLevel\":5");
        assertThat(schema).contains("\"refineLevel\":5");
        assertThat(schema).contains("\"socketGemRank\":9");
        assertThat(schema).contains("\"perfectAffixes\":true");
        assertThat(schema).contains("\"skipIfReached\":true");

        assertThat(inventoryService).contains("fillPackageSockets");
        assertThat(inventoryService).contains("writePackageAffixes");
        assertThat(itemEffectEngine).contains("skipIfReached");
        assertThat(itemEffectEngine).contains("\"enhancementLevel\"");
        assertThat(itemEffectEngine).contains("\"socketGemRank\"");
        assertThat(itemEffectEngine).contains("\"perfectAffixes\"");
    }

    @Test
    void shopSellsPotionThatMaxesAllEquipmentRefineLevels() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));
        String itemEffectEngine = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/inventory/ItemEffectEngine.java"));

        assertThat(schema).contains("'potion_refine_max_all', '全装淬炼满级药水'");
        assertThat(schema).contains("'growth_refine_max_all', '全装淬炼满级药水'");
        assertThat(schema).contains("\"typeLabel\":\"淬炼药水\"");
        assertThat(schema).contains("\"progression\":\"refine\"");
        assertThat(schema).contains("\"targetLevel\":5");
        assertThat(schema).contains("\"scope\":\"playableEquipment\"");
        assertThat(schema).contains("\"shop\":{\"purchaseLimit\":10}");
        assertThat(schema).contains("\"robot\":{\"policy\":\"never\"}");

        assertThat(itemEffectEngine).contains("progressBoostRefineFocus");
    }
}
