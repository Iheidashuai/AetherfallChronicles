package com.mythicrealm.api.gameplay.endgame;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EndgameRiftSchemaTest {

    @Test
    void newPlayersStartWithOneMillionBalance() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));
        String playerService = Files.readString(Path.of("src/main/java/com/mythicrealm/api/gameplay/player/PlayerService.java"));

        assertTrue(schema.contains("real_money BIGINT NOT NULL DEFAULT 1000000"));
        assertTrue(playerService.contains("VALUES (?, ?, ?, 1000000, ?, ?, ?, ?, ?, ?, ?)"));
    }

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
        assertTrue(schema.contains("('gem_ruby_starter', '裂纹红宝石 I'"));
        assertTrue(schema.contains("('gem_topaz_starter', '裂纹黄玉 I'"));
        assertTrue(schema.contains("('gem_ruby_rank_5', '星辉红宝石 V'"));
        assertTrue(schema.contains("('gem_topaz_rank_5', '星辉黄玉 V'"));
    }

    @Test
    void shopSellsOneTimeLevelSixtyBoostPotion() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('potion_level_60_boost', '一键60级药水', 'levelBoost'"));
        assertTrue(schema.contains("\"targetLevel\":60"));
        assertTrue(schema.contains("'configured', '{\"version\":1"));
        assertTrue(schema.contains("\"shop\":{\"purchaseLimit\":1}"));
        assertTrue(schema.contains("('growth_level_60_boost', '一键60级药水'"));
        assertTrue(schema.contains("'growth', 1, 'potion_level_60_boost', 1"));
        assertTrue(schema.contains("'potion_level_60_boost'"));
    }

    @Test
    void shopSellsOneTimeCatchUpItems() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('potion_level_70_boost', '一键70级药水', 'levelBoost'"));
        assertTrue(schema.contains("('potion_level_80_boost', '一键80级药水', 'levelBoost'"));
        assertTrue(schema.contains("('potion_level_90_boost', '一键90级药水', 'levelBoost'"));
        assertTrue(schema.contains("\"equipmentTier\":\"eq_t58\""));
        assertTrue(schema.contains("\"equipmentTier\":\"eq_t68\""));
        assertTrue(schema.contains("\"equipmentTier\":\"eq_t78\""));
        assertTrue(schema.contains("('chest_legendary_set_70', '70级传说九件套宝箱', 'chest'"));
        assertTrue(schema.contains("('chest_legendary_set_80', '80级传说九件套宝箱', 'chest'"));
        assertTrue(schema.contains("('chest_legendary_set_90', '90级传说九件套宝箱', 'chest'"));
        assertTrue(schema.contains("\"equipmentTemplatePrefix\":\"eq_bloodmoon_l70\""));
        assertTrue(schema.contains("\"equipmentTemplatePrefix\":\"eq_bloodmoon_l80\""));
        assertTrue(schema.contains("\"equipmentTemplatePrefix\":\"eq_bloodmoon_l90\""));
        assertTrue(schema.contains("('scroll_enhancement_max_all', '全装强化满级券', 'progressBooster'"));
        assertTrue(schema.contains("('scroll_ascension_max_all', '全装升阶满级券', 'progressBooster'"));
        assertTrue(schema.contains("\"progression\":\"enhancement\""));
        assertTrue(schema.contains("\"progression\":\"ascension\""));
        assertTrue(schema.contains("\"shop\":{\"purchaseLimit\":10}"));
        assertTrue(schema.contains("('growth_level_90_boost', '一键90级药水'"));
        assertTrue(schema.contains("('chest_legendary_set_90_pack', '90级传说九件套宝箱'"));
    }

    @Test
    void catchUpEquipmentTemplatesExistForAllSlots() throws IOException {
        String schema = Files.readString(Path.of("../mythic-realm-starter/src/main/resources/db/latest_schema.sql"));

        assertTrue(schema.contains("('eq_t48_weapon_04'"));
        assertTrue(schema.contains("('eq_t48_helmet_04'"));
        assertTrue(schema.contains("('eq_t48_armor_04'"));
        assertTrue(schema.contains("('eq_t48_legs_04'"));
        assertTrue(schema.contains("('eq_t48_boots_04'"));
        assertTrue(schema.contains("('eq_t48_gloves_04'"));
        assertTrue(schema.contains("('eq_t48_necklace_04'"));
        assertTrue(schema.contains("('eq_t48_ring_04'"));
        assertTrue(schema.contains("('eq_t58_weapon_04'"));
        assertTrue(schema.contains("('eq_t68_weapon_04'"));
        assertTrue(schema.contains("('eq_t78_weapon_04'"));
        assertTrue(schema.contains("('eq_bloodmoon_l70_weapon_legendary'"));
        assertTrue(schema.contains("('eq_bloodmoon_l80_weapon_legendary'"));
        assertTrue(schema.contains("('eq_bloodmoon_l90_weapon_legendary'"));
    }
}
