package com.mythicrealm.backend.gameconfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythicrealm.backend.gameconfig.ConfigModels.DungeonConfig;
import com.mythicrealm.backend.gameconfig.ConfigModels.ItemTemplate;
import com.mythicrealm.backend.gameconfig.ConfigModels.MonsterConfig;
import com.mythicrealm.backend.gameconfig.ConfigModels.QuestConfig;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseConfigSeeder {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String configVersion;

    public DatabaseConfigSeeder(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Value("${mythic.config.version}") String configVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.configVersion = configVersion;
    }

    @Transactional
    public void ensureSeeded() {
        try {
            byte[] itemsBytes = readBytes("config/items.json");
            byte[] monstersBytes = readBytes("config/monsters.json");
            byte[] dungeonsBytes = readBytes("config/dungeons.json");
            byte[] questsBytes = readBytes("config/quests.json");
            String checksum = checksum(itemsBytes, monstersBytes, dungeonsBytes, questsBytes);

            Integer matching = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM config_bundle WHERE version = ? AND checksum = ? AND active = TRUE",
                Integer.class,
                configVersion,
                checksum
            );
            Integer itemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item_template", Integer.class);
            if (matching != null && matching > 0 && itemCount != null && itemCount > 0) {
                return;
            }

            List<ItemTemplate> items = objectMapper.readValue(itemsBytes, new TypeReference<>() {
            });
            List<MonsterConfig> monsters = objectMapper.readValue(monstersBytes, new TypeReference<>() {
            });
            List<DungeonConfig> dungeons = objectMapper.readValue(dungeonsBytes, new TypeReference<>() {
            });
            List<QuestConfig> quests = objectMapper.readValue(questsBytes, new TypeReference<>() {
            });

            clearConfigTables();
            seedItems(items);
            seedMonsters(monsters);
            seedDungeons(dungeons);
            seedQuests(quests);
            jdbcTemplate.update("UPDATE config_bundle SET active = FALSE WHERE active = TRUE");
            jdbcTemplate.update(
                """
                INSERT INTO config_bundle (version, checksum, active)
                VALUES (?, ?, TRUE)
                ON DUPLICATE KEY UPDATE checksum = VALUES(checksum), active = TRUE
                """,
                configVersion,
                checksum
            );
        } catch (IOException error) {
            throw new IllegalStateException("Failed to seed game config into database", error);
        }
    }

    private void clearConfigTables() {
        jdbcTemplate.update("DELETE FROM quest_reward");
        jdbcTemplate.update("DELETE FROM quest_condition");
        jdbcTemplate.update("DELETE FROM quest_prerequisite");
        jdbcTemplate.update("DELETE FROM quest_config");
        jdbcTemplate.update("DELETE FROM dungeon_room_monster");
        jdbcTemplate.update("DELETE FROM dungeon_room");
        jdbcTemplate.update("DELETE FROM dungeon_config");
        jdbcTemplate.update("DELETE FROM monster_loot");
        jdbcTemplate.update("DELETE FROM monster_config");
        jdbcTemplate.update("DELETE FROM item_template");
    }

    private void seedItems(List<ItemTemplate> items) {
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO item_template
            (id, name, item_type, quality, required_level, attack_bonus, defense_bonus,
             hp_bonus, mp_bonus, crit_bonus, random_range, description, sell_price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            items,
            200,
            (ps, item) -> {
                ps.setString(1, item.id());
                ps.setString(2, item.name());
                ps.setString(3, item.type());
                ps.setString(4, item.quality());
                ps.setInt(5, item.requiredLevel());
                ps.setInt(6, item.attackBonus());
                ps.setInt(7, item.defenseBonus());
                ps.setInt(8, item.hpBonus());
                ps.setInt(9, item.mpBonus());
                ps.setBigDecimal(10, item.critBonus());
                ps.setInt(11, item.randomRange());
                ps.setString(12, item.description());
                ps.setInt(13, item.sellPrice());
            }
        );
    }

    private void seedMonsters(List<MonsterConfig> monsters) {
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO monster_config
            (id, name, level, max_hp, strength, is_boss, exp_reward, gold_reward)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            monsters,
            200,
            (ps, monster) -> {
                ps.setString(1, monster.id());
                ps.setString(2, monster.name());
                ps.setInt(3, monster.level());
                ps.setInt(4, monster.maxHP());
                ps.setInt(5, monster.strength());
                ps.setBoolean(6, monster.isBoss());
                ps.setInt(7, monster.expReward());
                ps.setInt(8, monster.goldReward());
            }
        );

        for (MonsterConfig monster : monsters) {
            if (monster.lootTable() == null) {
                continue;
            }
            jdbcTemplate.batchUpdate(
                "INSERT INTO monster_loot (monster_id, item_id, drop_rate) VALUES (?, ?, ?)",
                monster.lootTable(),
                100,
                (ps, loot) -> {
                    ps.setString(1, monster.id());
                    ps.setString(2, loot.itemId());
                    ps.setBigDecimal(3, BigDecimal.valueOf(loot.dropRate()));
                }
            );
        }
    }

    private void seedDungeons(List<DungeonConfig> dungeons) {
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO dungeon_config
            (id, name, description, difficulty, recommended_level, recommended_power)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            dungeons,
            100,
            (ps, dungeon) -> {
                ps.setString(1, dungeon.id());
                ps.setString(2, dungeon.name());
                ps.setString(3, dungeon.description());
                ps.setString(4, dungeon.difficulty());
                ps.setInt(5, dungeon.recommendedLevel());
                ps.setInt(6, dungeon.recommendedPower());
            }
        );

        for (DungeonConfig dungeon : dungeons) {
            for (int i = 0; i < dungeon.rooms().size(); i++) {
                var room = dungeon.rooms().get(i);
                var keyHolder = new GeneratedKeyHolder();
                int order = i;
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                        """
                        INSERT INTO dungeon_room (dungeon_id, room_config_id, room_order, is_boss_room)
                        VALUES (?, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                    );
                    ps.setString(1, dungeon.id());
                    ps.setString(2, room.id());
                    ps.setInt(3, order);
                    ps.setBoolean(4, room.isBossRoom());
                    return ps;
                }, keyHolder);
                long roomId = keyHolder.getKey().longValue();
                jdbcTemplate.batchUpdate(
                    "INSERT INTO dungeon_room_monster (room_id, monster_id, monster_count) VALUES (?, ?, ?)",
                    room.monsters(),
                    20,
                    (ps, roomMonster) -> {
                        ps.setLong(1, roomId);
                        ps.setString(2, roomMonster.monsterId());
                        ps.setInt(3, roomMonster.count());
                    }
                );
            }
        }
    }

    private void seedQuests(List<QuestConfig> quests) {
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO quest_config
            (id, title, category, description, lore, priority, navigation_target)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            quests,
            100,
            (ps, quest) -> {
                ps.setString(1, quest.id());
                ps.setString(2, quest.title());
                ps.setString(3, quest.category());
                ps.setString(4, quest.description());
                ps.setString(5, quest.lore());
                ps.setInt(6, quest.priority());
                ps.setString(7, quest.navigationTarget());
            }
        );

        for (QuestConfig quest : quests) {
            if (quest.prerequisiteIds() != null) {
                jdbcTemplate.batchUpdate(
                    "INSERT INTO quest_prerequisite (quest_id, prerequisite_id) VALUES (?, ?)",
                    quest.prerequisiteIds(),
                    20,
                    (ps, prerequisiteId) -> {
                        ps.setString(1, quest.id());
                        ps.setString(2, prerequisiteId);
                    }
                );
            }
            if (quest.conditions() != null) {
                for (int i = 0; i < quest.conditions().size(); i++) {
                    var condition = quest.conditions().get(i);
                    jdbcTemplate.update(
                        """
                        INSERT INTO quest_condition
                        (quest_id, condition_id, condition_type, target_id, target_value, condition_order)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        quest.id(),
                        condition.id(),
                        condition.type(),
                        condition.targetId(),
                        condition.targetValue(),
                        i
                    );
                }
            }
            if (quest.rewards() != null) {
                for (int i = 0; i < quest.rewards().size(); i++) {
                    var reward = quest.rewards().get(i);
                    jdbcTemplate.update(
                        """
                        INSERT INTO quest_reward
                        (quest_id, reward_type, target_id, amount, reward_order)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        quest.id(),
                        reward.type(),
                        reward.targetId(),
                        reward.amount(),
                        i
                    );
                }
            }
        }
    }

    private byte[] readBytes(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private String checksum(byte[]... chunks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] chunk : chunks) {
                digest.update(chunk);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
