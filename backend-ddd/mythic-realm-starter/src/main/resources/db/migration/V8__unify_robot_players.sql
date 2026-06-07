ALTER TABLE player DROP FOREIGN KEY fk_player_account;

ALTER TABLE player
    MODIFY account_id BIGINT NULL,
    ADD COLUMN controller_type VARCHAR(16) NOT NULL DEFAULT 'player',
    ADD COLUMN title VARCHAR(64) NOT NULL DEFAULT '玩家',
    ADD COLUMN personality VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN dungeon_clears INT NOT NULL DEFAULT 0,
    ADD COLUMN peak_enhancement INT NOT NULL DEFAULT 0,
    ADD COLUMN legendary_loot_count INT NOT NULL DEFAULT 0,
    ADD COLUMN current_activity_kind VARCHAR(32) NOT NULL DEFAULT 'rest',
    ADD COLUMN current_activity_text VARCHAR(240) NOT NULL DEFAULT '正在公会大厅整理装备。',
    ADD COLUMN current_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE player
    ADD KEY idx_player_controller (controller_type, level, gold),
    ADD CONSTRAINT fk_player_account FOREIGN KEY (account_id) REFERENCES account (id);

ALTER TABLE robot_profile
    ADD COLUMN player_id BIGINT NULL,
    ADD KEY idx_robot_profile_player (player_id),
    ADD CONSTRAINT fk_robot_profile_player FOREIGN KEY (player_id) REFERENCES player (id);

INSERT IGNORE INTO player
    (account_id, name, profession, level, experience, gold, strength, agility, constitution,
     intelligence, spirit, free_points, controller_type, title, personality, dungeon_clears,
     peak_enhancement, legendary_loot_count, current_activity_kind, current_activity_text,
     current_activity_at, last_activity_at)
SELECT
    NULL,
    rp.name,
    rp.profession,
    rp.level,
    0,
    rp.gold,
    CASE rp.profession
        WHEN 'mage' THEN 6 + FLOOR(rp.level / 2)
        WHEN 'ranger' THEN 12 + FLOOR(rp.level / 2)
        ELSE 16 + FLOOR(rp.level / 2)
    END,
    CASE rp.profession
        WHEN 'ranger' THEN 17 + FLOOR(rp.level / 2)
        ELSE 10 + FLOOR(rp.level / 2)
    END,
    CASE rp.profession
        WHEN 'warrior' THEN 15 + FLOOR(rp.level / 2)
        ELSE 10 + FLOOR(rp.level / 2)
    END,
    CASE rp.profession
        WHEN 'mage' THEN 18 + FLOOR(rp.level / 2)
        ELSE 8 + FLOOR(rp.level / 2)
    END,
    CASE rp.profession
        WHEN 'mage' THEN 16 + FLOOR(rp.level / 2)
        ELSE 9 + FLOOR(rp.level / 2)
    END,
    0,
    'robot',
    rp.title,
    rp.personality,
    rp.dungeon_clears,
    rp.peak_enhancement,
    rp.legendary_loot_count,
    rp.current_activity_kind,
    rp.current_activity_text,
    rp.current_activity_at,
    rp.last_activity_at
FROM robot_profile rp;

UPDATE robot_profile rp
JOIN player p ON p.name = rp.name AND p.controller_type = 'robot'
SET rp.player_id = p.id
WHERE rp.player_id IS NULL;

ALTER TABLE item_instance
    ADD COLUMN legacy_robot_equipment_id BIGINT NULL;

INSERT INTO item_instance
    (player_id, template_id, name, item_type, quality, required_level, attack_bonus,
     defense_bonus, hp_bonus, mp_bonus, crit_bonus, sell_price, enhancement_level,
     enhancement_luck, legacy_robot_equipment_id)
SELECT
    rp.player_id,
    re.template_id,
    re.name,
    re.item_type,
    re.quality,
    re.required_level,
    re.attack_bonus,
    re.defense_bonus,
    re.hp_bonus,
    re.mp_bonus,
    re.crit_bonus,
    re.sell_price,
    re.enhancement_level,
    re.enhancement_luck,
    re.id
FROM robot_equipment re
JOIN robot_profile rp ON rp.id = re.robot_id
LEFT JOIN equipment_slot es ON es.player_id = rp.player_id AND es.slot_name = re.slot_name
WHERE rp.player_id IS NOT NULL
  AND es.item_id IS NULL;

INSERT INTO equipment_slot (player_id, slot_name, item_id)
SELECT
    rp.player_id,
    re.slot_name,
    ii.id
FROM robot_equipment re
JOIN robot_profile rp ON rp.id = re.robot_id
JOIN item_instance ii ON ii.legacy_robot_equipment_id = re.id
LEFT JOIN equipment_slot es ON es.player_id = rp.player_id AND es.slot_name = re.slot_name
WHERE rp.player_id IS NOT NULL
  AND es.item_id IS NULL;

ALTER TABLE item_instance
    DROP COLUMN legacy_robot_equipment_id;

UPDATE market_listing ml
JOIN robot_profile rp ON rp.id = ml.seller_robot_id
SET ml.seller_player_id = rp.player_id
WHERE ml.seller_player_id IS NULL
  AND rp.player_id IS NOT NULL;

UPDATE market_listing ml
JOIN robot_profile rp ON rp.id = ml.buyer_robot_id
SET ml.buyer_player_id = rp.player_id
WHERE ml.buyer_player_id IS NULL
  AND rp.player_id IS NOT NULL;

ALTER TABLE robot_activity_log DROP FOREIGN KEY fk_robot_activity_robot;

UPDATE robot_activity_log ral
JOIN robot_profile rp ON rp.id = ral.robot_id
SET ral.robot_id = rp.player_id
WHERE rp.player_id IS NOT NULL;

ALTER TABLE robot_activity_log
    ADD CONSTRAINT fk_robot_activity_player FOREIGN KEY (robot_id) REFERENCES player (id);
