CREATE TABLE account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    token VARCHAR(96) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_token (token),
    KEY idx_session_account (account_id),
    CONSTRAINT fk_session_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE player (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    name VARCHAR(32) NOT NULL,
    profession VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    experience INT NOT NULL DEFAULT 0,
    gold INT NOT NULL DEFAULT 100,
    strength INT NOT NULL,
    agility INT NOT NULL,
    constitution INT NOT NULL,
    intelligence INT NOT NULL,
    spirit INT NOT NULL,
    free_points INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_player_account (account_id),
    UNIQUE KEY uk_player_name (name),
    CONSTRAINT fk_player_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE item_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    quality VARCHAR(32) NOT NULL,
    required_level INT NOT NULL,
    attack_bonus INT NOT NULL,
    defense_bonus INT NOT NULL,
    hp_bonus INT NOT NULL,
    mp_bonus INT NOT NULL,
    crit_bonus DECIMAL(10, 6) NOT NULL,
    sell_price INT NOT NULL,
    enhancement_level INT NOT NULL DEFAULT 0,
    enhancement_luck INT NOT NULL DEFAULT 0,
    market_lock_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_item_player (player_id),
    KEY idx_item_template (template_id),
    CONSTRAINT fk_item_player FOREIGN KEY (player_id) REFERENCES player (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_slot (
    player_id BIGINT NOT NULL,
    slot_index INT NOT NULL,
    item_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, slot_index),
    UNIQUE KEY uk_inventory_item (item_id),
    CONSTRAINT fk_inventory_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT fk_inventory_item FOREIGN KEY (item_id) REFERENCES item_instance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equipment_slot (
    player_id BIGINT NOT NULL,
    slot_name VARCHAR(32) NOT NULL,
    item_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, slot_name),
    UNIQUE KEY uk_equipment_item (item_id),
    CONSTRAINT fk_equipment_player FOREIGN KEY (player_id) REFERENCES player (id),
    CONSTRAINT fk_equipment_item FOREIGN KEY (item_id) REFERENCES item_instance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dungeon_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    dungeon_id VARCHAR(64) NOT NULL,
    config_version VARCHAR(64) NOT NULL,
    request_id VARCHAR(96) NULL,
    success BOOLEAN NOT NULL,
    rating VARCHAR(8) NOT NULL,
    monsters_killed INT NOT NULL,
    exp_gained INT NOT NULL,
    gold_gained INT NOT NULL,
    loot_json JSON NOT NULL,
    result_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dungeon_run_request (player_id, request_id),
    KEY idx_dungeon_run_player (player_id),
    CONSTRAINT fk_dungeon_run_player FOREIGN KEY (player_id) REFERENCES player (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE economy_audit_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(96) NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_economy_player (player_id),
    CONSTRAINT fk_economy_player FOREIGN KEY (player_id) REFERENCES player (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE config_bundle (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(64) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
