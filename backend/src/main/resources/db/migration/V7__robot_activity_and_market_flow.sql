ALTER TABLE robot_profile
    ADD COLUMN gold INT NOT NULL DEFAULT 50000,
    ADD COLUMN current_activity_kind VARCHAR(32) NOT NULL DEFAULT 'rest',
    ADD COLUMN current_activity_text VARCHAR(240) NOT NULL DEFAULT '正在公会大厅整理装备。',
    ADD COLUMN current_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE market_listing
    ADD COLUMN seller_robot_id BIGINT NULL,
    ADD COLUMN buyer_robot_id BIGINT NULL,
    ADD KEY idx_market_seller_robot (seller_robot_id),
    ADD KEY idx_market_buyer_robot (buyer_robot_id),
    ADD CONSTRAINT fk_market_seller_robot FOREIGN KEY (seller_robot_id) REFERENCES robot_profile (id),
    ADD CONSTRAINT fk_market_buyer_robot FOREIGN KEY (buyer_robot_id) REFERENCES robot_profile (id);

CREATE TABLE robot_activity_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    robot_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    actor_title VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    text VARCHAR(240) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_robot_activity_created (created_at),
    KEY idx_robot_activity_robot (robot_id, created_at),
    KEY idx_robot_activity_kind (kind, created_at),
    CONSTRAINT fk_robot_activity_robot FOREIGN KEY (robot_id) REFERENCES robot_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELETE FROM global_announcement;

INSERT INTO global_announcement (kind, actor_name, text, priority)
VALUES ('system', '银冠公会', '全服通告已降低传说掉落频率，商会与机器人动态已接入实时记录。', 1);
