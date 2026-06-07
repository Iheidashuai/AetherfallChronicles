CREATE TABLE item_template (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    quality VARCHAR(32) NOT NULL,
    required_level INT NOT NULL,
    attack_bonus INT NOT NULL,
    defense_bonus INT NOT NULL,
    hp_bonus INT NOT NULL,
    mp_bonus INT NOT NULL,
    crit_bonus DECIMAL(10, 6) NOT NULL,
    random_range INT NOT NULL,
    description VARCHAR(1000) NOT NULL,
    sell_price INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE monster_config (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    level INT NOT NULL,
    max_hp INT NOT NULL,
    strength INT NOT NULL,
    is_boss BOOLEAN NOT NULL,
    exp_reward INT NOT NULL,
    gold_reward INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE monster_loot (
    monster_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    drop_rate DECIMAL(10, 6) NOT NULL,
    PRIMARY KEY (monster_id, item_id),
    CONSTRAINT fk_monster_loot_monster FOREIGN KEY (monster_id) REFERENCES monster_config (id),
    CONSTRAINT fk_monster_loot_item FOREIGN KEY (item_id) REFERENCES item_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dungeon_config (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    recommended_level INT NOT NULL,
    recommended_power INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dungeon_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dungeon_id VARCHAR(64) NOT NULL,
    room_config_id VARCHAR(64) NOT NULL,
    room_order INT NOT NULL,
    is_boss_room BOOLEAN NOT NULL,
    UNIQUE KEY uk_dungeon_room_config (dungeon_id, room_config_id),
    KEY idx_dungeon_room_order (dungeon_id, room_order),
    CONSTRAINT fk_dungeon_room_dungeon FOREIGN KEY (dungeon_id) REFERENCES dungeon_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dungeon_room_monster (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    monster_id VARCHAR(64) NOT NULL,
    monster_count INT NOT NULL,
    KEY idx_room_monster_room (room_id),
    CONSTRAINT fk_room_monster_room FOREIGN KEY (room_id) REFERENCES dungeon_room (id),
    CONSTRAINT fk_room_monster_monster FOREIGN KEY (monster_id) REFERENCES monster_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_config (
    id VARCHAR(96) PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    description VARCHAR(500) NOT NULL,
    lore VARCHAR(1000) NULL,
    priority INT NOT NULL,
    navigation_target VARCHAR(64) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_prerequisite (
    quest_id VARCHAR(96) NOT NULL,
    prerequisite_id VARCHAR(96) NOT NULL,
    PRIMARY KEY (quest_id, prerequisite_id),
    CONSTRAINT fk_quest_prereq_quest FOREIGN KEY (quest_id) REFERENCES quest_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_condition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quest_id VARCHAR(96) NOT NULL,
    condition_id VARCHAR(96) NOT NULL,
    condition_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(96) NULL,
    target_value INT NOT NULL,
    condition_order INT NOT NULL,
    KEY idx_quest_condition_quest (quest_id),
    CONSTRAINT fk_quest_condition_quest FOREIGN KEY (quest_id) REFERENCES quest_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_reward (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quest_id VARCHAR(96) NOT NULL,
    reward_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(96) NULL,
    amount INT NOT NULL,
    reward_order INT NOT NULL,
    KEY idx_quest_reward_quest (quest_id),
    CONSTRAINT fk_quest_reward_quest FOREIGN KEY (quest_id) REFERENCES quest_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quest_progress (
    player_id BIGINT NOT NULL,
    quest_id VARCHAR(96) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_value INT NOT NULL DEFAULT 0,
    cycle_key VARCHAR(16) NULL,
    claimed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, quest_id),
    CONSTRAINT fk_quest_progress_player FOREIGN KEY (player_id) REFERENCES player (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE market_listing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    seller_player_id BIGINT NULL,
    seller_name VARCHAR(64) NOT NULL,
    item_id BIGINT NULL,
    item_snapshot_json JSON NOT NULL,
    price INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    buyer_player_id BIGINT NULL,
    sold_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_market_status (status, created_at),
    KEY idx_market_seller (seller_player_id),
    CONSTRAINT fk_market_seller FOREIGN KEY (seller_player_id) REFERENCES player (id),
    CONSTRAINT fk_market_buyer FOREIGN KEY (buyer_player_id) REFERENCES player (id),
    CONSTRAINT fk_market_item FOREIGN KEY (item_id) REFERENCES item_instance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id BIGINT NULL,
    sender_name VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    text VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chat_created (created_at),
    CONSTRAINT fk_chat_player FOREIGN KEY (player_id) REFERENCES player (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE robot_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    title VARCHAR(64) NOT NULL,
    profession VARCHAR(32) NOT NULL,
    level INT NOT NULL,
    power INT NOT NULL,
    personality VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_robot_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO robot_profile (name, title, profession, level, power, personality) VALUES
('铁靴雷恩', '热血战士', 'warrior', 18, 6200, '喜欢挑战和晒战绩'),
('旅法师米娅', '毒舌法师', 'mage', 21, 7100, '关注法杖、暴击和市场价格'),
('寒霜骑士雷欧', '沉稳骑士', 'warrior', 30, 11800, '稳定守榜的公会前辈'),
('银月游侠艾琳', '爱炫耀的游侠', 'ranger', 24, 8800, '喜欢晒掉落和低价饰品'),
('海盐商人托比', '捡漏商人', 'ranger', 16, 4100, '总在观察商会价格'),
('赤铜铁匠博林', '强化上头的铁匠', 'warrior', 19, 5600, '相信装备要见过火星'),
('星砂旅人薇拉', '幸运旅人', 'mage', 14, 3900, '总能捡到意外掉落'),
('灰烬伯爵卡修', '高冷剑士', 'warrior', 34, 14200, '榜单前列，只谈结果');
