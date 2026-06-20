package com.mythicrealm.api.gameplay.endgame;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EndgameRiftSchemaTest {

    @Test
    void weeklyRewardChestCanBeOpenedAndConsumed() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("CONSTRAINT fk_rift_weekly_chest FOREIGN KEY (chest_item_id) REFERENCES item_instance (id) ON DELETE SET NULL"));
    }

    @Test
    void gemProgressionExtendsToNineRanks() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('gem_ruby_8', 'ruby', 8, 'attack', 620.0000, 'any', 'gem_ruby_9'"));
        assertTrue(schema.contains("('gem_ruby_9', 'ruby', 9, 'attack', 820.0000, 'any', NULL"));
        assertTrue(schema.contains("('gem_topaz_9', 'topaz', 9, 'crit', 0.1400, 'any', NULL"));
    }

    @Test
    void shopSellsRiftMaterialsAndStarterGems() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('processing_abyss_essence_pack', '深渊精华匣'"));
        assertTrue(schema.contains("('processing_tempering_shard_pack', '淬炼碎片匣'"));
        assertTrue(schema.contains("('processing_guard_pack', '护阶符'"));
        assertTrue(schema.contains("('gem_ruby_starter', '裂纹红宝石'"));
        assertTrue(schema.contains("('gem_topaz_starter', '裂纹黄玉'"));
    }

    @Test
    void shopSellsOneTimeLevelSixtyBoostPotion() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('potion_level_60_boost', '一键60级药水', 'levelBoost'"));
        assertTrue(schema.contains("\"targetLevel\":60"));
        assertTrue(schema.contains("\"shopPurchaseLimit\":1"));
        assertTrue(schema.contains("('growth_level_60_boost', '一键60级药水'"));
        assertTrue(schema.contains("'growth', 1, 'potion_level_60_boost', 1"));
        assertTrue(schema.contains("WHERE id IN ('chest_growth_cache', 'chest_forge_cache', 'potion_level_60_boost')"));
    }

    @Test
    void levelSixtyEpicBaseGearExistsForAllNineSlots() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('eq_t48_weapon_04'"));
        assertTrue(schema.contains("('eq_t48_helmet_04'"));
        assertTrue(schema.contains("('eq_t48_armor_04'"));
        assertTrue(schema.contains("('eq_t48_legs_04'"));
        assertTrue(schema.contains("('eq_t48_boots_04'"));
        assertTrue(schema.contains("('eq_t48_gloves_04'"));
        assertTrue(schema.contains("('eq_t48_necklace_04'"));
        assertTrue(schema.contains("('eq_t48_ring_04'"));
    }
}
