CREATE TABLE global_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kind VARCHAR(32) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    text VARCHAR(240) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_announcement_created (created_at),
    KEY idx_announcement_priority (priority, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE robot_profile
    ADD COLUMN dungeon_clears INT NOT NULL DEFAULT 0,
    ADD COLUMN peak_enhancement INT NOT NULL DEFAULT 0,
    ADD COLUMN legendary_loot_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

INSERT INTO global_announcement (kind, actor_name, text, priority)
SELECT 'system', '银冠公会', '世界频道已接入实时远征通告。', 1
WHERE NOT EXISTS (SELECT 1 FROM global_announcement);
