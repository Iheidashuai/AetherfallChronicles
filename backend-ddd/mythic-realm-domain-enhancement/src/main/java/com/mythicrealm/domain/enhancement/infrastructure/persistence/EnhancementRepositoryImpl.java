package com.mythicrealm.domain.enhancement.infrastructure.persistence;

import com.mythicrealm.domain.enhancement.model.Enhancement;
import com.mythicrealm.domain.enhancement.repository.EnhancementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 强化仓储实现
 *
 * 使用 item_instance 表的 enhancement_level 和 enhancement_luck 字段
 */
@Repository
public class EnhancementRepositoryImpl implements EnhancementRepository {

    private final JdbcTemplate jdbcTemplate;

    public EnhancementRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Enhancement> findByItemId(long itemId) {
        return jdbcTemplate.query(
            """
            SELECT id, player_id, enhancement_level, enhancement_luck
            FROM item_instance
            WHERE id = ?
            """,
            (rs, rowNum) -> Enhancement.restore(
                rs.getLong("id"),
                rs.getLong("player_id"),
                rs.getInt("enhancement_level"),
                rs.getInt("enhancement_luck")
            ),
            itemId
        ).stream().findFirst();
    }

    @Override
    public void save(Enhancement enhancement) {
        jdbcTemplate.update(
            """
            UPDATE item_instance
            SET enhancement_level = ?, enhancement_luck = ?
            WHERE id = ? AND player_id = ?
            """,
            enhancement.getLevelValue(),
            enhancement.getLuckValue(),
            enhancement.getItemId(),
            enhancement.getPlayerId()
        );
    }

    @Override
    public void delete(long itemId) {
        jdbcTemplate.update(
            """
            UPDATE item_instance
            SET enhancement_level = 0, enhancement_luck = 0
            WHERE id = ?
            """,
            itemId
        );
    }
}
